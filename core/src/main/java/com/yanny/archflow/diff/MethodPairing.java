package com.yanny.archflow.diff;

import com.yanny.archflow.model.Edge;
import com.yanny.archflow.model.Model;
import com.yanny.archflow.model.Node;
import com.yanny.archflow.model.NodeKind;
import com.yanny.archflow.model.Step;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

final class MethodPairing {
    private MethodPairing() {
    }

    static Map<String, String> pairs(Model before, Model after) {
        Set<String> oldIds = before.nodes().stream().map(Node::id).collect(Collectors.toSet());
        Set<String> newIds = after.nodes().stream().map(Node::id).collect(Collectors.toSet());
        Map<String, List<Node>> removed = byTypeAndName(before, newIds);
        Map<String, List<Node>> added = byTypeAndName(after, oldIds);
        Map<String, String> pairs = new TreeMap<>();
        removed.forEach((key, oldNodes) -> {
            List<Node> newNodes = added.getOrDefault(key, List.of());
            if (oldNodes.size() == 1 && newNodes.size() == 1) {
                pairs.put(oldNodes.getFirst().id(), newNodes.getFirst().id());
            } else {
                pairByParameterCount(oldNodes, newNodes, pairs);
            }
        });
        return pairs;
    }

    private static void pairByParameterCount(List<Node> oldNodes, List<Node> newNodes, Map<String, String> pairs) {
        Map<Integer, List<Node>> oldByCount = oldNodes.stream().collect(Collectors.groupingBy(MethodPairing::parameterCount));
        Map<Integer, List<Node>> newByCount = newNodes.stream().collect(Collectors.groupingBy(MethodPairing::parameterCount));
        oldByCount.forEach((count, oldGroup) -> {
            List<Node> newGroup = newByCount.getOrDefault(count, List.of());
            if (oldGroup.size() == 1 && newGroup.size() == 1) {
                pairs.put(oldGroup.getFirst().id(), newGroup.getFirst().id());
            }
        });
    }

    private static int parameterCount(Node method) {
        String params = method.id().substring(method.id().indexOf('(') + 1, method.id().lastIndexOf(')'));
        return params.isEmpty() ? 0 : params.split(",").length;
    }

    private static Map<String, List<Node>> byTypeAndName(Model model, Set<String> otherIds) {
        Map<String, List<Node>> nodes = new HashMap<>();
        model.nodes().stream()
                .filter(node -> node.kind() == NodeKind.METHOD && !otherIds.contains(node.id()))
                .forEach(node -> nodes.computeIfAbsent(node.parent() + "#" + node.name(), k -> new ArrayList<>()).add(node));
        return nodes;
    }

    static Model rename(Model model, Map<String, String> renamed) {
        if (renamed.isEmpty()) {
            return model;
        }
        List<Node> nodes = model.nodes().stream()
                .map(n -> new Node(id(n.id(), renamed), n.kind(), n.name(), n.parent(), n.location()))
                .toList();
        List<Edge> edges = model.edges().stream()
                .map(e -> new Edge(e.kind(), id(e.from(), renamed), id(e.to(), renamed)))
                .toList();
        Map<String, List<Step>> flows = new HashMap<>();
        model.flows().forEach((method, steps) -> flows.put(id(method, renamed), steps(steps, renamed)));
        return new Model(nodes, edges, flows, model.sites());
    }

    private static List<Step> steps(List<Step> steps, Map<String, String> renamed) {
        return steps.stream().map(step -> step(step, renamed)).toList();
    }

    private static Step step(Step step, Map<String, String> renamed) {
        return switch (step) {
            case Step.Call call when call.resolved() ->
                    new Step.Call(call.line(), id(call.target(), renamed), call.text(), call.external());
            case Step.Branch branch -> new Step.Branch(branch.line(), branch.subject(), branch.arms().stream()
                    .map(arm -> new Step.Arm(arm.condition(), steps(arm.steps(), renamed)))
                    .toList());
            case Step.Loop loop -> new Step.Loop(loop.line(), loop.header(), steps(loop.steps(), renamed));
            default -> step;
        };
    }

    private static String id(String id, Map<String, String> renamed) {
        return renamed.getOrDefault(id, id);
    }
}
