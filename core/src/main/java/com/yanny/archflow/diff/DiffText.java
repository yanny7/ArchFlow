package com.yanny.archflow.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class DiffText {
    public record Entry(String target, String to, Change change, String text) {
        public String line() {
            return mark(change) + " " + text;
        }
    }

    private DiffText() {
    }

    public static List<String> lines(Diff diff, boolean inside) {
        return entries(diff, inside).stream().map(Entry::line).toList();
    }

    public static List<Entry> entries(Diff diff, boolean inside) {
        List<Entry> entries = new ArrayList<>();
        for (NodeChange change : diff.nodes()) {
            if (inside || change.change() != Change.INSIDE) {
                String previous = change.previousId() == null ? "" : " (was " + change.previousId() + ")";
                String text = change.node().kind().name().toLowerCase() + " " + change.node().id() + previous;
                entries.add(new Entry(change.node().id(), null, change.change(), text));
            }
        }
        Set<String> appeared = diff.nodes().stream()
                .filter(change -> change.change() == Change.ADDED || change.change() == Change.REMOVED)
                .map(change -> change.node().id())
                .collect(Collectors.toSet());
        for (EdgeChange change : diff.edges()) {
            if (!appeared.contains(change.edge().from())) {
                String text = change.edge().kind().name().toLowerCase() + " " + change.edge().from() + " -> " + change.edge().to();
                entries.add(new Entry(change.edge().from(), change.edge().to(), change.change(), text));
            }
        }
        return entries;
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
