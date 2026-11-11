package com.yanny.archflow.model;

import java.util.Comparator;

public record Site(Edge edge, SiteKind kind, String file, int line, String text) implements Comparable<Site> {
    private static final Comparator<Site> ORDER = Comparator.comparing(Site::edge)
            .thenComparing(Site::file)
            .thenComparingInt(Site::line)
            .thenComparing(Site::kind)
            .thenComparing(Site::text);

    @Override
    public int compareTo(Site other) {
        return ORDER.compare(this, other);
    }
}
