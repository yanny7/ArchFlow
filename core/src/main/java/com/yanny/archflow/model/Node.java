package com.yanny.archflow.model;

public record Node(String id, NodeKind kind, String name, String parent, Location location) {
    public int lines() {
        return location == null ? 0 : location.lines();
    }
}
