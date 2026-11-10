package com.yanny.archflow.facts;

import com.yanny.archflow.model.Location;

import java.util.List;

public record TypeFacts(String name, Location location, List<TypeRef> supertypes, List<FieldFacts> fields,
                        List<MethodFacts> methods, List<TypeFacts> types) {
    public TypeFacts {
        supertypes = List.copyOf(supertypes);
        fields = List.copyOf(fields);
        methods = List.copyOf(methods);
        types = List.copyOf(types);
    }
}
