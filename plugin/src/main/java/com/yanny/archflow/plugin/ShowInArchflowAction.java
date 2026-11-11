package com.yanny.archflow.plugin;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;

public final class ShowInArchflowAction extends AnAction implements DumbAware {
    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(AnActionEvent event) {
        boolean available = event.getProject() != null && event.getData(CommonDataKeys.EDITOR) != null
                && event.getData(CommonDataKeys.VIRTUAL_FILE) != null;
        event.getPresentation().setEnabledAndVisible(available);
    }

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (project == null || editor == null || file == null) {
            return;
        }
        int line = editor.getCaretModel().getLogicalPosition().line + 1;
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(ArchflowToolWindowFactory.ID);
        if (window == null) {
            return;
        }
        window.show(() -> {
            Content content = window.getContentManager().getContent(0);
            if (content != null && content.getComponent() instanceof ArchflowPanel panel) {
                panel.reveal(file, line, false);
            }
        });
    }
}
