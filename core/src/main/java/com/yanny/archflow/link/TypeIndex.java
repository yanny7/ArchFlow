package com.yanny.archflow.link;

import com.yanny.archflow.facts.FieldFacts;
import com.yanny.archflow.facts.FileFacts;
import com.yanny.archflow.facts.Import;
import com.yanny.archflow.facts.MethodFacts;
import com.yanny.archflow.facts.TypeFacts;
import com.yanny.archflow.facts.TypeRef;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

final class TypeIndex {
    record TypeInfo(String id, TypeFacts facts, FileFacts file, TypeInfo enclosing) {
    }

    record FieldHit(TypeInfo owner, FieldFacts field) {
    }

    record MethodHit(TypeInfo owner, MethodFacts method) {
    }

    private final Map<String, TypeInfo> types = new LinkedHashMap<>();

    TypeIndex(Collection<FileFacts> files) {
        for (FileFacts file : files) {
            String prefix = file.packageName().isEmpty() ? "" : file.packageName() + ".";
            file.types().forEach(type -> add(file, type, prefix, null));
        }
    }

    private void add(FileFacts file, TypeFacts type, String prefix, TypeInfo enclosing) {
        TypeInfo info = new TypeInfo(prefix + type.name(), type, file, enclosing);
        if (types.putIfAbsent(info.id(), info) == null) {
            type.types().forEach(nested -> add(file, nested, info.id() + ".", info));
        }
    }

    Collection<TypeInfo> types() {
        return types.values();
    }

    TypeInfo get(String id) {
        return types.get(id);
    }

    TypeInfo resolve(TypeRef ref, TypeInfo scope) {
        return ref == null ? null : resolve(ref.name(), scope);
    }

    TypeInfo resolve(String name, TypeInfo scope) {
        int dot = name.indexOf('.');
        if (dot < 0) {
            return resolveSimple(name, scope);
        }
        TypeInfo head = resolveSimple(name.substring(0, dot), scope);
        return head != null ? types.get(head.id() + name.substring(dot)) : types.get(name);
    }

    private TypeInfo resolveSimple(String name, TypeInfo scope) {
        for (TypeInfo type = scope; type != null; type = type.enclosing()) {
            if (type.facts().name().equals(name)) {
                return type;
            }
            TypeInfo nested = types.get(type.id() + "." + name);
            if (nested != null) {
                return nested;
            }
        }
        FileFacts file = scope.file();
        for (Import imp : file.imports()) {
            if (!imp.isStatic() && !imp.asterisk() && lastSegment(imp.name()).equals(name)) {
                return types.get(imp.name());
            }
        }
        TypeInfo samePackage = types.get(file.packageName().isEmpty() ? name : file.packageName() + "." + name);
        if (samePackage != null) {
            return samePackage;
        }
        for (Import imp : file.imports()) {
            if (imp.asterisk()) {
                TypeInfo imported = types.get(imp.name() + "." + name);
                if (imported != null) {
                    return imported;
                }
            }
        }
        return null;
    }

    TypeInfo superclass(TypeInfo type) {
        return resolve(type.facts().superclass(), type);
    }

    FieldHit field(TypeInfo type, String name) {
        return inHierarchy(type, t -> t.facts().fields().stream()
                .filter(f -> f.name().equals(name))
                .findFirst()
                .map(f -> new FieldHit(t, f))
                .orElse(null));
    }

    List<MethodHit> methods(TypeInfo type, String name, List<?> args) {
        return inHierarchy(type, t -> {
            List<MethodHit> hits = t.facts().methods().stream()
                    .filter(m -> m.name().equals(name) && accepts(m, args))
                    .map(m -> new MethodHit(t, m))
                    .toList();
            return hits.isEmpty() ? null : hits;
        });
    }

    List<MethodHit> constructors(TypeInfo type, List<?> args) {
        return type.facts().methods().stream()
                .filter(m -> m.isConstructor() && accepts(m, args))
                .map(m -> new MethodHit(type, m))
                .toList();
    }

    boolean isSubtype(TypeInfo type, TypeInfo supertype) {
        return inHierarchy(type, t -> t.id().equals(supertype.id()) ? t : null) != null;
    }

    private static boolean accepts(MethodFacts method, List<?> args) {
        int params = method.params().size();
        if (args == null || args.size() == params) {
            return true;
        }
        return params > 0 && method.params().getLast().varargs() && args.size() >= params - 1;
    }

    private <T> T inHierarchy(TypeInfo type, Function<TypeInfo, T> lookup) {
        Deque<TypeInfo> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        queue.add(type);
        while (!queue.isEmpty()) {
            TypeInfo current = queue.poll();
            if (!seen.add(current.id())) {
                continue;
            }
            T found = lookup.apply(current);
            if (found != null) {
                return found;
            }
            for (TypeRef supertype : current.facts().supertypes()) {
                TypeInfo resolved = resolve(supertype, current);
                if (resolved != null) {
                    queue.add(resolved);
                }
            }
        }
        return null;
    }

    static String lastSegment(String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }
}
