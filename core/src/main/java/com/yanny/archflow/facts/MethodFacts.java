package com.yanny.archflow.facts;

import com.yanny.archflow.model.Location;

import java.util.List;

public record MethodFacts(String name, List<Param> params, TypeRef returnType, Location location, boolean abstractMethod,
                          List<FlowFact> body, List<Implementation> implementations) {
    public static final String CONSTRUCTOR = "<init>";
    public static final String STATIC_INIT = "<clinit>";
    public static final String INSTANCE_INIT = "<instance-init>";

    public MethodFacts {
        params = List.copyOf(params);
        body = List.copyOf(body);
        implementations = List.copyOf(implementations);
    }

    public boolean isConstructor() {
        return name.equals(CONSTRUCTOR);
    }

    public boolean overridable() {
        return !name.startsWith("<");
    }
}
