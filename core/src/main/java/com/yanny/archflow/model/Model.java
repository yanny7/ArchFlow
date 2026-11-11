package com.yanny.archflow.model;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record Model(List<Node> nodes, List<Edge> edges, Map<String, List<Step>> flows, List<Site> sites) {
    public Model {
        nodes = nodes.stream().sorted(Comparator.comparing(Node::id)).toList();
        edges = edges.stream().distinct().sorted().toList();
        TreeMap<String, List<Step>> sorted = new TreeMap<>();
        flows.forEach((method, steps) -> sorted.put(method, List.copyOf(steps)));
        flows = Collections.unmodifiableSortedMap(sorted);
        sites = sites.stream().distinct().sorted().toList();
    }

    public List<Step> flow(String method) {
        return flows.getOrDefault(method, List.of());
    }
}
