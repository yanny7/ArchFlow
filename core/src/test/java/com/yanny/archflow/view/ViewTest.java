package com.yanny.archflow.view;

import com.yanny.archflow.link.Fixtures;
import com.yanny.archflow.model.ModelDump;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ViewTest {
    private static final ViewOptions OPTIONS = ViewOptions.DEFAULT.withMinLines(8);

    @Test
    void outlineKeepsBigMethodsAndCarriesCallsThroughHiddenMethodsAndInterfaces() {
        assertEquals("""
                package app
                  type Calls app/Calls.java:3-9
                  type Handler app/Handler.java:3-5
                  type Reads app/Reads.java:3-13
                    method execute(State) 4-12
                      6 if
                        state.pending()
                          8 call app.State#reset()
                  type State app/State.java:3-30
                    method reset() 19-26
                  type Walker app/Walker.java:5-29
                    method walk(State) 12-20
                      13 loop while (state.pending())
                        14 call app.Reads#execute(State)
                      19 call app.State#reset()
                call app.Reads#execute(State) -> app.State#reset()
                call app.Walker#walk(State) -> app.Reads#execute(State)
                call app.Walker#walk(State) -> app.State#reset()
                dependency app.Calls -> app.Handler
                dependency app.Calls -> app.State
                dependency app.Handler -> app.State
                dependency app.Reads -> app.Handler
                dependency app.Reads -> app.State
                dependency app.Walker -> app.Handler
                dependency app.Walker -> app.State""", dump(View.OUTLINE, OPTIONS));
    }

    @Test
    void allFunctionsHideExternalCallsAndBranchesWithoutCalls() {
        assertEquals("""
                package app
                  type Calls app/Calls.java:3-9
                    method execute(State) 4-8
                      6 call app.State#push(int)
                  type Handler app/Handler.java:3-5
                    method execute(State) 4-4
                  type Reads app/Reads.java:3-13
                    method execute(State) 4-12
                      6 call app.State#pending()
                      6 if
                        state.pending()
                          7 call app.State#push(int)
                          8 call app.State#reset()
                  type State app/State.java:3-30
                    method <instance-init>() 4-4
                    method failed() 10-12
                    method log(int) 28-29
                    method pending() 6-8
                    method push(int) 14-17
                      16 call app.State#log(int)
                    method reset() 19-26
                      21 call app.State#log(int)
                      22 call app.State#log(int)
                      23 call app.State#log(int)
                      24 call app.State#log(int)
                      25 call app.State#log(int)
                  type Walker app/Walker.java:5-29
                    method <init>(List) 8-10
                    method step(State) 22-28
                      23 loop for (Handler handler : handlers)
                        24 call app.Handler#execute(State)
                    method walk(State) 12-20
                      13 loop while (state.pending())
                        13 call app.State#pending()
                        14 call app.Walker#step(State)
                      16 call app.State#failed()
                      19 call app.State#reset()
                call app.Calls#execute(State) -> app.State#push(int)
                call app.Reads#execute(State) -> app.State#pending()
                call app.Reads#execute(State) -> app.State#push(int)
                call app.Reads#execute(State) -> app.State#reset()
                call app.State#push(int) -> app.State#log(int)
                call app.State#reset() -> app.State#log(int)
                call app.Walker#step(State) -> app.Handler#execute(State)
                call app.Walker#walk(State) -> app.State#failed()
                call app.Walker#walk(State) -> app.State#pending()
                call app.Walker#walk(State) -> app.State#reset()
                call app.Walker#walk(State) -> app.Walker#step(State)
                dependency app.Calls -> app.Handler
                dependency app.Calls -> app.State
                dependency app.Handler -> app.State
                dependency app.Reads -> app.Handler
                dependency app.Reads -> app.State
                dependency app.Walker -> app.Handler
                dependency app.Walker -> app.State
                override app.Calls#execute(State) -> app.Handler#execute(State)
                override app.Reads#execute(State) -> app.Handler#execute(State)""", dump(View.ALL_FUNCTIONS, OPTIONS));
    }

    @Test
    void allFunctionsShowExternalCallsWhenAsked() {
        assertEquals("""
                package app
                  type Calls app/Calls.java:3-9
                    method execute(State) 4-8
                      6 call app.State#push(int)
                  type Handler app/Handler.java:3-5
                    method execute(State) 4-4
                  type Reads app/Reads.java:3-13
                    method execute(State) 4-12
                      6 call app.State#pending()
                      6 if
                        state.pending()
                          7 call app.State#push(int)
                          8 call app.State#reset()
                  type State app/State.java:3-30
                    method <instance-init>() 4-4
                      4 call external new java.util.ArrayDeque()
                    method failed() 10-12
                    method log(int) 28-29
                    method pending() 6-8
                      7 call external stack.isEmpty()
                    method push(int) 14-17
                      15 call external stack.push()
                      16 call app.State#log(int)
                    method reset() 19-26
                      20 call external stack.clear()
                      21 call app.State#log(int)
                      22 call app.State#log(int)
                      23 call app.State#log(int)
                      24 call app.State#log(int)
                      25 call app.State#log(int)
                  type Walker app/Walker.java:5-29
                    method <init>(List) 8-10
                    method step(State) 22-28
                      23 loop for (Handler handler : handlers)
                        24 call app.Handler#execute(State)
                    method walk(State) 12-20
                      13 loop while (state.pending())
                        13 call app.State#pending()
                        14 call app.Walker#step(State)
                      16 call app.State#failed()
                      16 if
                        state.failed()
                          17 call external state.toString()
                      19 call app.State#reset()
                call app.Calls#execute(State) -> app.State#push(int)
                call app.Reads#execute(State) -> app.State#pending()
                call app.Reads#execute(State) -> app.State#push(int)
                call app.Reads#execute(State) -> app.State#reset()
                call app.State#push(int) -> app.State#log(int)
                call app.State#reset() -> app.State#log(int)
                call app.Walker#step(State) -> app.Handler#execute(State)
                call app.Walker#walk(State) -> app.State#failed()
                call app.Walker#walk(State) -> app.State#pending()
                call app.Walker#walk(State) -> app.State#reset()
                call app.Walker#walk(State) -> app.Walker#step(State)
                dependency app.Calls -> app.Handler
                dependency app.Calls -> app.State
                dependency app.Handler -> app.State
                dependency app.Reads -> app.Handler
                dependency app.Reads -> app.State
                dependency app.Walker -> app.Handler
                dependency app.Walker -> app.State
                override app.Calls#execute(State) -> app.Handler#execute(State)
                override app.Reads#execute(State) -> app.Handler#execute(State)""", dump(View.ALL_FUNCTIONS, OPTIONS.withShowExternal(true)));
    }

    @Test
    void dependenciesKeepPackagesTypesAndDependencyEdges() {
        assertEquals("""
                package app
                  type Calls app/Calls.java:3-9
                  type Handler app/Handler.java:3-5
                  type Reads app/Reads.java:3-13
                  type State app/State.java:3-30
                  type Walker app/Walker.java:5-29
                dependency app.Calls -> app.Handler
                dependency app.Calls -> app.State
                dependency app.Handler -> app.State
                dependency app.Reads -> app.Handler
                dependency app.Reads -> app.State
                dependency app.Walker -> app.Handler
                dependency app.Walker -> app.State""", dump(View.DEPENDENCIES, OPTIONS));
    }

    private static String dump(View view, ViewOptions options) {
        return String.join("\n", ModelDump.dump(view.project(Fixtures.model("views"), options)));
    }
}
