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
    private static final String ELK = "lib/elk.bundled.js";
    private static final List<String> LIBRARIES = List.of("lib/cytoscape.min.js", ELK, "lib/cytoscape-elk.js");
    private static final Pattern PLACEHOLDER = Pattern.compile("<!--([A-Z]+)-->");

    private ViewerPage() {
    }

    public static String html(String dataJson) {
        String libraries = LIBRARIES.stream()
                .map(path -> path.equals(ELK) ? script("elk-source", resource(path)) : script(null, resource(path)))
                .collect(Collectors.joining("\n"));
        Map<String, String> parts = Map.of(
                "STYLE", resource("viewer.css"),
                "LIBS", libraries,
                "DATA", dataJson == null ? "" : script(null, "window.ARCHFLOW_DATA = " + parse(dataJson) + ";"),
                "SCRIPT", script(null, resource("viewer.js")));
        Matcher matcher = PLACEHOLDER.matcher(resource("index.html"));
        StringBuilder html = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(html, Matcher.quoteReplacement(parts.get(matcher.group(1))));
        }
        matcher.appendTail(html);
        return html.toString();
    }

    public static String parse(String json) {
        StringBuilder literal = new StringBuilder(json.length() + json.length() / 16 + 16).append("JSON.parse('");
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            switch (c) {
                case '\\' -> literal.append("\\\\");
                case '\'' -> literal.append("\\'");
                case '\n' -> literal.append("\\n");
                case '\r' -> literal.append("\\r");
                case '\u2028' -> literal.append("\\u2028");
                case '\u2029' -> literal.append("\\u2029");
                default -> literal.append(c);
            }
        }
        return literal.append("')").toString();
    }

    private static String script(String id, String code) {
        String open = id == null ? "<script>" : "<script id=\"" + id + "\">";
        return open + code.replace("</script", "<\\/script") + "</script>";
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
