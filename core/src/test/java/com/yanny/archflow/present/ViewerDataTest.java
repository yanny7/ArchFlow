package com.yanny.archflow.present;

import com.yanny.archflow.link.Fixtures;
import com.yanny.archflow.view.View;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ViewerDataTest {
    @Test
    void graphOfDiffHoldsRemovedNodesAndEdgesWithTheirChanges() {
        ViewerData.Graph graph = ViewerData.graph(View.ALL_FUNCTIONS, Fixtures.model("diff-before"), Fixtures.model("diff-after"));

        assertEquals("""
                package a in null [inside]
                type a.Context in a [added]
                type a.Walker in a [inside]
                method a.Walker#<instance-init>() in a.Walker [added]
                  call a.Context
                method a.Walker#run(long) in a.Walker [changed]
                method a.Walker#run(long,int) in a.Walker [changed]
                method a.Walker#step(long) in a.Walker [changed]
                  call b.Util#log(int)
                method a.Walker#walk(int) in a.Walker [changed]
                  if
                    depth > 0
                      call a.Walker#step(long)
                package b in null
                type b.Util in b
                method b.Util#log(int) in b.Util
                type a.Old in a [removed]
                call a.Walker#<instance-init>() -> a.Context [added]
                call a.Walker#step(long) -> b.Util#log(int)
                call a.Walker#walk(int) -> a.Walker#step(long)
                dependency a.Walker -> a.Context [added]
                dependency a.Walker -> b.Util
                change a.Context: + type a.Context
                change a.Old: - type a.Old
                change a.Walker#<instance-init>(): + method a.Walker#<instance-init>()
                change a.Walker#run(long): ~ method a.Walker#run(long) (was a.Walker#run(int))
                change a.Walker#run(long,int): ~ method a.Walker#run(long,int) (was a.Walker#run(int,int))
                change a.Walker#step(long): ~ method a.Walker#step(long) (was a.Walker#step(int))
                change a.Walker#walk(int): ~ method a.Walker#walk(int)
                change a.Walker to a.Context: + dependency a.Walker -> a.Context""", String.join("\n", lines(graph)));
    }

    private static List<String> lines(ViewerData.Graph graph) {
        List<String> lines = new ArrayList<>();
        for (ViewerData.GraphNode node : graph.nodes()) {
            String change = node.change() == null ? "" : " [" + node.change() + "]";
            lines.add(node.kind() + " " + node.id() + " in " + node.parent() + change);
            node.flow().forEach(line -> lines.add("  " + "  ".repeat(line.depth()) + line.text()));
        }
        for (ViewerData.GraphEdge edge : graph.edges()) {
            lines.add(edge.kind() + " " + edge.from() + " -> " + edge.to() + (edge.change() == null ? "" : " [" + edge.change() + "]"));
        }
        graph.changes().forEach(change -> lines.add("change " + change.target() + (change.to() == null ? "" : " to " + change.to())
                + ": " + change.text()));
        return lines;
    }
}
