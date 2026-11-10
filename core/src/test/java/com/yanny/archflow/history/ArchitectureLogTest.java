package com.yanny.archflow.history;

import com.yanny.archflow.diff.DiffText;
import com.yanny.archflow.git.GitRepo;
import com.yanny.archflow.view.View;
import com.yanny.archflow.view.ViewOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArchitectureLogTest {
    @TempDir
    Path dir;

    @Test
    void logNamesOnlyCommitsThatChangeTheView() throws Exception {
        try (TestRepo repo = new TestRepo(dir)) {
            String first = repo.write("a/A.java", "package a;\nclass A {\n    void run() {\n    }\n}\n").commit("Added A");
            repo.write("a/A.java", "package a;\n// comment\nclass A {\n    void run() {\n    }\n}\n").commit("Commented A");
            String last = repo.write("a/B.java", "package a;\nclass B {\n    static void go() {\n    }\n}\n")
                    .write("a/A.java", "package a;\nclass A {\n    void run() {\n        B.go();\n    }\n}\n")
                    .commit("A runs B");

            try (GitRepo git = new GitRepo(repo.dir())) {
                ArchitectureLog log = new ArchitectureLog(git, new ModelSource(git, List.of(), null), View.ALL_FUNCTIONS, ViewOptions.DEFAULT);
                List<String> lines = new ArrayList<>();
                for (LogEntry entry : log.log(first, last)) {
                    lines.add(entry.message());
                    DiffText.lines(entry.diff(), false).forEach(line -> lines.add("  " + line));
                }

                assertEquals("""
                        A runs B
                          ~ method a.A#run()
                          + type a.B
                          + method a.B#go()
                          + call a.A#run() -> a.B#go()
                          + dependency a.A -> a.B""", String.join("\n", lines));
            }
        }
    }
}
