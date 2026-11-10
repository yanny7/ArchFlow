package com.yanny.archflow.history;

import com.yanny.archflow.diff.Diff;

public record LogEntry(String commit, String message, Diff diff) {
}
