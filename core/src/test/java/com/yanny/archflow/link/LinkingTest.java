package com.yanny.archflow.link;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LinkingTest {
    @Test
    void callsResolveThroughDeclaredTypesImportsFieldsAndReturnTypes() {
        assertEquals("""
                package app
                  type Walker app/Walker.java:8-31
                    method <init>() 11-13
                      12 call core.Context#create(String[])
                      12 call core.Base#<init>(Context)
                    method walk(List) 15-30
                      16 loop for (Handler handler : handlers)
                        17 call core.Handler#handle(Context)
                      19 loop for (var handler : handlers)
                        20 call core.Handler#handle(Context,int)
                      22 call core.Handler#handle(Context)
                      23 call core.Context#first()
                      23 call core.Handler#handle(Context)
                      24 call core.handlers.Echo
                      24 call core.Context.Registry#register(Handler)
                      25 call core.handlers.Echo
                      25 call core.Context.Entry
                      25 call core.Context.Registry#register(Entry)
                      26 call core.Context.Registry#register(Handler)
                      26 call ? handlers.forEach()
                      27 call util.Strings#trim(String)
                      27 call core.Base#log(String)
                      28 call core.Context#create(String[])
                      29 call ? unknown.call()
                package core
                  type Base core/Base.java:3-12
                    method <init>(Context) 6-8
                    method log(String) 10-11
                  type Context core/Context.java:5-32
                    method create(String[]) 13-15
                      14 call core.Context
                      14 return
                    method first() 9-11
                      10 call ? handlers.get()
                      10 return
                    type Entry 17-21
                      method handler() 17-17
                      method name() 18-20
                        19 call ? name.trim()
                        19 return
                    type Registry 23-31
                      method register(Entry) 27-30
                        28 call core.Context.Entry#handler()
                        28 call core.Context.Registry#register(Handler)
                        29 call core.Context.Entry#name()
                      method register(Handler) 24-25
                  type Handler core/Handler.java:3-7
                    method handle(Context) 4-4
                    method handle(Context,int) 6-6
                  package handlers
                    type Echo core/handlers/Echo.java:6-16
                      method handle(Context) 7-10
                        9 call core.handlers.Echo#handle(Context,int)
                        9 return
                      method handle(Context,int) 12-15
                        14 return
                package util
                  type Strings util/Strings.java:3-7
                    method trim(String) 4-6
                      5 call ? text.trim()
                      5 return
                call app.Walker#<init>() -> core.Base#<init>(Context)
                call app.Walker#<init>() -> core.Context#create(String[])
                call app.Walker#walk(List) -> core.Base#log(String)
                call app.Walker#walk(List) -> core.Context#create(String[])
                call app.Walker#walk(List) -> core.Context#first()
                call app.Walker#walk(List) -> core.Context.Entry
                call app.Walker#walk(List) -> core.Context.Registry#register(Entry)
                call app.Walker#walk(List) -> core.Context.Registry#register(Handler)
                call app.Walker#walk(List) -> core.Handler#handle(Context)
                call app.Walker#walk(List) -> core.Handler#handle(Context,int)
                call app.Walker#walk(List) -> core.handlers.Echo
                call app.Walker#walk(List) -> util.Strings#trim(String)
                call core.Context#create(String[]) -> core.Context
                call core.Context.Registry#register(Entry) -> core.Context.Entry#handler()
                call core.Context.Registry#register(Entry) -> core.Context.Entry#name()
                call core.Context.Registry#register(Entry) -> core.Context.Registry#register(Handler)
                call core.handlers.Echo#handle(Context) -> core.handlers.Echo#handle(Context,int)
                dependency app.Walker -> core.Base
                dependency app.Walker -> core.Context
                dependency app.Walker -> core.Context.Entry
                dependency app.Walker -> core.Context.Registry
                dependency app.Walker -> core.Handler
                dependency app.Walker -> core.handlers.Echo
                dependency app.Walker -> util.Strings
                dependency core.Base -> core.Context
                dependency core.Context -> core.Context.Registry
                dependency core.Context -> core.Handler
                dependency core.Context.Entry -> core.Handler
                dependency core.Context.Registry -> core.Context.Entry
                dependency core.Context.Registry -> core.Handler
                dependency core.Handler -> core.Context
                dependency core.handlers.Echo -> core.Context
                dependency core.handlers.Echo -> core.Handler""", Fixtures.dump("linking"));
    }
}
