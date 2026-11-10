package com.yanny.archflow.link;

import com.yanny.archflow.facts.FlowFact;
import com.yanny.archflow.facts.Import;
import com.yanny.archflow.facts.MethodFacts;
import com.yanny.archflow.facts.Param;
import com.yanny.archflow.facts.Receiver;
import com.yanny.archflow.facts.TypeRef;
import com.yanny.archflow.link.TypeIndex.FieldHit;
import com.yanny.archflow.link.TypeIndex.MethodHit;
import com.yanny.archflow.link.TypeIndex.TypeInfo;

import java.util.List;

final class CallResolver {
    record Target(String id, boolean external) {
        static final Target UNKNOWN = new Target(null, false);
        static final Target EXTERNAL = new Target(null, true);
    }

    private record Typed(TypeInfo info, TypeRef ref, TypeInfo scope) {
        static final Typed EXTERNAL = new Typed(null, null, null);
    }

    private record Found(MethodHit hit) {
        static final Found EXTERNAL = new Found(null);
    }

    private final TypeIndex index;

    CallResolver(TypeIndex index) {
        this.index = index;
    }

    Target target(FlowFact.Call call, TypeInfo current) {
        if (!call.name().equals(MethodFacts.CONSTRUCTOR)) {
            Found found = method(call.receiver(), call.name(), call.args(), current);
            if (found == null) {
                return Target.UNKNOWN;
            }
            return found.hit() == null ? Target.EXTERNAL : new Target(id(found.hit()), false);
        }
        TypeInfo owner = switch (call.receiver()) {
            case Receiver.This ignored -> current;
            case Receiver.Super ignored -> index.superclass(current);
            case Receiver.Typed typed -> index.resolve(typed.type(), current);
            default -> null;
        };
        if (owner == null) {
            return call.receiver() instanceof Receiver.Unknown ? Target.UNKNOWN : Target.EXTERNAL;
        }
        MethodHit constructor = choose(index.constructors(owner, call.args()), call.args(), current);
        return new Target(constructor == null ? owner.id() : id(constructor), false);
    }

    private Found method(Receiver receiver, String name, List<Receiver> args, TypeInfo current) {
        if (!(receiver instanceof Receiver.Implicit)) {
            Typed owner = type(receiver, current);
            if (owner == null) {
                return null;
            }
            MethodHit hit = owner.info() == null ? null : choose(index.methods(owner.info(), name, args), args, current);
            return hit == null ? Found.EXTERNAL : new Found(hit);
        }
        for (TypeInfo type = current; type != null; type = type.enclosing()) {
            MethodHit hit = choose(index.methods(type, name, args), args, current);
            if (hit != null) {
                return new Found(hit);
            }
        }
        for (Import imp : current.file().imports()) {
            if (!imp.isStatic()) {
                continue;
            }
            String typeName = imp.asterisk() ? imp.name() : null;
            if (!imp.asterisk() && TypeIndex.lastSegment(imp.name()).equals(name)) {
                typeName = imp.name().substring(0, imp.name().lastIndexOf('.'));
            }
            TypeInfo type = typeName == null ? null : index.get(typeName);
            MethodHit hit = type == null ? null : choose(index.methods(type, name, args), args, current);
            if (hit != null) {
                return new Found(hit);
            }
        }
        return Found.EXTERNAL;
    }

    private MethodHit choose(List<MethodHit> candidates, List<Receiver> args, TypeInfo current) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (args == null || candidates.size() == 1) {
            return candidates.getFirst();
        }
        List<Typed> argTypes = args.stream().map(arg -> type(arg, current)).toList();
        return candidates.stream()
                .filter(candidate -> compatible(candidate, argTypes))
                .findFirst()
                .orElse(candidates.getFirst());
    }

    private boolean compatible(MethodHit candidate, List<Typed> args) {
        List<Param> params = candidate.method().params();
        for (int i = 0; i < args.size(); i++) {
            Typed arg = args.get(i);
            Param param = params.get(Math.min(i, params.size() - 1));
            TypeRef paramRef = param.varargs() && i >= params.size() - 1 ? param.type().withDims(-1) : param.type();
            if (arg != null && !compatible(arg, paramRef, candidate.owner())) {
                return false;
            }
        }
        return true;
    }

    private boolean compatible(Typed arg, TypeRef param, TypeInfo scope) {
        TypeInfo paramType = param.dims() > 0 ? null : index.resolve(param, scope);
        if (arg.info() != null && paramType != null) {
            return index.isSubtype(arg.info(), paramType);
        }
        return arg.info() != null || paramType == null;
    }

    private Typed type(Receiver receiver, TypeInfo current) {
        return switch (receiver) {
            case Receiver.This ignored -> new Typed(current, null, null);
            case Receiver.Super ignored -> new Typed(index.superclass(current), null, null);
            case Receiver.Typed typed -> of(typed.type(), current);
            case Receiver.Name name -> name(name.name(), current);
            case Receiver.Field field -> field(field, current);
            case Receiver.Result result -> {
                Found found = method(result.receiver(), result.method(), result.args(), current);
                if (found == null || found.hit() == null) {
                    yield found == null ? null : Typed.EXTERNAL;
                }
                yield of(found.hit().method().returnType(), found.hit().owner());
            }
            case Receiver.Element element -> {
                Typed container = type(element.receiver(), current);
                if (container == null || container.ref() == null) {
                    yield container == null ? null : Typed.EXTERNAL;
                }
                TypeRef ref = container.ref();
                if (ref.dims() > 0) {
                    yield of(ref.withDims(-1), container.scope());
                }
                yield ref.args().size() == 1 ? of(ref.args().getFirst(), container.scope()) : Typed.EXTERNAL;
            }
            case Receiver.Implicit ignored -> null;
            case Receiver.Unknown ignored -> null;
        };
    }

    private Typed name(String name, TypeInfo current) {
        for (TypeInfo type = current; type != null; type = type.enclosing()) {
            FieldHit hit = index.field(type, name);
            if (hit != null) {
                return of(hit.field().type(), hit.owner());
            }
        }
        TypeInfo type = index.resolve(name, current);
        if (type != null) {
            return new Typed(type, null, null);
        }
        return isTypeName(name) ? Typed.EXTERNAL : null;
    }

    private static boolean isTypeName(String name) {
        return Character.isUpperCase(name.charAt(0));
    }

    private Typed field(Receiver.Field field, TypeInfo current) {
        Typed base = type(field.receiver(), current);
        if (base != null && base.info() != null) {
            FieldHit hit = index.field(base.info(), field.name());
            if (hit != null) {
                return of(hit.field().type(), hit.owner());
            }
            TypeInfo nested = index.get(base.info().id() + "." + field.name());
            return nested == null ? Typed.EXTERNAL : new Typed(nested, null, null);
        }
        if (base != null) {
            return Typed.EXTERNAL;
        }
        String qualified = qualifiedName(field);
        TypeInfo type = qualified == null ? null : index.get(qualified);
        if (type != null) {
            return new Typed(type, null, null);
        }
        return qualified != null && isTypeName(field.name()) ? Typed.EXTERNAL : null;
    }

    private static String qualifiedName(Receiver receiver) {
        return switch (receiver) {
            case Receiver.Name name -> name.name();
            case Receiver.Field field -> {
                String scope = qualifiedName(field.receiver());
                yield scope == null ? null : scope + "." + field.name();
            }
            default -> null;
        };
    }

    private Typed of(TypeRef ref, TypeInfo scope) {
        if (ref == null) {
            return null;
        }
        return new Typed(ref.dims() > 0 ? null : index.resolve(ref, scope), ref, scope);
    }

    private static String id(MethodHit hit) {
        return Linker.methodId(hit.owner().id(), hit.method());
    }
}
