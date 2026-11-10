package com.yanny.archflow.cli;

import com.yanny.archflow.view.View;
import com.yanny.archflow.view.ViewOptions;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

record Options(String command, List<String> revisions, Path repo, List<String> paths, View view, ViewOptions viewOptions,
               boolean json, Path cache, Path out) {
    static final String USAGE = """
            usage: archflow <command> [options]
              model [REV]          the view of a commit, or of the working copy without REV
              diff FROM [TO]       changes of the view from FROM to TO, or to the working copy without TO
              log FROM..TO         the commits of a range that change the view, with their changes
              html [REV | FROM TO] a page with every view of REV, or of the working copy, or of the diff FROM TO
            options:
              --repo DIR           the git repository (default: the current directory)
              --path PREFIX        a path prefix of the sources to read, repeatable (default: every .java file)
              --view NAME          outline, all-functions or dependencies (default: outline)
              --min-lines N        the smallest method the outline shows (default: 10)
              --external           show calls into code outside the sources
              --json               write the model as JSON (model only)
              --cache DIR          the cache of models (default: ~/.cache/archflow)
              --no-cache           build every model anew
              --out FILE           the page html writes (default: archflow.html)""";

    static Options parse(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("missing command");
        }
        List<String> revisions = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        Path repo = Path.of(".");
        View view = View.OUTLINE;
        ViewOptions viewOptions = ViewOptions.DEFAULT;
        boolean json = false;
        Path cache = defaultCache();
        Path out = Path.of("archflow.html");
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--repo" -> repo = Path.of(value(args, ++i, arg));
                case "--path" -> paths.add(value(args, ++i, arg));
                case "--view" -> view = View.valueOf(value(args, ++i, arg).toUpperCase(Locale.ROOT).replace('-', '_'));
                case "--min-lines" -> viewOptions = viewOptions.withMinLines(Integer.parseInt(value(args, ++i, arg)));
                case "--external" -> viewOptions = viewOptions.withShowExternal(true);
                case "--json" -> json = true;
                case "--cache" -> cache = Path.of(value(args, ++i, arg));
                case "--no-cache" -> cache = null;
                case "--out" -> out = Path.of(value(args, ++i, arg));
                default -> {
                    if (arg.startsWith("--")) {
                        throw new IllegalArgumentException("unknown option " + arg);
                    }
                    revisions.add(arg);
                }
            }
        }
        return new Options(args[0], revisions, repo, paths, view, viewOptions, json, cache, out);
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " needs a value");
        }
        return args[index];
    }

    private static Path defaultCache() {
        String xdg = System.getenv("XDG_CACHE_HOME");
        Path base = xdg == null || xdg.isEmpty() ? Path.of(System.getProperty("user.home"), ".cache") : Path.of(xdg);
        return base.resolve("archflow");
    }
}
