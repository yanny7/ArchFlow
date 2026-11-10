package com.yanny.archflow.view;

public record ViewOptions(int minLines, boolean showExternal) {
    public static final ViewOptions DEFAULT = new ViewOptions(10, false);

    public ViewOptions withMinLines(int minLines) {
        return new ViewOptions(minLines, showExternal);
    }

    public ViewOptions withShowExternal(boolean showExternal) {
        return new ViewOptions(minLines, showExternal);
    }
}
