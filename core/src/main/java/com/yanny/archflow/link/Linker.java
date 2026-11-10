package com.yanny.archflow.link;

import com.yanny.archflow.facts.FileFacts;
import com.yanny.archflow.facts.MethodFacts;
import com.yanny.archflow.facts.Param;
import com.yanny.archflow.facts.TypeFacts;
import com.yanny.archflow.facts.TypeRef;
import com.yanny.archflow.model.Model;
import com.yanny.archflow.model.Node;
import com.yanny.archflow.model.NodeKind;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class Linker {
    private final Map<String, Node> nodes = new LinkedHashMap<>();

    public static Model link(List<FileFacts> files) {
        Linker linker = new Linker();
        linker.packages(files);
        for (FileFacts file : files) {
            String parent = file.packageName().isEmpty() ? null : file.packageName();
            file.types().forEach(type -> linker.type(type, parent));
        }
        return new Model(List.copyOf(linker.nodes.values()), List.of(), Map.of());
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

    private void type(TypeFacts type, String parent) {
        String id = parent == null ? type.name() : parent + "." + type.name();
        if (!add(new Node(id, NodeKind.TYPE, type.name(), parent, type.location()))) {
            return;
        }
        for (MethodFacts method : type.methods()) {
            String name = method.isConstructor() ? type.name() : method.name();
            add(new Node(methodId(id, method), NodeKind.METHOD, name, id, method.location()));
        }
        type.types().forEach(nested -> type(nested, id));
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
