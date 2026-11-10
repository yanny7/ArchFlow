package com.yanny.archflow.facts;

import java.util.List;

public record Implementation(List<TypeRef> types, FlowFact.Call reference, int line, int endLine, List<FlowFact> body) {
    public Implementation {
        types = List.copyOf(types);
        body = List.copyOf(body);
    }

    public boolean isLambda() {
        return reference == null;
    }
}
