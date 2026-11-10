package com.yanny.archflow.git;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class GitRepo implements AutoCloseable {
    public record CommitInfo(String id, String message) {
    }

    private final Repository repository;

    public GitRepo(Path dir) throws IOException {
        repository = new FileRepositoryBuilder().findGitDir(dir.toFile()).setMustExist(true).build();
    }

    public String resolve(String revision) throws IOException {
        ObjectId id = repository.resolve(revision + "^{commit}");
        if (id == null) {
            throw new IOException("Unknown commit: " + revision);
        }
        return id.name();
    }

    public String parent(String commit) throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit revCommit = walk.parseCommit(ObjectId.fromString(commit));
            return revCommit.getParentCount() == 0 ? null : revCommit.getParent(0).name();
        }
    }

    public String message(String commit) throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            return walk.parseCommit(ObjectId.fromString(commit)).getShortMessage();
        }
    }

    public Path workTree() {
        return repository.getWorkTree().toPath();
    }

    public List<CommitInfo> history(String start, int limit) throws IOException {
        List<CommitInfo> commits = new ArrayList<>();
        try (RevWalk walk = new RevWalk(repository)) {
            walk.setFirstParent(true);
            walk.markStart(walk.parseCommit(ObjectId.fromString(resolve(start))));
            for (RevCommit commit : walk) {
                if (commits.size() == limit) {
                    break;
                }
                commits.add(new CommitInfo(commit.name(), commit.getShortMessage()));
            }
        }
        return commits;
    }

    public String fileAt(String commit, String path) throws IOException {
        try (RevWalk walk = new RevWalk(repository);
             TreeWalk tree = TreeWalk.forPath(repository, path, walk.parseCommit(ObjectId.fromString(commit)).getTree())) {
            if (tree == null) {
                throw new IOException(path + " is not in " + commit);
            }
            return read(tree.getObjectId(0));
        }
    }

    public List<String> range(String from, String to) throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            walk.setFirstParent(true);
            walk.sort(RevSort.TOPO);
            walk.sort(RevSort.REVERSE, true);
            walk.markStart(walk.parseCommit(ObjectId.fromString(to)));
            walk.markUninteresting(walk.parseCommit(ObjectId.fromString(from)));
            List<String> commits = new ArrayList<>();
            walk.forEach(commit -> commits.add(commit.name()));
            return commits;
        }
    }

    public List<SourceFile> commitSources(String commit, List<String> paths) throws IOException {
        List<SourceFile> files = new ArrayList<>();
        try (RevWalk walk = new RevWalk(repository); TreeWalk tree = new TreeWalk(repository)) {
            tree.addTree(walk.parseCommit(ObjectId.fromString(commit)).getTree());
            tree.setRecursive(true);
            tree.setFilter(filter(paths));
            while (tree.next()) {
                ObjectId blob = tree.getObjectId(0);
                files.add(new SourceFile(tree.getPathString(), blob.name(), () -> read(blob)));
            }
        }
        return files;
    }

    public List<SourceFile> workingCopySources(List<String> paths) throws IOException {
        Path root = repository.getWorkTree().toPath();
        List<SourceFile> files = new ArrayList<>();
        try (TreeWalk tree = new TreeWalk(repository)) {
            tree.addTree(new FileTreeIterator(repository));
            tree.setRecursive(true);
            tree.setFilter(filter(paths));
            while (tree.next()) {
                WorkingTreeIterator entry = tree.getTree(0, WorkingTreeIterator.class);
                if (!entry.isEntryIgnored()) {
                    Path file = root.resolve(tree.getPathString());
                    files.add(new SourceFile(tree.getPathString(), entry.getEntryObjectId().name(), () -> readFile(file)));
                }
            }
        }
        return files;
    }

    private static TreeFilter filter(List<String> paths) {
        TreeFilter java = PathSuffixFilter.create(".java");
        return paths.isEmpty() ? java : AndTreeFilter.create(PathFilterGroup.createFromStrings(paths), java);
    }

    private String read(ObjectId blob) {
        try (ObjectReader reader = repository.newObjectReader()) {
            return new String(reader.open(blob, Constants.OBJ_BLOB).getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String readFile(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        repository.close();
    }
}
