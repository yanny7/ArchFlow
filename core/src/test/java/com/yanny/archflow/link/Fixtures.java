package com.yanny.archflow.link;

import com.yanny.archflow.extract.JavaExtractor;
import com.yanny.archflow.facts.FileFacts;
import com.yanny.archflow.model.Model;
import com.yanny.archflow.model.ModelDump;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public final class Fixtures {
    private Fixtures() {
    }

    public static String dump(String fixture) {
        return String.join("\n", ModelDump.dump(model(fixture)));
    }

    public static Model model(String fixture) {
        return Linker.link(facts(fixture));
    }

    public static List<FileFacts> facts(String fixture) {
        Path root = root(fixture);
        JavaExtractor extractor = new JavaExtractor();
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .map(p -> extractor.extract(root.relativize(p).toString(), read(p)))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path root(String fixture) {
        try {
            return Path.of(Fixtures.class.getResource("/fixtures/" + fixture).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
