package com.yanny.archflow.present;

import com.yanny.archflow.link.Fixtures;
import com.yanny.archflow.view.View;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ViewerDataTest {
    @Test
    void graphOfDiffHoldsRemovedNodesAndEdgesWithTheirChangesAndPlaces() {
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
                  at a/Walker.java:4 call new Context()
                call a.Walker#step(long) -> b.Util#log(int)
                  at a/Walker.java:20 call b.Util.log()
                call a.Walker#walk(int) -> a.Walker#step(long)
                  at a/Walker.java:8 call step()
                dependency a.Walker -> a.Context [added]
                  at a/Walker.java:4 call new Context()
                  at a/Walker.java:4 field context
                dependency a.Walker -> b.Util
                  at a/Walker.java:20 call b.Util.log()
                change a.Context: + type a.Context
                change a.Old: - type a.Old
                change a.Walker#<instance-init>(): + method a.Walker#<instance-init>()
                change a.Walker#run(long): ~ method a.Walker#run(long) (was a.Walker#run(int))
                change a.Walker#run(long,int): ~ method a.Walker#run(long,int) (was a.Walker#run(int,int))
                change a.Walker#step(long): ~ method a.Walker#step(long) (was a.Walker#step(int))
                change a.Walker#walk(int): ~ method a.Walker#walk(int)
                change a.Walker to a.Context: + dependency a.Walker -> a.Context""", String.join("\n", lines(graph)));
    }

    @Test
    void fileIsATestWhenItsSourceSetNameHoldsTest() {
        assertEquals(List.of(true, true, true, false, false, false), List.of(
                ViewerData.isTest("ali/common/src/test/java/a/A.java"),
                ViewerData.isTest("ali/fabric/src/gametest/java/a/A.java"),
                ViewerData.isTest("src/testFixtures/java/a/A.java"),
                ViewerData.isTest("src/main/java/test/A.java"),
                ViewerData.isTest("a/test/A.java"),
                ViewerData.isTest("src/Test.java")));
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
            edge.sites().forEach(site -> lines.add("  at " + site.file() + ":" + site.line() + " " + site.kind() + " " + site.text()));
        }
        graph.changes().forEach(change -> lines.add("change " + change.target() + (change.to() == null ? "" : " to " + change.to())
                + ": " + change.text()));
        return lines;
    }
}
