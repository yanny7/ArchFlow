package com.yanny.archflow.facts;

import java.util.List;

public record FileFacts(String file, String packageName, List<Import> imports, List<TypeFacts> types, String problem) {
    public FileFacts {
        imports = List.copyOf(imports);
        types = List.copyOf(types);
    }

    public static FileFacts failed(String file, String problem) {
        return new FileFacts(file, "", List.of(), List.of(), problem);
    }
}
