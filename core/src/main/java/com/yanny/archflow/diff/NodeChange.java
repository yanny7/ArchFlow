package com.yanny.archflow.diff;

import com.yanny.archflow.model.Node;
import com.yanny.archflow.model.Step;

import java.util.List;

public record NodeChange(Node node, Change change, String previousId, List<Step> previousFlow) {
    public NodeChange {
        previousFlow = List.copyOf(previousFlow);
    }

    public NodeChange(Node node, Change change) {
        this(node, change, null, List.of());
    }
}
