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
                      14 call ? new Sink()
                      14 call a.Flow#<init>(Sink)
                    method <init>(Sink) 8-11
                      9 call ? sink.name()
                      9 call ? super()
                    method branches(int,Object) 17-34
                      18 if
                        kind > 2
                          19 call ? sink.big()
                        kind < 0
                          21 return
                        else
                          22 call ? sink.accepts()
                          22 if
                            sink.accepts(kind)
                              23 call ? sink.small()
                            else
                              25 call ? sink.name()
                              25 throw new IllegalStateException()
                      27 switch kind
                        case 1, 2
                          28 call ? sink.few()
                        case 3
                        default
                          31 call ? sink.many()
                      33 if
                        value instanceof List<?> list
                          33 call ? list.size()
                        else
                          33 call ? sink.size()
                    method expressions(List) 51-67
                      52 call ? sink.name()
                      52 call ? sink.name().trim()
                      52 call a.Flow#helper()
                      52 call ? sink.write()
                      54 call ? s.accepts()
                      54 if
                        s.accepts(0)
                      57 call ? s.flush()
                      53 call ? sinks.forEach()
                      59 call ? sinks.stream()
                      59 call ? Sink::name
                      59 call ? sinks.stream().map()
                      59 call ? sink::write
                      59 call ? sinks.stream().map().forEach()
                      61 call ? sink.open()
                      62 if
                        catch (IllegalStateException e)
                          63 call ? e.printStackTrace()
                      65 call ? sink.close()
                    method helper() 69-71
                      70 return
                    method loops(List) 36-49
                      37 loop for (int i = 0; i < sinks.size(); i++)
                        37 call ? sinks.size()
                        38 call ? sinks.get()
                        38 call ? sinks.get().flush()
                      40 loop for (Sink each : sinks)
                        41 call ? each.flush()
                      43 loop while (sink.accepts(0))
                        43 call ? sink.accepts()
                        44 call ? sink.drain()
                      46 loop do while (sink.accepts(1))
                        47 call ? sink.drain()
                        48 call ? sink.accepts()
                call a.Flow#<init>() -> a.Flow#<init>(Sink)
                call a.Flow#expressions(List) -> a.Flow#helper()""", Fixtures.dump("flow"));
    }
}
