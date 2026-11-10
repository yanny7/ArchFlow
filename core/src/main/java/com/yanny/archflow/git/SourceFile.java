package com.yanny.archflow.git;

import java.util.function.Supplier;

public record SourceFile(String path, String blobId, Supplier<String> content) {
}
