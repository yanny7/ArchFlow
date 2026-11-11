package com.yanny.archflow.plugin;

import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefBrowserBase;
import com.intellij.ui.jcef.JBCefJSQuery;
import com.intellij.util.Alarm;
import com.intellij.util.ui.WrapLayout;
import com.yanny.archflow.viewer.ViewerPage;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class ArchflowPanel extends JPanel implements Disposable {
    private static final CommitPicker.Choice NONE = new CommitPicker.Choice(null, "None", "None");
    private static final CommitPicker.Choice WORKING_COPY = new CommitPicker.Choice(null, "Working copy", "Working copy");
    private static final int REFRESH_DELAY_MS = 1500;
    private static final int FOLLOW_DELAY_MS = 300;

    private final Project project;
    private final JBCefBrowser browser = new JBCefBrowser();
    private final JBCefJSQuery openQuery;
    private final CommitPicker from;
    private final CommitPicker to;
    private final JBLabel fromLabel = new JBLabel("From");
    private final JBCheckBox single = new JBCheckBox("Single commit");
    private final JBTextField paths = new JBTextField(30);
    private final JSpinner minLines = new JSpinner(new SpinnerNumberModel(10, 1, 1000, 1));
    private final JBCheckBox follow = new JBCheckBox("Follow caret");
    private final Alarm refresh = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
    private final Alarm caret = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
    private final List<String> pending = new ArrayList<>();
    private boolean loaded;

    ArchflowPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        Disposer.register(this, browser);
        openQuery = JBCefJSQuery.create((JBCefBrowserBase) browser);
        Disposer.register(this, openQuery);
        openQuery.addHandler(request -> {
            String[] parts = request.split("\n", -1);
            SourceNavigator.open(project, parts[0], Integer.parseInt(parts[1]), parts[2].isEmpty() ? null : parts[2]);
            return null;
        });
        browser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadEnd(CefBrowser cefBrowser, CefFrame frame, int httpStatusCode) {
                if (frame.isMain()) {
                    ApplicationManager.getApplication().invokeLater(ArchflowPanel.this::pageLoaded);
                }
            }
        }, browser.getCefBrowser());
        browser.loadHTML(ViewerPage.html(null));

        ArchflowSettings.Data settings = ArchflowSettings.of(project).getState();
        paths.setText(String.join(", ", settings.paths));
        minLines.setValue(settings.minLines);
        follow.setSelected(settings.followCaret);
        follow.addActionListener(event -> ArchflowSettings.of(project).getState().followCaret = follow.isSelected());
        from = new CommitPicker(project, NONE, choice -> rebuild());
        to = new CommitPicker(project, WORKING_COPY, choice -> rebuild());
        single.setToolTipText("Shows the changes of the To commit, or of the working copy against HEAD");
        single.setSelected(settings.singleCommit);
        single.addActionListener(event -> {
            ArchflowSettings.of(project).getState().singleCommit = single.isSelected();
            showFrom();
            rebuild();
        });
        showFrom();
        JButton show = new JButton("Show");
        show.addActionListener(event -> rebuild());
        paths.addActionListener(event -> rebuild());
        minLines.addChangeListener(event -> rebuild());
        JPanel toolbar = new JPanel(new WrapLayout(FlowLayout.LEFT));
        toolbar.add(single);
        toolbar.add(fromLabel);
        toolbar.add(from);
        toolbar.add(new JBLabel("To"));
        toolbar.add(to);
        toolbar.add(new JBLabel("Paths"));
        toolbar.add(paths);
        toolbar.add(new JBLabel("Min lines"));
        toolbar.add(minLines);
        toolbar.add(show);
        toolbar.add(follow);
        add(toolbar, BorderLayout.NORTH);
        add(browser.getComponent(), BorderLayout.CENTER);

        var connection = project.getMessageBus().connect(this);
        connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(List<? extends VFileEvent> events) {
                String base = project.getBasePath();
                boolean sources = events.stream()
                        .anyMatch(event -> event.getPath().endsWith(".java") && base != null && event.getPath().startsWith(base));
                if (to.getSelectedItem() == WORKING_COPY && sources) {
                    refresh.cancelAllRequests();
                    refresh.addRequest(ArchflowPanel.this::rebuild, REFRESH_DELAY_MS);
                }
            }
        });
        ApplicationManager.getApplication().getMessageBus().connect(this)
                .subscribe(LafManagerListener.TOPIC, (LafManagerListener) source -> applyTheme());
        EditorFactory.getInstance().getEventMulticaster().addCaretListener(new CaretListener() {
            @Override
            public void caretPositionChanged(CaretEvent event) {
                Editor editor = event.getEditor();
                if (follow.isSelected() && project.equals(editor.getProject()) && isShowing()) {
                    VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
                    int line = event.getNewPosition().line + 1;
                    caret.cancelAllRequests();
                    caret.addRequest(() -> reveal(file, line, true), FOLLOW_DELAY_MS);
                }
            }
        }, this);
        rebuild();
    }

    void showCommits(String fromRevision, String toRevision) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                ArchflowService service = ArchflowService.of(project);
                CommitPicker.Choice toChoice = CommitPicker.Choice.of(service.commit(toRevision));
                CommitPicker.Choice fromChoice = fromRevision == null ? null : CommitPicker.Choice.of(service.commit(fromRevision));
                ApplicationManager.getApplication().invokeLater(() -> {
                    to.select(toChoice);
                    single.setSelected(fromChoice == null);
                    ArchflowSettings.of(project).getState().singleCommit = fromChoice == null;
                    if (fromChoice != null) {
                        from.select(fromChoice);
                    }
                    showFrom();
                    rebuild();
                }, project.getDisposed());
            } catch (IOException e) {
                deliver(message("Archflow cannot read git: " + e.getMessage()));
            }
        });
    }

    private void showFrom() {
        fromLabel.setVisible(!single.isSelected());
        from.setVisible(!single.isSelected());
    }

    private void rebuild() {
        ArchflowSettings.Data settings = ArchflowSettings.of(project).getState();
        settings.paths = Arrays.stream(paths.getText().split(",")).map(String::trim).filter(path -> !path.isEmpty()).toList();
        settings.minLines = (Integer) minLines.getValue();
        ArchflowService.Selection selection = new ArchflowService.Selection(from.getSelectedItem().revision(),
                to.getSelectedItem().revision(), single.isSelected(), settings.paths, settings.minLines);
        deliver(message("Building the model…"));
        new Task.Backgroundable(project, "Building the architecture model", false) {
            @Override
            public void run(ProgressIndicator indicator) {
                try {
                    deliver("archflowLoad(" + ViewerPage.parse(ArchflowService.of(project).viewerData(selection)) + ");");
                } catch (Exception e) {
                    deliver(message("Archflow failed: " + e.getMessage()));
                }
            }
        }.queue();
    }

    void reveal(VirtualFile file, int line, boolean following) {
        if (file == null || !file.isInLocalFileSystem()) {
            return;
        }
        String commit = to.getSelectedItem().revision();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                Path root = ArchflowService.of(project).git().workTree();
                Path path = file.toNioPath();
                if (path.startsWith(root)) {
                    String relative = root.relativize(path).toString().replace('\\', '/');
                    boolean exact = commit == null || sameAs(file, commit, relative);
                    String arguments = jsString(relative) + ", " + line + ", " + exact + ", " + following;
                    deliver("if (window.archflowReveal) archflowReveal(" + arguments + ");");
                }
            } catch (IOException e) {
                deliver(message("Archflow cannot read git: " + e.getMessage()));
            }
        });
    }

    private boolean sameAs(VirtualFile file, String commit, String path) {
        String text = ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {
            Document document = FileDocumentManager.getInstance().getDocument(file);
            return document == null ? null : document.getText();
        });
        try {
            return text != null && text.equals(StringUtil.convertLineSeparators(ArchflowService.of(project).fileAt(commit, path)));
        } catch (IOException e) {
            return false;
        }
    }

    private void pageLoaded() {
        execute("window.archflowOpen = function (file, line, commit) {"
                + openQuery.inject("file + '\\n' + line + '\\n' + (commit || '')") + "};");
        applyTheme();
        loaded = true;
        pending.forEach(this::execute);
        pending.clear();
    }

    private void applyTheme() {
        execute("if (window.archflowTheme) archflowTheme('" + (JBColor.isBright() ? "light" : "dark") + "');");
    }

    private void deliver(String script) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (loaded) {
                execute(script);
            } else {
                pending.add(script);
            }
        }, project.getDisposed());
    }

    private void execute(String script) {
        browser.getCefBrowser().executeJavaScript(script, browser.getCefBrowser().getURL(), 0);
    }

    private static String message(String text) {
        return "archflowMessage(" + jsString(text) + ");";
    }

    private static String jsString(String text) {
        return "'" + text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", " ") + "'";
    }

    @Override
    public void dispose() {
    }
}
