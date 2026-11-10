package com.yanny.archflow.viewer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ViewerPage {
    private static final String BASE = "/com/yanny/archflow/viewer/";
    private static final List<String> LIBRARIES = List.of("lib/cytoscape.min.js", "lib/elk.bundled.js", "lib/cytoscape-elk.js");
    private static final Pattern PLACEHOLDER = Pattern.compile("<!--([A-Z]+)-->");

    private ViewerPage() {
    }

    public static String html(String dataJson) {
        String libraries = LIBRARIES.stream().map(path -> script(resource(path))).collect(Collectors.joining("\n"));
        Map<String, String> parts = Map.of(
                "STYLE", resource("viewer.css"),
                "LIBS", libraries,
                "DATA", dataJson == null ? "" : script("window.ARCHFLOW_DATA = " + dataJson + ";"),
                "SCRIPT", script(resource("viewer.js")));
        Matcher matcher = PLACEHOLDER.matcher(resource("index.html"));
        StringBuilder html = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(html, Matcher.quoteReplacement(parts.get(matcher.group(1))));
        }
        matcher.appendTail(html);
        return html.toString();
    }

    private static String script(String code) {
        return "<script>" + code.replace("</script", "<\\/script") + "</script>";
    }

    private static String resource(String path) {
        try (InputStream in = ViewerPage.class.getResourceAsStream(BASE + path)) {
            if (in == null) {
                throw new IllegalStateException("Missing viewer resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
