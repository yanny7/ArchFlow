package com.yanny.archflow.present;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.yanny.archflow.diff.Change;
import com.yanny.archflow.diff.Diff;
import com.yanny.archflow.diff.DiffText;
import com.yanny.archflow.diff.EdgeChange;
import com.yanny.archflow.diff.ModelDiff;
import com.yanny.archflow.diff.NodeChange;
import com.yanny.archflow.model.Edge;
import com.yanny.archflow.model.FlowChart;
import com.yanny.archflow.model.FlowText;
import com.yanny.archflow.model.Model;
import com.yanny.archflow.model.Node;
import com.yanny.archflow.model.Step;
import com.yanny.archflow.view.View;
import com.yanny.archflow.view.ViewOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ViewerData {
    public record Data(String title, String commit, String beforeCommit, List<Graph> views) {
    }

    public record Graph(String view, List<GraphNode> nodes, List<GraphEdge> edges, List<ChangeEntry> changes) {
    }

    public record GraphNode(String id, String kind, String name, String parent, String file, int line, int lines,
                            String change, String previousId, List<FlowText.Line> flow, FlowChart chart) {
    }

    public record GraphEdge(String kind, String from, String to, String change) {
    }

    public record ChangeEntry(String target, String change, String text) {
    }

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private ViewerData() {
    }

    public static Data data(String title, String beforeCommit, Model before, String commit, Model after, ViewOptions options) {
        List<Graph> graphs = new ArrayList<>();
        for (View view : View.values()) {
            Model projectedAfter = view.project(after, options);
            Model projectedBefore = before == null ? null : view.project(before, options);
            graphs.add(graph(view, projectedBefore, projectedAfter));
        }
        return new Data(title, commit, beforeCommit, graphs);
    }

    public static String json(Data data) {
        return GSON.toJson(data);
    }

    static Graph graph(View view, Model before, Model after) {
        Diff diff = before == null ? new Diff(List.of(), List.of()) : ModelDiff.diff(before, after);
        Map<String, NodeChange> nodeChanges = new HashMap<>();
        diff.nodes().forEach(change -> nodeChanges.put(change.node().id(), change));
        Map<Edge, Change> edgeChanges = new HashMap<>();
        diff.edges().forEach(change -> edgeChanges.put(change.edge(), change.change()));

        List<GraphNode> nodes = new ArrayList<>();
        after.nodes().forEach(node -> nodes.add(node(node, after, nodeChanges.get(node.id()))));
        diff.nodes().stream()
                .filter(change -> change.change() == Change.REMOVED)
                .forEach(change -> nodes.add(node(change.node(), before, change)));
        List<GraphEdge> edges = new ArrayList<>();
        after.edges().forEach(edge -> edges.add(edge(edge, edgeChanges.get(edge))));
        diff.edges().stream()
                .filter(change -> change.change() == Change.REMOVED)
                .map(EdgeChange::edge)
                .forEach(edge -> edges.add(edge(edge, Change.REMOVED)));
        List<ChangeEntry> changes = DiffText.entries(diff, false).stream()
                .map(entry -> new ChangeEntry(entry.target(), name(entry.change()), entry.line()))
                .toList();
        return new Graph(name(view), nodes, edges, changes);
    }

    private static GraphNode node(Node node, Model model, NodeChange change) {
        var location = node.location();
        return new GraphNode(node.id(), name(node.kind()), node.name(), node.parent(),
                location == null ? null : location.file(), location == null ? 0 : location.line(), node.lines(),
                change == null ? null : name(change.change()), change == null ? null : change.previousId(),
                FlowText.lines(model.flow(node.id())), chart(model.flow(node.id())));
    }

    private static FlowChart chart(List<Step> flow) {
        return flow.isEmpty() ? null : FlowChart.of(flow);
    }

    private static GraphEdge edge(Edge edge, Change change) {
        return new GraphEdge(name(edge.kind()), edge.from(), edge.to(), change == null ? null : name(change));
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
