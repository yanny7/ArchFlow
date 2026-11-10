package com.yanny.archflow.plugin;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.yanny.archflow.git.GitRepo;
import com.yanny.archflow.history.ModelSource;
import com.yanny.archflow.model.Model;
import com.yanny.archflow.present.ViewerData;
import com.yanny.archflow.view.ViewOptions;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Service(Service.Level.PROJECT)
public final class ArchflowService implements Disposable {
    public record Selection(String from, String to, List<String> paths, int minLines) {
    }

    private final Project project;
    private GitRepo git;
    private ModelSource source;
    private List<String> sourcePaths;

    public ArchflowService(Project project) {
        this.project = project;
    }

    public static ArchflowService of(Project project) {
        return project.getService(ArchflowService.class);
    }

    public synchronized GitRepo git() throws IOException {
        if (git == null) {
            String base = project.getBasePath();
            if (base == null) {
                throw new IOException("The project has no directory");
            }
            git = new GitRepo(Path.of(base));
        }
        return git;
    }

    public synchronized List<GitRepo.CommitInfo> history(int limit) throws IOException {
        return git().history("HEAD", limit);
    }

    public synchronized String fileAt(String commit, String path) throws IOException {
        return git().fileAt(commit, path);
    }

    public synchronized String viewerData(Selection selection) throws IOException {
        ModelSource models = source(selection.paths());
        String afterCommit = selection.to() == null ? null : git().resolve(selection.to());
        Model after = afterCommit == null ? models.workingCopy() : models.commit(afterCommit);
        String beforeCommit = selection.from() == null ? null : git().resolve(selection.from());
        Model before = beforeCommit == null ? null : models.commit(beforeCommit);
        String title = project.getName() + " " + (beforeCommit == null ? "" : shortId(beforeCommit) + " → ")
                + (afterCommit == null ? "working copy" : shortId(afterCommit));
        ViewOptions options = ViewOptions.DEFAULT.withMinLines(selection.minLines());
        return ViewerData.json(ViewerData.data(title, beforeCommit, before, afterCommit, after, options));
    }

    static String shortId(String commit) {
        return commit.substring(0, 9);
    }

    private ModelSource source(List<String> paths) throws IOException {
        if (source == null || !paths.equals(sourcePaths)) {
            source = new ModelSource(git(), paths, Path.of(PathManager.getSystemPath(), "archflow"));
            sourcePaths = List.copyOf(paths);
        }
        return source;
    }

    @Override
    public synchronized void dispose() {
        if (git != null) {
            git.close();
        }
    }
}
