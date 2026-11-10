package com.yanny.archflow.link;

import com.yanny.archflow.facts.FileFacts;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StructureTest {
    @Test
    void packagesNestTypesAndMethodsWithTheirLines() {
        assertEquals("""
                package a
                  type Walker a/Walker.java:5-20
                    method <init>(Handler) 8-10
                    method walk(int[],String[]) 12-13
                    type State 15-19
                      method values() 16-18
                        17 return
                  package b
                    type Handler a/b/Handler.java:3-5
                      method handle(Object) 4-4
                package c
                  type Mode c/Mode.java:3-10
                    method next() 7-9
                      8 return""", Fixtures.dump("structure"));
    }

    @Test
    void fileThatFailsToParseIsSkippedWithItsProblem() {
        List<String> failed = Fixtures.facts("structure").stream()
                .filter(f -> f.problem() != null)
                .map(FileFacts::file)
                .toList();

        assertEquals(List.of("c/Broken.java"), failed);
    }
}
