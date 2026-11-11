package com.yanny.archflow.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlowChartTest {
    @Test
    void branchesFallThroughWithoutElseLoopsReturnToTheirHeaderAndReturnsEndTheMethod() {
        List<Step> steps = List.of(
                new Step.Call(1, "a.A#first()", "first()", false),
                new Step.Branch(2, null, List.of(new Step.Arm("x > 0", List.of(new Step.Return(3))))),
                new Step.Loop(4, "for (int i : items)", List.of(new Step.Call(5, null, "log(i)", true))),
                new Step.Branch(6, "kind", List.of(
                        new Step.Arm("A", List.of(new Step.Throw(7, "new IllegalStateException()"))),
                        new Step.Arm(null, List.of()))));

        assertEquals("""
                0 START start -> 1
                1 CALL a.A#first() -> 2
                2 BRANCH if -> 3, 5
                3 ARM x > 0 -> 4
                4 RETURN return -> 12
                5 ARM else -> 6
                6 LOOP loop for (int i : items) -> 7, 8
                7 CALL external log(i) -> 6
                8 BRANCH switch kind -> 9, 11
                9 ARM case A -> 10
                10 THROW throw new IllegalStateException() ->\s
                11 ARM default -> 12
                12 END end ->\s""", dump(FlowChart.of(steps)));
    }

    private static String dump(FlowChart chart) {
        return chart.boxes().stream()
                .map(box -> box.id() + " " + box.kind() + " " + box.text() + " -> " + chart.arrows().stream()
                        .filter(arrow -> arrow.from() == box.id())
                        .map(arrow -> String.valueOf(arrow.to()))
                        .collect(Collectors.joining(", ")))
                .collect(Collectors.joining("\n"));
    }
}
