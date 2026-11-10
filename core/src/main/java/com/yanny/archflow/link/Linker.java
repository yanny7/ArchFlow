package com.yanny.archflow.link;

import com.yanny.archflow.facts.FieldFacts;
import com.yanny.archflow.facts.FileFacts;
import com.yanny.archflow.facts.FlowFact;
import com.yanny.archflow.facts.Implementation;
import com.yanny.archflow.facts.MethodFacts;
import com.yanny.archflow.facts.Param;
import com.yanny.archflow.facts.TypeRef;
import com.yanny.archflow.link.TypeIndex.TypeInfo;
import com.yanny.archflow.model.Edge;
import com.yanny.archflow.model.Location;
import com.yanny.archflow.model.EdgeKind;
import com.yanny.archflow.model.Model;
import com.yanny.archflow.model.Node;
import com.yanny.archflow.model.NodeKind;
import com.yanny.archflow.model.Step;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class Linker {
    private final TypeIndex index;
    private final CallResolver resolver;
    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Map<String, List<Step>> flows = new LinkedHashMap<>();
    private final List<Edge> edges = new ArrayList<>();

    private Linker(TypeIndex index) {
        this.index = index;
        this.resolver = new CallResolver(index);
    }

    public static Model link(List<FileFacts> files) {
        Linker linker = new Linker(new TypeIndex(files));
        linker.packages(files);
        linker.index.types().forEach(linker::type);
        return new Model(List.copyOf(linker.nodes.values()), linker.edges, linker.flows);
    }

    private void packages(List<FileFacts> files) {
        Set<String> names = files.stream()
                .map(FileFacts::packageName)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));
        for (String name : names) {
            add(new Node(name, NodeKind.PACKAGE, name, enclosingPackage(name, names), null));
        }
    }

    private static String enclosingPackage(String name, Set<String> names) {
        for (int dot = name.lastIndexOf('.'); dot > 0; dot = name.lastIndexOf('.', dot - 1)) {
            String prefix = name.substring(0, dot);
            if (names.contains(prefix)) {
                return prefix;
            }
        }
        return null;
    }

    private void type(TypeInfo type) {
        String parent = type.enclosing() != null ? type.enclosing().id() : packageOf(type);
        add(new Node(type.id(), NodeKind.TYPE, type.facts().name(), parent, type.facts().location()));
        type.facts().supertypes().forEach(ref -> depend(type, ref));
        type.facts().fields().stream().map(FieldFacts::type).forEach(ref -> depend(type, ref));
        for (MethodFacts method : type.facts().methods()) {
            method.params().stream().map(Param::type).forEach(ref -> depend(type, ref));
            depend(type, method.returnType());
            String id = methodId(type.id(), method);
            String name = switch (method.name()) {
                case MethodFacts.CONSTRUCTOR -> type.facts().name();
                case MethodFacts.STATIC_INIT -> "static initializer";
                case MethodFacts.INSTANCE_INIT -> "initializer";
                default -> method.name();
            };
            if (!add(new Node(id, NodeKind.METHOD, name, type.id(), method.location()))) {
                continue;
            }
            if (!method.body().isEmpty()) {
                flows.put(id, steps(method.body(), type, id));
            }
            if (method.overridable()) {
                index.overridden(type, method)
                        .forEach(hit -> edges.add(new Edge(EdgeKind.OVERRIDE, id, methodId(hit.owner().id(), hit.method()))));
            }
            implementations(type, method, id);
        }
    }

    private void implementations(TypeInfo type, MethodFacts method, String id) {
        int lambdas = 0;
        for (Implementation implementation : method.implementations()) {
            String implemented = functionalMethod(implementation.types(), type);
            if (implemented == null) {
                continue;
            }
            if (!implementation.isLambda()) {
                String target = resolver.target(implementation.reference(), type).id();
                if (target != null && target.contains("#")) {
                    edges.add(new Edge(EdgeKind.OVERRIDE, target, implemented));
                }
                continue;
            }
            String lambda = id + "$lambda" + ++lambdas;
            String name = "lambda " + TypeIndex.lastSegment(implemented.substring(0, implemented.indexOf('#')));
            Location location = new Location(method.location().file(), implementation.line(), implementation.endLine());
            add(new Node(lambda, NodeKind.METHOD, name, id, location));
            if (!implementation.body().isEmpty()) {
                flows.put(lambda, steps(implementation.body(), type, lambda));
            }
            edges.add(new Edge(EdgeKind.OVERRIDE, lambda, implemented));
        }
    }

    private String functionalMethod(List<TypeRef> types, TypeInfo scope) {
        for (TypeRef candidate : types) {
            TypeInfo type = index.resolve(candidate, scope);
            List<MethodFacts> abstractMethods = type == null ? List.of()
                    : type.facts().methods().stream().filter(MethodFacts::abstractMethod).toList();
            if (abstractMethods.size() == 1) {
                return methodId(type.id(), abstractMethods.getFirst());
            }
        }
        return null;
    }

    private static String packageOf(TypeInfo type) {
        return type.file().packageName().isEmpty() ? null : type.file().packageName();
    }

    private void depend(TypeInfo from, TypeRef ref) {
        if (ref == null) {
            return;
        }
        TypeInfo to = index.resolve(ref, from);
        if (to != null && !to.id().equals(from.id())) {
            edges.add(new Edge(EdgeKind.DEPENDENCY, from.id(), to.id()));
        }
        ref.args().forEach(arg -> depend(from, arg));
    }

    private List<Step> steps(List<FlowFact> facts, TypeInfo type, String method) {
        return facts.stream().map(fact -> step(fact, type, method)).toList();
    }

    private Step step(FlowFact fact, TypeInfo type, String method) {
        return switch (fact) {
            case FlowFact.Call call -> {
                CallResolver.Target resolved = resolver.target(call, type);
                String target = resolved.id();
                if (target != null) {
                    edges.add(new Edge(EdgeKind.CALL, method, target));
                    String owner = target.contains("#") ? target.substring(0, target.indexOf('#')) : target;
                    if (!owner.equals(type.id())) {
                        edges.add(new Edge(EdgeKind.DEPENDENCY, type.id(), owner));
                    }
                }
                yield new Step.Call(call.line(), target, call.text(), resolved.external());
            }
            case FlowFact.Branch branch -> new Step.Branch(branch.line(), branch.subject(), branch.arms().stream()
                    .map(arm -> new Step.Arm(arm.condition(), steps(arm.facts(), type, method)))
                    .toList());
            case FlowFact.Loop loop -> new Step.Loop(loop.line(), loop.header(), steps(loop.facts(), type, method));
            case FlowFact.Return ret -> new Step.Return(ret.line());
            case FlowFact.Throw thrown -> new Step.Throw(thrown.line(), thrown.text());
        };
    }

    static String methodId(String typeId, MethodFacts method) {
        return typeId + "#" + method.name() + method.params().stream()
                .map(Param::type)
                .map(TypeRef::erasedSimpleName)
                .collect(Collectors.joining(",", "(", ")"));
    }

    private boolean add(Node node) {
        return nodes.putIfAbsent(node.id(), node) == null;
    }
}
