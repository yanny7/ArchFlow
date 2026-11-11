package com.yanny.archflow.plugin;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.yanny.archflow.view.ViewOptions;

import java.util.ArrayList;
import java.util.List;

@Service(Service.Level.PROJECT)
@State(name = "Archflow", storages = @Storage("archflow.xml"))
public final class ArchflowSettings implements PersistentStateComponent<ArchflowSettings.Data> {
    public static final class Data {
        public List<String> paths = new ArrayList<>();
        public int minLines = ViewOptions.DEFAULT.minLines();
        public boolean followCaret;
        public boolean singleCommit;
    }

    private Data data = new Data();

    public static ArchflowSettings of(Project project) {
        return project.getService(ArchflowSettings.class);
    }

    @Override
    public Data getState() {
        return data;
    }

    @Override
    public void loadState(Data state) {
        data = state;
    }
}
