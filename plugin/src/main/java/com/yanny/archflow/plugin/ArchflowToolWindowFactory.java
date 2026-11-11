package com.yanny.archflow.plugin;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.jcef.JBCefApp;

import javax.swing.SwingConstants;

public final class ArchflowToolWindowFactory implements ToolWindowFactory, DumbAware {
    static final String ID = "Archflow";

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        ContentFactory factory = ContentFactory.getInstance();
        if (!JBCefApp.isSupported()) {
            JBLabel label = new JBLabel("Archflow needs JCEF, which this IDE does not support.", SwingConstants.CENTER);
            toolWindow.getContentManager().addContent(factory.createContent(label, "", false));
            return;
        }
        ArchflowPanel panel = new ArchflowPanel(project);
        Content content = factory.createContent(panel, "", false);
        content.setDisposer(panel);
        toolWindow.getContentManager().addContent(content);
    }
}
