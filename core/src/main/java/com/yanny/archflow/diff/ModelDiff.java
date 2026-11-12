package com.yanny.archflow.diff;

import com.yanny.archflow.model.Edge;
import com.yanny.archflow.model.Model;
import com.yanny.archflow.model.Node;
import com.yanny.archflow.model.Step;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ModelDiff {
    private final Map<String, Node> before;
    private final Map<String, Node> after;
    private final Map<String, NodeChange> changes = new TreeMap<>();

    private ModelDiff(Model before, Model after) {
        this.before = byId(before);
        this.after = byId(after);
    }

    public static Diff diff(Model original, Model after) {
        Map<String, String> renamed = MethodPairing.pairs(original, after);
        Map<String, String> previous = new HashMap<>();
        renamed.forEach((oldId, newId) -> previous.put(newId, oldId));
        Model before = MethodPairing.rename(original, renamed);
        ModelDiff diff = new ModelDiff(before, after);
        diff.after.values().forEach(node -> {
            Node old = diff.before.get(node.id());
            if (old == null) {
                diff.changes.put(node.id(), new NodeChange(node, Change.ADDED));
            } else if (previous.containsKey(node.id()) || !normalize(before.flow(node.id())).equals(normalize(after.flow(node.id())))) {
                diff.changes.put(node.id(), new NodeChange(node, Change.CHANGED, previous.get(node.id()), before.flow(node.id())));
            }
        });
        diff.before.values().stream()
                .filter(node -> !diff.after.containsKey(node.id()))
                .forEach(node -> diff.changes.put(node.id(), new NodeChange(node, Change.REMOVED)));

        Set<Edge> oldEdges = new HashSet<>(before.edges());
        Set<Edge> newEdges = new HashSet<>(after.edges());
        List<EdgeChange> edges = new ArrayList<>();
        after.edges().stream().filter(edge -> !oldEdges.contains(edge)).forEach(edge -> edges.add(new EdgeChange(edge, Change.ADDED)));
        before.edges().stream().filter(edge -> !newEdges.contains(edge)).forEach(edge -> edges.add(new EdgeChange(edge, Change.REMOVED)));
        edges.sort(Comparator.comparing(EdgeChange::edge).thenComparing(EdgeChange::change));

        List<NodeChange> own = List.copyOf(diff.changes.values());
        own.forEach(change -> diff.markAncestors(change.node().parent()));
        edges.forEach(change -> diff.markAncestors(change.edge().from()));
        return new Diff(List.copyOf(diff.changes.values()), edges);
    }

    private void markAncestors(String id) {
        for (Node node = after.get(id); node != null; node = after.get(node.parent())) {
            if (changes.containsKey(node.id())) {
                if (changes.get(node.id()).change() != Change.INSIDE) {
                    continue;
                }
                return;
            }
            changes.put(node.id(), new NodeChange(node, Change.INSIDE));
        }
    }

    private static Map<String, Node> byId(Model model) {
        return model.nodes().stream().collect(Collectors.toMap(Node::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private static List<Step> normalize(List<Step> steps) {
        return steps.stream().map(ModelDiff::normalize).toList();
    }

    private static Step normalize(Step step) {
        return switch (step) {
            case Step.Call call -> new Step.Call(0, call.target(), call.resolved() ? "" : call.text(), call.external());
            case Step.Branch branch -> new Step.Branch(0, branch.subject(), branch.arms().stream()
                    .map(arm -> new Step.Arm(arm.condition(), normalize(arm.steps())))
                    .toList());
            case Step.Loop loop -> new Step.Loop(0, loop.header(), normalize(loop.steps()));
            case Step.Return ignored -> new Step.Return(0);
            case Step.Throw thrown -> new Step.Throw(0, thrown.text());
        };
    }
}
