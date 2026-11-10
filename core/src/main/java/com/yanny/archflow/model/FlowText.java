package com.yanny.archflow.model;

import java.util.ArrayList;
import java.util.List;

public final class FlowText {
    public record Line(int line, int depth, String text) {
        public static final int NONE = 0;
    }

    private FlowText() {
    }

    public static List<Line> lines(List<Step> steps) {
        List<Line> lines = new ArrayList<>();
        add(steps, 0, lines);
        return lines;
    }

    private static void add(List<Step> steps, int depth, List<Line> lines) {
        for (Step step : steps) {
            switch (step) {
                case Step.Call call -> lines.add(new Line(call.line(), depth, "call " + target(call)));
                case Step.Branch branch -> {
                    lines.add(new Line(branch.line(), depth, branch.subject() == null ? "if" : "switch " + branch.subject()));
                    for (Step.Arm arm : branch.arms()) {
                        lines.add(new Line(Line.NONE, depth + 1, armLabel(branch, arm)));
                        add(arm.steps(), depth + 2, lines);
                    }
                }
                case Step.Loop loop -> {
                    lines.add(new Line(loop.line(), depth, "loop " + loop.header()));
                    add(loop.steps(), depth + 1, lines);
                }
                case Step.Return ret -> lines.add(new Line(ret.line(), depth, "return"));
                case Step.Throw thrown -> lines.add(new Line(thrown.line(), depth, "throw " + thrown.text()));
            }
        }
    }

    private static String target(Step.Call call) {
        if (call.resolved()) {
            return call.target();
        }
        return (call.external() ? "external " : "? ") + call.text();
    }

    private static String armLabel(Step.Branch branch, Step.Arm arm) {
        if (arm.condition() == null) {
            return branch.subject() == null ? "else" : "default";
        }
        return branch.subject() == null ? arm.condition() : "case " + arm.condition();
    }
}
