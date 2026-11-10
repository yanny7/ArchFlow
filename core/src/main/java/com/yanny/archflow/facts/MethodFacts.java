package com.yanny.archflow.facts;

import com.yanny.archflow.model.Location;

import java.util.List;

public record MethodFacts(String name, List<Param> params, TypeRef returnType, Location location) {
    public static final String CONSTRUCTOR = "<init>";

    public MethodFacts {
        params = List.copyOf(params);
    }

    public boolean isConstructor() {
        return name.equals(CONSTRUCTOR);
    }
}
