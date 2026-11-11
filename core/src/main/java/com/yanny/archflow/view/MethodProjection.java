package com.yanny.archflow.view;

import com.yanny.archflow.model.Edge;
import com.yanny.archflow.model.EdgeKind;
import com.yanny.archflow.model.Model;
import com.yanny.archflow.model.Node;
import com.yanny.archflow.model.NodeKind;
import com.yanny.archflow.model.Step;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

final class MethodProjection {
    private final Model model;
    private final ViewOptions options;
    private final Predicate<Node> visibleMethod;
    private final boolean unresolvedShown;
    private final Map<String, Node> nodes = new HashMap<>();
    private final Map<String, List<String>> overriders = new HashMap<>();
    private final Map<String, Set<String>> reached = new HashMap<>();

    MethodProjection(Model model, ViewOptions options, Predicate<Node> visibleMethod, boolean unresolvedShown) {
        this.model = model;
        this.options = options;
        this.visibleMethod = visibleMethod;
        this.unresolvedShown = unresolvedShown;
        model.nodes().forEach(node -> nodes.put(node.id(), node));
        model.edges().stream()
                .filter(edge -> edge.kind() == EdgeKind.OVERRIDE)
                .forEach(edge -> overriders.computeIfAbsent(edge.to(), k -> new ArrayList<>()).add(edge.from()));
    }

    Model project() {
        List<Node> kept = model.nodes().stream().filter(this::visible).map(this::reparent).toList();
        Map<String, List<Step>> flows = new LinkedHashMap<>();
        List<Edge> edges = new ArrayList<>();
        for (Node node : kept) {
            List<Step> flow = reduce(model.flow(node.id()));
            if (!flow.isEmpty()) {
                flows.put(node.id(), flow);
                calls(flow).forEach(target -> edges.add(new Edge(EdgeKind.CALL, node.id(), target)));
            }
        }
        for (Edge edge : model.edges()) {
            boolean override = edge.kind() == EdgeKind.OVERRIDE && visible(edge.from()) && visible(edge.to());
            if (edge.kind() == EdgeKind.DEPENDENCY || override) {
                edges.add(edge);
            }
        }
        return new Model(kept, edges, flows);
    }

    private Node reparent(Node node) {
        String parent = node.parent();
        while (parent != null && !visible(parent)) {
            parent = nodes.get(parent).parent();
        }
        return parent == null || parent.equals(node.parent()) ? node
                : new Node(node.id(), node.kind(), node.name(), parent, node.location());
    }

    private boolean visible(String id) {
        Node node = nodes.get(id);
        return node != null && visible(node);
    }

    private boolean visible(Node node) {
        return node.kind() != NodeKind.METHOD || visibleMethod.test(node);
    }

    private List<Step> reduce(List<Step> steps) {
        List<Step> out = new ArrayList<>();
        for (Step step : steps) {
            switch (step) {
                case Step.Call call when call.resolved() -> shown(call.target())
                        .forEach(target -> out.add(new Step.Call(call.line(), target, call.text(), false)));
                case Step.Call call when shown(call) -> out.add(call);
                case Step.Call ignored -> {
                }
                case Step.Branch branch -> {
                    List<Step.Arm> arms = branch.arms().stream()
                            .map(arm -> new Step.Arm(arm.condition(), reduce(arm.steps())))
                            .toList();
                    if (arms.stream().anyMatch(arm -> !arm.steps().isEmpty())) {
                        out.add(new Step.Branch(branch.line(), branch.subject(), arms));
                    }
                }
                case Step.Loop loop -> {
                    List<Step> body = reduce(loop.steps());
                    if (!body.isEmpty()) {
                        out.add(new Step.Loop(loop.line(), loop.header(), body));
                    }
                }
                case Step.Return ignored -> {
                }
                case Step.Throw ignored -> {
                }
            }
        }
        return out;
    }

    private boolean shown(Step.Call call) {
        return options.showExternal() || (!call.external() && unresolvedShown);
    }

    private Set<String> shown(String target) {
        return visible(target) ? Set.of(target) : reach(target);
    }

    private Set<String> reach(String hidden) {
        return reached.computeIfAbsent(hidden, start -> {
            Set<String> result = new LinkedHashSet<>();
            Set<String> seen = new HashSet<>();
            Deque<String> queue = new ArrayDeque<>(List.of(start));
            while (!queue.isEmpty()) {
                String current = queue.poll();
                if (!seen.add(current)) {
                    continue;
                }
                for (String next : successors(current)) {
                    if (visible(next)) {
                        result.add(next);
                    } else {
                        queue.add(next);
                    }
                }
            }
            return result;
        });
    }

    private Set<String> successors(String method) {
        Set<String> successors = calls(model.flow(method));
        successors.addAll(overriders.getOrDefault(method, List.of()));
        return successors;
    }

    private static Set<String> calls(List<Step> steps) {
        Set<String> targets = new LinkedHashSet<>();
        collect(steps, targets);
        return targets;
    }

    private static void collect(List<Step> steps, Set<String> targets) {
        for (Step step : steps) {
            switch (step) {
                case Step.Call call when call.resolved() -> targets.add(call.target());
                case Step.Branch branch -> branch.arms().forEach(arm -> collect(arm.steps(), targets));
                case Step.Loop loop -> collect(loop.steps(), targets);
                default -> {
                }
            }
        }
    }
}
