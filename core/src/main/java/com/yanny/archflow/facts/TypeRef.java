package com.yanny.archflow.facts;

import java.util.List;

public record TypeRef(String name, List<TypeRef> args, int dims) {
    public TypeRef {
        args = List.copyOf(args);
    }

    public TypeRef withDims(int extra) {
        return new TypeRef(name, args, dims + extra);
    }

    public String erasedSimpleName() {
        return name.substring(name.lastIndexOf('.') + 1) + "[]".repeat(dims);
    }
}
