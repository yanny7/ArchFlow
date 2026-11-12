package com.yanny.archflow.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public record FlowChart(List<Box> boxes, List<Arrow> arrows) {
    public enum Kind { START, END, CALL, BRANCH, ARM, LOOP, RETURN, THROW }

    public enum Change { ADDED, REMOVED, INSIDE }

    public record Box(int id, Kind kind, String text, int line, String target, Change change) {
    }

    public record Arrow(int from, int to) {
    }

    public FlowChart {
        boxes = List.copyOf(boxes);
        arrows = List.copyOf(arrows);
    }

    public static FlowChart of(List<Step> steps) {
        return of(steps, steps);
    }

    public static FlowChart of(List<Step> before, List<Step> after) {
        Builder builder = new Builder();
        List<Integer> open = builder.merge(before, after, List.of(builder.add(Kind.START, "start", FlowText.Line.NONE, null)));
        int end = builder.add(Kind.END, "end", FlowText.Line.NONE, null);
        builder.link(open, end);
        builder.link(builder.returns, end);
        return new FlowChart(builder.boxes, builder.arrows);
    }

    private record Pair<T>(T before, T after) {
    }

    private static final class Builder {
        private final List<Box> boxes = new ArrayList<>();
        private final List<Arrow> arrows = new ArrayList<>();
        private final List<Integer> returns = new ArrayList<>();
        private Change change;
        private int changed;

        private int add(Kind kind, String text, int line, String target) {
            boxes.add(new Box(boxes.size(), kind, text, line, target, change));
            if (change != null) {
                changed++;
            }
            return boxes.size() - 1;
        }

        private void link(List<Integer> from, int to) {
            from.forEach(id -> arrows.add(new Arrow(id, to)));
        }

        private List<Integer> merge(List<Step> before, List<Step> after, List<Integer> entry) {
            List<Integer> open = entry;
            for (Pair<Step> pair : align(before, after, Builder::key, step -> exact(List.of(step)))) {
                if (pair.after() == null) {
                    List<Integer> exits = marked(Change.REMOVED, pair.before(), open);
                    open = exits.isEmpty() ? open : exits;
                } else if (pair.before() == null) {
                    open = marked(Change.ADDED, pair.after(), open);
                } else {
                    open = same(pair.before(), pair.after(), open);
                }
            }
            return open;
        }

        private List<Integer> marked(Change mark, Step step, List<Integer> open) {
            change = mark;
            List<Integer> exits = step(step, open);
            change = null;
            return exits;
        }

        private List<Integer> same(Step before, Step after, List<Integer> open) {
            int changedBefore = changed;
            return switch (after) {
                case Step.Branch branch -> {
                    int decision = add(Kind.BRANCH, FlowText.branchLabel(branch), branch.line(), null);
                    link(open, decision);
                    List<Integer> exits = new ArrayList<>();
                    List<Step.Arm> oldArms = ((Step.Branch) before).arms();
                    for (Pair<Step.Arm> pair : align(oldArms, branch.arms(), arm -> String.valueOf(arm.condition()), arm -> exact(arm.steps()))) {
                        if (pair.before() == null || pair.after() == null) {
                            Step.Arm arm = pair.after() == null ? pair.before() : pair.after();
                            change = pair.after() == null ? Change.REMOVED : Change.ADDED;
                            exits.addAll(arm(branch, arm, decision));
                            change = null;
                        } else {
                            int condition = add(Kind.ARM, FlowText.armLabel(branch, pair.after()), FlowText.Line.NONE, null);
                            exits.addAll(merge(pair.before().steps(), pair.after().steps(), enter(List.of(decision), condition)));
                        }
                    }
                    exits.addAll(otherwise(branch, decision));
                    inside(decision, changedBefore);
                    yield exits;
                }
                case Step.Loop loop -> {
                    int header = add(Kind.LOOP, "loop " + loop.header(), loop.line(), null);
                    link(open, header);
                    link(merge(((Step.Loop) before).steps(), loop.steps(), List.of(header)), header);
                    inside(header, changedBefore);
                    yield List.of(header);
                }
                default -> step(after, open);
            };
        }

        private void inside(int id, int changedBefore) {
            if (changed > changedBefore) {
                Box box = boxes.get(id);
                boxes.set(id, new Box(box.id(), box.kind(), box.text(), box.line(), box.target(), Change.INSIDE));
            }
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
                    branch.arms().forEach(arm -> exits.addAll(arm(branch, arm, decision)));
                    exits.addAll(otherwise(branch, decision));
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

        private List<Integer> arm(Step.Branch branch, Step.Arm arm, int decision) {
            int condition = add(Kind.ARM, FlowText.armLabel(branch, arm), FlowText.Line.NONE, null);
            return steps(arm.steps(), enter(List.of(decision), condition));
        }

        private List<Integer> otherwise(Step.Branch branch, int decision) {
            if (branch.arms().stream().allMatch(arm -> arm.condition() != null)) {
                return enter(List.of(decision), add(Kind.ARM, FlowText.otherwise(branch), FlowText.Line.NONE, null));
            }
            return List.of();
        }

        private List<Integer> enter(List<Integer> open, int box) {
            link(open, box);
            return List.of(box);
        }

        private static String key(Step step) {
            return switch (step) {
                case Step.Call call -> "call " + FlowText.target(call);
                case Step.Branch branch -> FlowText.branchLabel(branch);
                case Step.Loop loop -> "loop " + loop.header();
                case Step.Return ignored -> "return";
                case Step.Throw thrown -> "throw " + thrown.text();
            };
        }

        private static String exact(List<Step> steps) {
            return FlowText.lines(steps).stream().map(line -> line.depth() + " " + line.text()).collect(Collectors.joining("\n"));
        }

        private static <T> List<Pair<T>> align(List<T> before, List<T> after, Function<T, String> key, Function<T, String> exact) {
            List<String> beforeKeys = before.stream().map(key).toList();
            List<String> afterKeys = after.stream().map(key).toList();
            List<String> beforeExact = before.stream().map(exact).toList();
            List<String> afterExact = after.stream().map(exact).toList();
            int[][] score = new int[before.size() + 1][after.size() + 1];
            for (int i = before.size() - 1; i >= 0; i--) {
                for (int j = after.size() - 1; j >= 0; j--) {
                    int skip = Math.max(score[i + 1][j], score[i][j + 1]);
                    int match = weight(beforeKeys, afterKeys, beforeExact, afterExact, i, j);
                    score[i][j] = match == 0 ? skip : Math.max(skip, score[i + 1][j + 1] + match);
                }
            }
            List<Pair<T>> pairs = new ArrayList<>();
            int i = 0;
            int j = 0;
            while (i < before.size() || j < after.size()) {
                if (i < before.size() && j < after.size()) {
                    int match = weight(beforeKeys, afterKeys, beforeExact, afterExact, i, j);
                    if (match > 0 && score[i][j] == score[i + 1][j + 1] + match) {
                        pairs.add(new Pair<>(before.get(i++), after.get(j++)));
                        continue;
                    }
                }
                if (j == after.size() || i < before.size() && score[i][j] == score[i + 1][j]) {
                    pairs.add(new Pair<>(before.get(i++), null));
                } else {
                    pairs.add(new Pair<>(null, after.get(j++)));
                }
            }
            return pairs;
        }

        private static int weight(List<String> beforeKeys, List<String> afterKeys, List<String> beforeExact, List<String> afterExact, int i, int j) {
            if (!beforeKeys.get(i).equals(afterKeys.get(j))) {
                return 0;
            }
            return beforeExact.get(i).equals(afterExact.get(j)) ? 2 : 1;
        }
    }
}
