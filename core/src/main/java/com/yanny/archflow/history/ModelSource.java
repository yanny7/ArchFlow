package com.yanny.archflow.history;

import com.yanny.archflow.extract.JavaExtractor;
import com.yanny.archflow.facts.FileFacts;
import com.yanny.archflow.git.GitRepo;
import com.yanny.archflow.git.SourceFile;
import com.yanny.archflow.json.ModelJson;
import com.yanny.archflow.link.Linker;
import com.yanny.archflow.model.Model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

public final class ModelSource {
    // Bump on any change to extraction, linking or the model: cached models are never invalidated otherwise.
    public static final int FORMAT = 1;

    private final GitRepo git;
    private final List<String> paths;
    private final Path cacheDir;
    private final JavaExtractor extractor = new JavaExtractor();
    private final Map<String, FileFacts> facts = new HashMap<>();
    private final Map<String, Model> models = new HashMap<>();

    public ModelSource(GitRepo git, List<String> paths, Path cacheDir) {
        this.git = git;
        this.paths = List.copyOf(paths);
        this.cacheDir = cacheDir;
    }

    public Model commit(String commit) throws IOException {
        Model model = models.get(commit);
        if (model != null) {
            return model;
        }
        Path cached = cacheDir == null ? null : cacheDir.resolve(commit + "-" + cacheKey() + ".json");
        if (cached != null && Files.exists(cached)) {
            model = ModelJson.read(Files.readString(cached));
        } else {
            model = link(git.commitSources(commit, paths));
            if (cached != null) {
                write(cached, ModelJson.write(model));
            }
        }
        models.put(commit, model);
        return model;
    }

    public Model workingCopy() throws IOException {
        return link(git.workingCopySources(paths));
    }

    private Model link(List<SourceFile> files) {
        return Linker.link(files.stream().map(this::facts).toList());
    }

    private FileFacts facts(SourceFile file) {
        return facts.computeIfAbsent(file.path() + "@" + file.blobId(), key -> extractor.extract(file.path(), file.content().get()));
    }

    private String cacheKey() {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1")
                    .digest((FORMAT + "\n" + String.join("\n", paths)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        Files.writeString(temp, content);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
