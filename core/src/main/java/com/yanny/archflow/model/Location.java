package com.yanny.archflow.model;

public record Location(String file, int line, int endLine) {
    public int lines() {
        return endLine - line + 1;
    }
}
