package com.yanny.archflow.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelTest {
    @Test
    void dumpsNodesAsTreeWithFlowsAndEdgesInStableOrder() {
        Node walk = new Node("a.Walker#walk(Context)", NodeKind.METHOD, "walk", "a.Walker", new Location("a/Walker.java", 5, 20));
        Node handle = new Node("a.Handler#handle(Context)", NodeKind.METHOD, "handle", "a.Handler", new Location("a/Handler.java", 4, 4));
        Node walker = new Node("a.Walker", NodeKind.TYPE, "Walker", "a", new Location("a/Walker.java", 3, 40));
        Node handler = new Node("a.Handler", NodeKind.TYPE, "Handler", "a", new Location("a/Handler.java", 3, 5));
        Node pkg = new Node("a", NodeKind.PACKAGE, "a", null, null);
        List<Step> flow = List.of(
                new Step.Loop(6, "for (Block block : blocks)", List.of(
                        new Step.Branch(7, null, List.of(
                                new Step.Arm("block.isExit()", List.of(new Step.Return(8))),
                                new Step.Arm(null, List.of(new Step.Call(10, "a.Handler#handle(Context)", "handler.handle(context)", false))))))),
                new Step.Branch(12, "kind", List.of(
                        new Step.Arm("A, B", List.of(new Step.Call(13, null, "queue.poll()", false))),
                        new Step.Arm(null, List.of(new Step.Throw(15, "new IllegalStateException()"))))));
        Edge call = new Edge(EdgeKind.CALL, walk.id(), handle.id());
        Edge dependency = new Edge(EdgeKind.DEPENDENCY, walker.id(), handler.id());

        Model model = new Model(List.of(walk, handle, walker, handler, pkg), List.of(dependency, call, dependency), Map.of(walk.id(), flow),
                List.of());

        assertEquals("""
                package a
                  type Handler a/Handler.java:3-5
                    method handle(Context) 4-4
                  type Walker a/Walker.java:3-40
                    method walk(Context) 5-20
                      6 loop for (Block block : blocks)
                        7 if
                          block.isExit()
                            8 return
                          else
                            10 call a.Handler#handle(Context)
                      12 switch kind
                        case A, B
                          13 call ? queue.poll()
                        default
                          15 throw new IllegalStateException()
                call a.Walker#walk(Context) -> a.Handler#handle(Context)
                dependency a.Walker -> a.Handler""", String.join("\n", ModelDump.dump(model)));
    }
}
