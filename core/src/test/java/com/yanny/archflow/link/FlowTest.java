package com.yanny.archflow.link;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlowTest {
    @Test
    void flowKeepsCallsBranchesLoopsReturnsAndThrowsInSourceOrder() {
        assertEquals("""
                package a
                  type Flow a/Flow.java:5-72
                    method <init>() 13-15
                      14 call external new Sink()
                      14 call a.Flow#<init>(Sink)
                    method <init>(Sink) 8-11
                      9 call external sink.name()
                      9 call external super()
                    method branches(int,Object) 17-34
                      18 if
                        kind > 2
                          19 call external sink.big()
                        kind < 0
                          21 return
                        else
                          22 call external sink.accepts()
                          22 if
                            sink.accepts(kind)
                              23 call external sink.small()
                            else
                              25 call external sink.name()
                              25 throw new IllegalStateException()
                      27 switch kind
                        case 1, 2
                          28 call external sink.few()
                        case 3
                        default
                          31 call external sink.many()
                      33 if
                        value instanceof List<?> list
                          33 call external list.size()
                        else
                          33 call external sink.size()
                    method expressions(List) 51-67
                      52 call external sink.name()
                      52 call external sink.name().trim()
                      52 call a.Flow#helper()
                      52 call external sink.write()
                      54 call ? s.accepts()
                      54 if
                        s.accepts(0)
                      57 call ? s.flush()
                      53 call external sinks.forEach()
                      59 call external sinks.stream()
                      59 call external Sink::name
                      59 call external sinks.stream().map()
                      59 call external sink::write
                      59 call external sinks.stream().map().forEach()
                      61 call external sink.open()
                      62 if
                        catch (IllegalStateException e)
                          63 call external e.printStackTrace()
                      65 call external sink.close()
                    method helper() 69-71
                      70 return
                    method loops(List) 36-49
                      37 loop for (int i = 0; i < sinks.size(); i++)
                        37 call external sinks.size()
                        38 call external sinks.get()
                        38 call external sinks.get().flush()
                      40 loop for (Sink each : sinks)
                        41 call external each.flush()
                      43 loop while (sink.accepts(0))
                        43 call external sink.accepts()
                        44 call external sink.drain()
                      46 loop do while (sink.accepts(1))
                        47 call external sink.drain()
                        48 call external sink.accepts()
                call a.Flow#<init>() -> a.Flow#<init>(Sink)
                call a.Flow#expressions(List) -> a.Flow#helper()""", Fixtures.dump("flow"));
    }
}
