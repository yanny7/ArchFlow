package com.yanny.archflow.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class DiffText {
    private DiffText() {
    }

    public static List<String> lines(Diff diff, boolean inside) {
        List<String> lines = new ArrayList<>();
        for (NodeChange change : diff.nodes()) {
            if (inside || change.change() != Change.INSIDE) {
                String previous = change.previousId() == null ? "" : " (was " + change.previousId() + ")";
                lines.add(mark(change.change()) + " " + change.node().kind().name().toLowerCase() + " " + change.node().id() + previous);
            }
        }
        Set<String> appeared = diff.nodes().stream()
                .filter(change -> change.change() == Change.ADDED || change.change() == Change.REMOVED)
                .map(change -> change.node().id())
                .collect(Collectors.toSet());
        for (EdgeChange change : diff.edges()) {
            if (appeared.contains(change.edge().from())) {
                continue;
            }
            lines.add(mark(change.change()) + " " + change.edge().kind().name().toLowerCase() + " "
                    + change.edge().from() + " -> " + change.edge().to());
        }
        return lines;
    }

    private static String mark(Change change) {
        return switch (change) {
            case ADDED -> "+";
            case REMOVED -> "-";
            case CHANGED -> "~";
            case INSIDE -> "*";
        };
    }
}
