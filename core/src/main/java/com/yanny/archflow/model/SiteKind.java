package com.yanny.archflow.model;

public enum SiteKind {
    CALL,
    FIELD,
    IMPORT,
    PARAMETER,
    RETURN,
    SUPERTYPE,
    DECLARATION;

    public boolean declares() {
        return this == PARAMETER || this == RETURN || this == SUPERTYPE || this == DECLARATION;
    }
}
