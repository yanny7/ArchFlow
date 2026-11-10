package com.yanny.archflow.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ModelDump {
    private final Model model;
    private final List<String> lines = new ArrayList<>();

    private ModelDump(Model model) {
        this.model = model;
    }

    public static List<String> dump(Model model) {
        ModelDump dump = new ModelDump(model);
        dump.children(null, "");
        model.edges().forEach(e -> dump.lines.add(e.kind().name().toLowerCase() + " " + e.from() + " -> " + e.to()));
        return dump.lines;
    }

    private void children(Node parent, String indent) {
        String parentId = parent == null ? null : parent.id();
        for (Node node : model.nodes()) {
            if (Objects.equals(node.parent(), parentId)) {
                lines.add(indent + node.kind().name().toLowerCase() + " " + relativeId(node, parentId) + place(node, parent));
                steps(model.flow(node.id()), indent + "  ");
                children(node, indent + "  ");
            }
        }
    }

    private static String relativeId(Node node, String parentId) {
        return parentId == null ? node.id() : node.id().substring(parentId.length() + 1);
    }

    private static String place(Node node, Node parent) {
        Location location = node.location();
        if (location == null) {
            return "";
        }
        boolean sameFile = parent != null && parent.location() != null && parent.location().file().equals(location.file());
        return " " + (sameFile ? "" : location.file() + ":") + location.line() + "-" + location.endLine();
    }

    private void steps(List<Step> steps, String indent) {
        for (Step step : steps) {
            String prefix = indent + step.line() + " ";
            switch (step) {
                case Step.Call call -> lines.add(prefix + "call " + (call.resolved() ? call.target() : "? " + call.text()));
                case Step.Branch branch -> {
                    lines.add(prefix + (branch.subject() == null ? "if" : "switch " + branch.subject()));
                    for (Step.Arm arm : branch.arms()) {
                        lines.add(indent + "  " + armLabel(branch, arm));
                        steps(arm.steps(), indent + "    ");
                    }
                }
                case Step.Loop loop -> {
                    lines.add(prefix + "loop " + loop.header());
                    steps(loop.steps(), indent + "  ");
                }
                case Step.Return ignored -> lines.add(prefix + "return");
                case Step.Throw thrown -> lines.add(prefix + "throw " + thrown.text());
            }
        }
    }

    private static String armLabel(Step.Branch branch, Step.Arm arm) {
        if (arm.condition() == null) {
            return branch.subject() == null ? "else" : "default";
        }
        return branch.subject() == null ? arm.condition() : "case " + arm.condition();
    }
}
