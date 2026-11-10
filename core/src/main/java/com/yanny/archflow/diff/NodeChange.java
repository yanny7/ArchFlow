package com.yanny.archflow.diff;

import com.yanny.archflow.model.Node;

public record NodeChange(Node node, Change change, String previousId) {
    public NodeChange(Node node, Change change) {
        this(node, change, null);
    }
}
