package com.yanny.archflow.diff;

import java.util.List;

public record Diff(List<NodeChange> nodes, List<EdgeChange> edges) {
    public Diff {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    public boolean isEmpty() {
        return nodes.isEmpty() && edges.isEmpty();
    }
}
