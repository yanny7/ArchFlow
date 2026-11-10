package com.yanny.archflow.json;

import com.yanny.archflow.link.Fixtures;
import com.yanny.archflow.model.Location;
import com.yanny.archflow.model.Model;
import com.yanny.archflow.model.Node;
import com.yanny.archflow.model.NodeKind;
import com.yanny.archflow.model.Step;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelJsonTest {
    @Test
    void writesNodesEdgesAndTaggedSteps() {
        Node pkg = new Node("a", NodeKind.PACKAGE, "a", null, null);
        Node method = new Node("a.A#run()", NodeKind.METHOD, "run", "a.A", new Location("a/A.java", 3, 5));
        Model model = new Model(List.of(method, pkg), List.of(), Map.of(method.id(), List.of(
                new Step.Branch(4, null, List.of(new Step.Arm("ready", List.of(new Step.Call(4, null, "go()", true))))))));

        assertEquals("""
                {"nodes":[{"id":"a","kind":"PACKAGE","name":"a"},\
                {"id":"a.A#run()","kind":"METHOD","name":"run","parent":"a.A","location":{"file":"a/A.java","line":3,"endLine":5}}],\
                "edges":[],\
                "flows":{"a.A#run()":[{"kind":"branch","line":4,"arms":[{"condition":"ready","steps":\
                [{"kind":"call","line":4,"text":"go()","external":true}]}]}]}}""", ModelJson.write(model));
    }

    @Test
    void readsBackTheSameModelAndBytes() {
        Model model = Fixtures.model("linking");
        String json = ModelJson.write(model);

        assertEquals(model, ModelJson.read(json));
        assertEquals(json, ModelJson.write(ModelJson.read(json)));
    }
}
