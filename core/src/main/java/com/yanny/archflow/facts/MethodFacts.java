package com.yanny.archflow.facts;

import com.yanny.archflow.model.Location;

import java.util.List;

public record MethodFacts(String name, List<Param> params, TypeRef returnType, Location location, List<FlowFact> body) {
    public static final String CONSTRUCTOR = "<init>";
    public static final String STATIC_INIT = "<clinit>";
    public static final String INSTANCE_INIT = "<instance-init>";

    public MethodFacts {
        params = List.copyOf(params);
        body = List.copyOf(body);
    }

    public boolean isConstructor() {
        return name.equals(CONSTRUCTOR);
    }

    public boolean overridable() {
        return !name.startsWith("<");
    }
}
