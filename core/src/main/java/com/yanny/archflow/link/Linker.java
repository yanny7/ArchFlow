package com.yanny.archflow.link;

import com.yanny.archflow.facts.FileFacts;
import com.yanny.archflow.facts.FlowFact;
import com.yanny.archflow.facts.MethodFacts;
import com.yanny.archflow.facts.Param;
import com.yanny.archflow.facts.TypeFacts;
import com.yanny.archflow.facts.TypeRef;
import com.yanny.archflow.model.Model;
import com.yanny.archflow.model.Node;
import com.yanny.archflow.model.NodeKind;
import com.yanny.archflow.model.Step;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class Linker {
    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Map<String, List<Step>> flows = new LinkedHashMap<>();

    public static Model link(List<FileFacts> files) {
        Linker linker = new Linker();
        linker.packages(files);
        for (FileFacts file : files) {
            String parent = file.packageName().isEmpty() ? null : file.packageName();
            file.types().forEach(type -> linker.type(type, parent));
        }
        return new Model(List.copyOf(linker.nodes.values()), List.of(), linker.flows);
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
            String methodId = methodId(id, method);
            if (add(new Node(methodId, NodeKind.METHOD, name, id, method.location())) && !method.body().isEmpty()) {
                flows.put(methodId, steps(method.body()));
            }
        }
        type.types().forEach(nested -> type(nested, id));
    }

    private List<Step> steps(List<FlowFact> facts) {
        return facts.stream().map(this::step).toList();
    }

    private Step step(FlowFact fact) {
        return switch (fact) {
            case FlowFact.Call call -> new Step.Call(call.line(), null, call.text());
            case FlowFact.Branch branch -> new Step.Branch(branch.line(), branch.subject(), branch.arms().stream()
                    .map(arm -> new Step.Arm(arm.condition(), steps(arm.facts())))
                    .toList());
            case FlowFact.Loop loop -> new Step.Loop(loop.line(), loop.header(), steps(loop.facts()));
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
