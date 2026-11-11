package com.yanny.archflow.history;

import com.yanny.archflow.git.GitRepo;
import com.yanny.archflow.model.Model;
import com.yanny.archflow.model.ModelDump;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelSourceTest {
    @TempDir
    Path dir;

    @Test
    void modelsOfCommitsAndWorkingCopyComeFromTheirOwnSources() throws Exception {
        try (TestRepo repo = new TestRepo(dir.resolve("repo"))) {
            String first = repo.write("src/a/A.java", "package a;\nclass A {\n}\n")
                    .write("other/b/B.java", "package b;\nclass B {\n}\n")
                    .commit("first");
            String second = repo.write("src/a/A.java", "package a;\nclass A {\n    void run() {\n    }\n}\n").commit("second");
            repo.write("src/a/C.java", "package a;\nclass C {\n}\n");

            try (GitRepo git = new GitRepo(repo.dir())) {
                ModelSource source = new ModelSource(git, List.of("src"), null);

                assertEquals("""
                        package a
                          type A src/a/A.java:2-3""", dump(source.commit(first)));
                assertEquals("""
                        package a
                          type A src/a/A.java:2-5
                            method run() 3-4""", dump(source.commit(second)));
                assertEquals("""
                        package a
                          type A src/a/A.java:2-5
                            method run() 3-4
                          type C src/a/C.java:2-3""", dump(source.workingCopy()));
                assertEquals(List.of(second), git.range(first, second));
                assertEquals(first, git.parent(second));
                assertEquals(List.of(new GitRepo.CommitInfo(second, "second"), new GitRepo.CommitInfo(first, "first")),
                        git.history("main", "", 0, 10));
                assertEquals("package a;\nclass A {\n}\n", git.fileAt(first, "src/a/A.java"));
            }
        }
    }

    @Test
    void modelOfCommitIsCachedOnDiskAndReadBack() throws Exception {
        try (TestRepo repo = new TestRepo(dir.resolve("repo"))) {
            String commit = repo.write("a/A.java", "package a;\nclass A {\n}\n").commit("first");
            Path cache = dir.resolve("cache");

            try (GitRepo git = new GitRepo(repo.dir())) {
                Model built = new ModelSource(git, List.of(), cache).commit(commit);
                try (var files = Files.list(cache)) {
                    assertEquals(1, files.count());
                }
                assertEquals(built, new ModelSource(git, List.of(), cache).commit(commit));
            }
        }
    }

    private static String dump(Model model) {
        return String.join("\n", ModelDump.dump(model));
    }
}
