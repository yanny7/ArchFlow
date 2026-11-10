package com.yanny.archflow.json;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.yanny.archflow.model.Step;

import java.io.IOException;
import java.util.Map;

final class StepAdapterFactory implements TypeAdapterFactory {
    private static final Map<String, Class<? extends Step>> KINDS = Map.of(
            "call", Step.Call.class,
            "branch", Step.Branch.class,
            "loop", Step.Loop.class,
            "return", Step.Return.class,
            "throw", Step.Throw.class);

    @Override
    @SuppressWarnings("unchecked")
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (type.getRawType() != Step.class) {
            return null;
        }
        TypeAdapter<JsonElement> elements = gson.getAdapter(JsonElement.class);
        return (TypeAdapter<T>) new TypeAdapter<Step>() {
            @Override
            public void write(JsonWriter out, Step step) throws IOException {
                JsonObject tagged = new JsonObject();
                tagged.addProperty("kind", kind(step));
                gson.toJsonTree(step, step.getClass()).getAsJsonObject().entrySet()
                        .forEach(entry -> tagged.add(entry.getKey(), entry.getValue()));
                elements.write(out, tagged);
            }

            @Override
            public Step read(JsonReader in) throws IOException {
                JsonObject object = elements.read(in).getAsJsonObject();
                Class<? extends Step> kind = KINDS.get(object.remove("kind").getAsString());
                if (kind == null) {
                    throw new JsonParseException("Unknown step kind in " + object);
                }
                return gson.fromJson(object, kind);
            }
        }.nullSafe();
    }

    private static String kind(Step step) {
        return switch (step) {
            case Step.Call ignored -> "call";
            case Step.Branch ignored -> "branch";
            case Step.Loop ignored -> "loop";
            case Step.Return ignored -> "return";
            case Step.Throw ignored -> "throw";
        };
    }
}
