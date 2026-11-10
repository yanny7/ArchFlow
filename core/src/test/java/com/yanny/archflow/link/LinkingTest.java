package com.yanny.archflow.link;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LinkingTest {
    @Test
    void callsResolveThroughDeclaredTypesImportsFieldsAndReturnTypes() {
        assertEquals("""
                package app
                  type Walker app/Walker.java:8-37
                    method <clinit>() 9-15
                      9 call core.handlers.Echo
                      9 call ? value.first()
                      9 call external java.util.List.of()
                      14 call util.Strings#trim(String)
                    method <init>() 17-19
                      18 call core.Context#create(String[])
                      18 call core.Base#<init>(Context)
                    method <instance-init>() 10-11
                      11 call core.Context#create(String[])
                    method walk(List) 21-36
                      22 loop for (Handler handler : handlers)
                        23 call core.Handler#handle(Context)
                      25 loop for (var handler : handlers)
                        26 call core.Handler#handle(Context,int)
                      28 call core.Handler#handle(Context)
                      29 call core.Context#first()
                      29 call core.Handler#handle(Context)
                      30 call core.handlers.Echo
                      30 call core.Context.Registry#register(Handler)
                      31 call core.handlers.Echo
                      31 call core.Context.Entry
                      31 call core.Context.Registry#register(Entry)
                      32 call core.Context.Registry#register(Handler)
                      32 call external handlers.forEach()
                      33 call util.Strings#trim(String)
                      33 call core.Base#log(String)
                      34 call core.Context#create(String[])
                      35 call ? unknown.call()
                package core
                  type Base core/Base.java:3-12
                    method <init>(Context) 6-8
                    method log(String) 10-11
                  type Context core/Context.java:5-32
                    method <instance-init>() 6-6
                      6 call core.Context.Registry
                    method create(String[]) 13-15
                      14 call core.Context
                      14 return
                    method first() 9-11
                      10 call external handlers.get()
                      10 return
                    type Entry 17-21
                      method handler() 17-17
                      method name() 18-20
                        19 call external name.trim()
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
                  type Visitor core/Visitor.java:3-5
                    method visit(T) 4-4
                  package handlers
                    type Echo core/handlers/Echo.java:7-22
                      method handle(Context) 8-11
                        10 call core.handlers.Echo#handle(Context,int)
                        10 return
                      method handle(Context,int) 13-16
                        15 return
                      method visit(Context) 18-21
                        20 call core.handlers.Echo#handle(Context)
                package util
                  type Strings util/Strings.java:3-7
                    method trim(String) 4-6
                      5 call external text.trim()
                      5 return
                call app.Walker#<clinit>() -> core.handlers.Echo
                call app.Walker#<clinit>() -> util.Strings#trim(String)
                call app.Walker#<init>() -> core.Base#<init>(Context)
                call app.Walker#<init>() -> core.Context#create(String[])
                call app.Walker#<instance-init>() -> core.Context#create(String[])
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
                call core.Context#<instance-init>() -> core.Context.Registry
                call core.Context#create(String[]) -> core.Context
                call core.Context.Registry#register(Entry) -> core.Context.Entry#handler()
                call core.Context.Registry#register(Entry) -> core.Context.Entry#name()
                call core.Context.Registry#register(Entry) -> core.Context.Registry#register(Handler)
                call core.handlers.Echo#handle(Context) -> core.handlers.Echo#handle(Context,int)
                call core.handlers.Echo#visit(Context) -> core.handlers.Echo#handle(Context)
                dependency app.Walker -> core.Base
                dependency app.Walker -> core.Context
                dependency app.Walker -> core.Context.Entry
                dependency app.Walker -> core.Context.Registry
                dependency app.Walker -> core.Handler
                dependency app.Walker -> core.Visitor
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
                dependency core.handlers.Echo -> core.Handler
                dependency core.handlers.Echo -> core.Visitor
                override core.handlers.Echo#handle(Context) -> core.Handler#handle(Context)
                override core.handlers.Echo#handle(Context,int) -> core.Handler#handle(Context,int)
                override core.handlers.Echo#visit(Context) -> core.Visitor#visit(T)""", Fixtures.dump("linking"));
    }
}
