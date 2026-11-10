package com.yanny.archflow.facts;

import com.yanny.archflow.model.Location;

import java.util.ArrayList;
import java.util.List;

public record TypeFacts(String name, Location location, TypeRef superclass, List<TypeRef> interfaces,
                        List<FieldFacts> fields, List<MethodFacts> methods, List<TypeFacts> types) {
    public TypeFacts {
        interfaces = List.copyOf(interfaces);
        fields = List.copyOf(fields);
        methods = List.copyOf(methods);
        types = List.copyOf(types);
    }

    public List<TypeRef> supertypes() {
        List<TypeRef> supertypes = new ArrayList<>();
        if (superclass != null) {
            supertypes.add(superclass);
        }
        supertypes.addAll(interfaces);
        return supertypes;
    }
}
