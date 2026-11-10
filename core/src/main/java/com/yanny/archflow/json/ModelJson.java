package com.yanny.archflow.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.yanny.archflow.model.Model;

public final class ModelJson {
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapterFactory(new StepAdapterFactory())
            .disableHtmlEscaping()
            .create();

    private ModelJson() {
    }

    public static String write(Model model) {
        return GSON.toJson(model);
    }

    public static Model read(String json) {
        return GSON.fromJson(json, Model.class);
    }
}
