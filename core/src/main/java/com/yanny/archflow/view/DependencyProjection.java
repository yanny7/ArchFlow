package com.yanny.archflow.view;

import com.yanny.archflow.model.EdgeKind;
import com.yanny.archflow.model.Model;
import com.yanny.archflow.model.NodeKind;

import java.util.Map;

final class DependencyProjection {
    private DependencyProjection() {
    }

    static Model project(Model model) {
        return new Model(
                model.nodes().stream().filter(node -> node.kind() != NodeKind.METHOD).toList(),
                model.edges().stream().filter(edge -> edge.kind() == EdgeKind.DEPENDENCY).toList(),
                Map.of());
    }
}
