package com.yanny.archflow.plugin;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsLogCommitSelection;
import com.intellij.vcs.log.VcsLogDataKeys;

import java.util.List;

public final class ShowCommitsInArchflowAction extends AnAction implements DumbAware {
    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(AnActionEvent event) {
        VcsLogCommitSelection selection = event.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION);
        event.getPresentation().setEnabledAndVisible(event.getProject() != null && selection != null && selection.getRows().length > 0);
    }

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        VcsLogCommitSelection selection = event.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION);
        if (project == null || selection == null || selection.getCommits().isEmpty()) {
            return;
        }
        // The log lists every commit above its ancestors, so the first selected commit is the newest.
        List<CommitId> commits = selection.getCommits();
        String newest = commits.getFirst().getHash().asString();
        String oldest = commits.size() == 1 ? null : commits.getLast().getHash().asString();
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(ArchflowToolWindowFactory.ID);
        if (window == null) {
            return;
        }
        window.show(() -> {
            Content content = window.getContentManager().getContent(0);
            if (content != null && content.getComponent() instanceof ArchflowPanel panel) {
                panel.showCommits(oldest, newest);
            }
        });
    }
}
