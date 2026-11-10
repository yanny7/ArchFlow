package com.yanny.archflow.diff;

import com.yanny.archflow.model.Node;

public record NodeChange(Node node, Change change) {
}
