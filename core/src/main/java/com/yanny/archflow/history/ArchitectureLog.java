package com.yanny.archflow.history;

import com.yanny.archflow.diff.Diff;
import com.yanny.archflow.diff.ModelDiff;
import com.yanny.archflow.git.GitRepo;
import com.yanny.archflow.model.Model;
import com.yanny.archflow.view.View;
import com.yanny.archflow.view.ViewOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ArchitectureLog {
    private static final Model EMPTY = new Model(List.of(), List.of(), Map.of(), List.of());

    private final GitRepo git;
    private final ModelSource source;
    private final View view;
    private final ViewOptions options;
    private final Map<String, Model> projected = new HashMap<>();

    public ArchitectureLog(GitRepo git, ModelSource source, View view, ViewOptions options) {
        this.git = git;
        this.source = source;
        this.view = view;
        this.options = options;
    }

    public List<LogEntry> log(String from, String to) throws IOException {
        List<LogEntry> entries = new ArrayList<>();
        for (String commit : git.range(from, to)) {
            String parent = git.parent(commit);
            Diff diff = ModelDiff.diff(parent == null ? EMPTY : project(parent), project(commit));
            if (!diff.isEmpty()) {
                entries.add(new LogEntry(commit, git.message(commit), diff));
            }
        }
        return entries;
    }

    private Model project(String commit) throws IOException {
        Model model = projected.get(commit);
        if (model == null) {
            model = view.project(source.commit(commit), options);
            projected.put(commit, model);
        }
        return model;
    }
}
