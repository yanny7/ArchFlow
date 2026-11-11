package com.yanny.archflow.view;

import com.yanny.archflow.model.Model;

public enum View {
    ALL_FUNCTIONS {
        @Override
        public Model project(Model model, ViewOptions options) {
            return new MethodProjection(model, options, method -> true, true).project();
        }
    },
    OUTLINE {
        @Override
        public Model project(Model model, ViewOptions options) {
            return new MethodProjection(model, options, method -> method.lines() >= options.minLines(), false).project();
        }
    },
    DEPENDENCIES {
        @Override
        public Model project(Model model, ViewOptions options) {
            return DependencyProjection.project(model);
        }
    };

    public abstract Model project(Model model, ViewOptions options);
}
