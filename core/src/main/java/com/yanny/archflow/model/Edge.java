package com.yanny.archflow.model;

import java.util.Comparator;

public record Edge(EdgeKind kind, String from, String to) implements Comparable<Edge> {
    private static final Comparator<Edge> ORDER = Comparator.comparing(Edge::kind)
            .thenComparing(Edge::from)
            .thenComparing(Edge::to);

    @Override
    public int compareTo(Edge other) {
        return ORDER.compare(this, other);
    }
}
