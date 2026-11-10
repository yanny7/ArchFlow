package com.yanny.archflow.history;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class TestRepo implements AutoCloseable {
    private final Git git;
    private final Path dir;

    TestRepo(Path dir) throws GitAPIException {
        this.dir = dir;
        this.git = Git.init().setDirectory(dir.toFile()).setInitialBranch("main").call();
    }

    TestRepo write(String path, String content) throws IOException {
        Path file = dir.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return this;
    }

    String commit(String message) throws GitAPIException {
        git.add().addFilepattern(".").call();
        git.add().addFilepattern(".").setUpdate(true).call();
        return git.commit().setMessage(message).setAuthor("test", "test@example.com").setCommitter("test", "test@example.com")
                .setSign(false).call().name();
    }

    Path dir() {
        return dir;
    }

    @Override
    public void close() {
        git.close();
    }
}
