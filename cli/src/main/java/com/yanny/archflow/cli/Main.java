package com.yanny.archflow.cli;

import com.yanny.archflow.diff.DiffText;
import com.yanny.archflow.diff.ModelDiff;
import com.yanny.archflow.git.GitRepo;
import com.yanny.archflow.history.ArchitectureLog;
import com.yanny.archflow.history.LogEntry;
import com.yanny.archflow.history.ModelSource;
import com.yanny.archflow.json.ModelJson;
import com.yanny.archflow.model.Model;
import com.yanny.archflow.model.ModelDump;
import com.yanny.archflow.present.ViewerData;
import com.yanny.archflow.viewer.ViewerPage;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.List;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            err.println(Options.USAGE);
            return 2;
        }
        long start = System.nanoTime();
        try (GitRepo git = new GitRepo(options.repo())) {
            ModelSource source = new ModelSource(git, options.paths(), options.cache());
            List<String> revisions = options.revisions();
            switch (options.command()) {
                case "model" -> model(options, git, source, revisions, out);
                case "diff" -> diff(options, git, source, revisions, out);
                case "log" -> log(options, git, source, revisions, out);
                case "html" -> html(options, git, source, revisions, out);
                default -> {
                    err.println("unknown command " + options.command());
                    err.println(Options.USAGE);
                    return 2;
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            err.println(e.getMessage());
            return 1;
        }
        err.printf("%s: %d ms%n", options.command(), (System.nanoTime() - start) / 1_000_000);
        return 0;
    }

    private static void model(Options options, GitRepo git, ModelSource source, List<String> revisions, PrintStream out)
            throws IOException {
        Model model = view(options, revisions.isEmpty() ? source.workingCopy() : source.commit(git.resolve(revisions.getFirst())));
        if (options.json()) {
            out.println(ModelJson.write(model));
        } else {
            ModelDump.dump(model).forEach(out::println);
        }
    }

    private static void diff(Options options, GitRepo git, ModelSource source, List<String> revisions, PrintStream out)
            throws IOException {
        if (revisions.isEmpty()) {
            throw new IllegalArgumentException("diff needs FROM");
        }
        Model before = view(options, source.commit(git.resolve(revisions.getFirst())));
        Model after = view(options, revisions.size() > 1 ? source.commit(git.resolve(revisions.get(1))) : source.workingCopy());
        DiffText.lines(ModelDiff.diff(before, after), false).forEach(out::println);
    }

    private static void log(Options options, GitRepo git, ModelSource source, List<String> revisions, PrintStream out)
            throws IOException {
        String[] range = revisions.size() == 1 ? revisions.getFirst().split("\\.\\.", 2) : revisions.toArray(String[]::new);
        if (range.length != 2) {
            throw new IllegalArgumentException("log needs FROM..TO");
        }
        ArchitectureLog log = new ArchitectureLog(git, source, options.view(), options.viewOptions());
        for (LogEntry entry : log.log(git.resolve(range[0]), git.resolve(range[1]))) {
            out.println(entry.commit().substring(0, 9) + " " + entry.message());
            DiffText.lines(entry.diff(), false).forEach(line -> out.println("  " + line));
        }
    }

    private static void html(Options options, GitRepo git, ModelSource source, List<String> revisions, PrintStream out)
            throws IOException {
        String beforeCommit = null;
        Model before = null;
        String commit = null;
        Model after;
        if (revisions.isEmpty()) {
            after = source.workingCopy();
        } else {
            commit = git.resolve(revisions.getLast());
            after = source.commit(commit);
            if (revisions.size() > 1) {
                beforeCommit = git.resolve(revisions.getFirst());
                before = source.commit(beforeCommit);
            }
        }
        String name = options.repo().toAbsolutePath().normalize().getFileName().toString();
        String title = name + " " + (revisions.isEmpty() ? "working copy" : String.join(" → ", revisions));
        ViewerData.Data data = ViewerData.data(title, beforeCommit, before, commit, after, options.viewOptions());
        Files.writeString(options.out(), ViewerPage.html(ViewerData.json(data)));
        out.println(options.out().toAbsolutePath());
    }

    private static Model view(Options options, Model model) {
        return options.view().project(model, options.viewOptions());
    }
}
