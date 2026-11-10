package com.yanny.archflow.link;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FunctionalTest {
    @Test
    void lambdasAndMethodReferencesOfSourceInterfacesImplementTheirMethod() {
        assertEquals("""
                package app
                  type Handler app/Handler.java:3-9
                    method accepts(int) 6-8
                      7 return
                    method execute(int) 4-4
                  type Instructions app/Instructions.java:3-7
                    method execute(int) 4-6
                      5 return
                  type Reads app/Reads.java:3-7
                    method execute(int) 4-6
                      5 return
                  type Walker app/Walker.java:5-22
                    method <clinit>() 6-8
                      7 call app.Instructions#execute(int)
                      8 call app.Reads#execute(int)
                      6 call external List.of()
                      method lambda1 7-7
                        7 call app.Instructions#execute(int)
                    method <instance-init>() 9-9
                      9 call app.Walker#handle(int)
                    method fallback() 11-13
                      12 call app.Reads#execute(int)
                      12 return
                      method lambda1 12-12
                        12 call app.Reads#execute(int)
                    method handle(int) 15-21
                      16 loop for (Handler handler : HANDLERS)
                        17 call app.Handler#execute(int)
                        17 if
                          handler.execute(insn)
                            18 return
                call app.Walker#<clinit>() -> app.Instructions#execute(int)
                call app.Walker#<clinit>() -> app.Reads#execute(int)
                call app.Walker#<clinit>()$lambda1 -> app.Instructions#execute(int)
                call app.Walker#<instance-init>() -> app.Walker#handle(int)
                call app.Walker#fallback() -> app.Reads#execute(int)
                call app.Walker#fallback()$lambda1 -> app.Reads#execute(int)
                call app.Walker#handle(int) -> app.Handler#execute(int)
                dependency app.Walker -> app.Handler
                dependency app.Walker -> app.Instructions
                dependency app.Walker -> app.Reads
                override app.Reads#execute(int) -> app.Handler#execute(int)
                override app.Walker#<clinit>()$lambda1 -> app.Handler#execute(int)
                override app.Walker#fallback()$lambda1 -> app.Handler#execute(int)""", Fixtures.dump("functional"));
    }
}
