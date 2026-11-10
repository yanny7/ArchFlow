package com.yanny.archflow.diff;

import com.yanny.archflow.link.Fixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelDiffTest {
    @Test
    void listsAddedRemovedAndChangedNodesTheirAncestorsAndChangedEdges() {
        Diff diff = ModelDiff.diff(Fixtures.model("diff-before"), Fixtures.model("diff-after"));

        assertEquals("""
                * package a
                + type a.Context
                - type a.Old
                * type a.Walker
                + method a.Walker#<instance-init>()
                ~ method a.Walker#step(long) (was a.Walker#step(int))
                ~ method a.Walker#walk(int)
                + dependency a.Walker -> a.Context""", String.join("\n", DiffText.lines(diff, true)));
    }

    @Test
    void sameModelHasNoChanges() {
        assertTrue(ModelDiff.diff(Fixtures.model("diff-before"), Fixtures.model("diff-before")).isEmpty());
    }
}
