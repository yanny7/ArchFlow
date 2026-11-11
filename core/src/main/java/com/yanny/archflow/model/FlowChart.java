package com.yanny.archflow.model;

import java.util.ArrayList;
import java.util.List;

public record FlowChart(List<Box> boxes, List<Arrow> arrows) {
    public enum Kind { START, END, CALL, BRANCH, ARM, LOOP, RETURN, THROW }

    public record Box(int id, Kind kind, String text, int line, String target) {
    }

    public record Arrow(int from, int to) {
    }

    public FlowChart {
        boxes = List.copyOf(boxes);
        arrows = List.copyOf(arrows);
    }

    public static FlowChart of(List<Step> steps) {
        Builder builder = new Builder();
        List<Integer> open = builder.steps(steps, List.of(builder.add(Kind.START, "start", FlowText.Line.NONE, null)));
        int end = builder.add(Kind.END, "end", FlowText.Line.NONE, null);
        builder.link(open, end);
        builder.link(builder.returns, end);
        return new FlowChart(builder.boxes, builder.arrows);
    }

    private static final class Builder {
        private final List<Box> boxes = new ArrayList<>();
        private final List<Arrow> arrows = new ArrayList<>();
        private final List<Integer> returns = new ArrayList<>();

        private int add(Kind kind, String text, int line, String target) {
            boxes.add(new Box(boxes.size(), kind, text, line, target));
            return boxes.size() - 1;
        }

        private void link(List<Integer> from, int to) {
            from.forEach(id -> arrows.add(new Arrow(id, to)));
        }

        private List<Integer> steps(List<Step> steps, List<Integer> entry) {
            List<Integer> open = entry;
            for (Step step : steps) {
                open = step(step, open);
            }
            return open;
        }

        private List<Integer> step(Step step, List<Integer> open) {
            return switch (step) {
                case Step.Call call -> enter(open, add(Kind.CALL, FlowText.target(call), call.line(), call.target()));
                case Step.Branch branch -> {
                    int decision = add(Kind.BRANCH, FlowText.branchLabel(branch), branch.line(), null);
                    link(open, decision);
                    List<Integer> exits = new ArrayList<>();
                    for (Step.Arm arm : branch.arms()) {
                        int condition = add(Kind.ARM, FlowText.armLabel(branch, arm), FlowText.Line.NONE, null);
                        exits.addAll(steps(arm.steps(), enter(List.of(decision), condition)));
                    }
                    if (branch.arms().stream().allMatch(arm -> arm.condition() != null)) {
                        exits.addAll(enter(List.of(decision), add(Kind.ARM, FlowText.otherwise(branch), FlowText.Line.NONE, null)));
                    }
                    yield exits;
                }
                case Step.Loop loop -> {
                    List<Integer> header = enter(open, add(Kind.LOOP, "loop " + loop.header(), loop.line(), null));
                    link(steps(loop.steps(), header), header.getFirst());
                    yield header;
                }
                case Step.Return ret -> {
                    returns.addAll(enter(open, add(Kind.RETURN, "return", ret.line(), null)));
                    yield List.of();
                }
                case Step.Throw thrown -> {
                    enter(open, add(Kind.THROW, "throw " + thrown.text(), thrown.line(), null));
                    yield List.of();
                }
            };
        }

        private List<Integer> enter(List<Integer> open, int box) {
            link(open, box);
            return List.of(box);
        }
    }
}
