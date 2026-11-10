package com.yanny.archflow.plugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;

import java.io.IOException;
import java.nio.file.Path;

final class SourceNavigator {
    private static final Logger LOG = Logger.getInstance(SourceNavigator.class);

    private SourceNavigator() {
    }

    static void open(Project project, String file, int line, String commit) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            VirtualFile target;
            try {
                target = commit == null ? workingFile(project, file) : fileAt(project, file, commit);
            } catch (IOException e) {
                LOG.warn("Cannot open " + file + (commit == null ? "" : " at " + commit), e);
                return;
            }
            if (target != null) {
                ApplicationManager.getApplication().invokeLater(
                        () -> new OpenFileDescriptor(project, target, Math.max(line - 1, 0), 0).navigate(true), project.getDisposed());
            }
        });
    }

    private static VirtualFile workingFile(Project project, String file) throws IOException {
        Path path = ArchflowService.of(project).git().workTree().resolve(file);
        return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
    }

    private static VirtualFile fileAt(Project project, String file, String commit) throws IOException {
        String text = ArchflowService.of(project).fileAt(commit, file);
        String name = Path.of(file).getFileName().toString();
        LightVirtualFile light = new LightVirtualFile(name + " @ " + ArchflowService.shortId(commit),
                FileTypeManager.getInstance().getFileTypeByFileName(name), text);
        light.setWritable(false);
        return light;
    }
}
