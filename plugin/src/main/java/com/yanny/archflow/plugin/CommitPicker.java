package com.yanny.archflow.plugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.DropDownLink;
import com.intellij.ui.components.JBList;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import com.yanny.archflow.git.GitRepo;

import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import java.awt.BorderLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

final class CommitPicker extends DropDownLink<CommitPicker.Choice> {
    record Choice(String revision, String label, String description) {
        static Choice of(GitRepo.CommitInfo commit) {
            String id = ArchflowService.shortId(commit.id()) + " ";
            return new Choice(commit.id(), id + StringUtil.shortenTextWithEllipsis(commit.message(), LABEL_LENGTH, 0), id + commit.message());
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final int LABEL_LENGTH = 50;
    private static final int PAGE = 100;
    private static final int SEARCH_DELAY_MS = 200;

    CommitPicker(Project project, Choice fixed, Consumer<Choice> chosen) {
        super(fixed, link -> new Search(project, fixed, (CommitPicker) link, chosen).popup);
        setToolTipText(fixed.description());
    }

    void select(Choice choice) {
        setSelectedItem(choice);
        setText(choice.label());
        setToolTipText(choice.description());
    }

    private record Page(GitRepo.CommitInfo exact, List<GitRepo.CommitInfo> commits, String error) {
    }

    private static final class Search {
        private final Project project;
        private final Choice fixed;
        private final CollectionListModel<Choice> model = new CollectionListModel<>();
        private final JBList<Choice> list = new JBList<>(model);
        private final SearchTextField field = new SearchTextField(false);
        private final Alarm typing;
        private final JBPopup popup;
        private final Set<String> ids = new HashSet<>();
        private String query = "";
        private int generation;
        private int matched;
        private boolean loading;
        private boolean done;

        Search(Project project, Choice fixed, CommitPicker link, Consumer<Choice> chosen) {
            this.project = project;
            this.fixed = fixed;
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setCellRenderer(new SimpleListCellRenderer<>() {
                @Override
                public void customize(JList<? extends Choice> list, Choice value, int index, boolean selected, boolean focus) {
                    setText(value.description());
                }
            });
            JScrollPane scroll = ScrollPaneFactory.createScrollPane(list, true);
            scroll.getVerticalScrollBar().getModel().addChangeListener(event -> {
                if (list.getLastVisibleIndex() >= model.getSize() - 10) {
                    load();
                }
            });
            JPanel panel = new JPanel(new BorderLayout());
            panel.add(field, BorderLayout.NORTH);
            panel.add(scroll, BorderLayout.CENTER);
            panel.setPreferredSize(JBUI.size(560, 420));
            popup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, field.getTextEditor())
                    .setRequestFocus(true).setFocusable(true).setResizable(true).setCancelOnClickOutside(true).createPopup();
            typing = new Alarm(Alarm.ThreadToUse.SWING_THREAD, popup);
            Runnable choose = () -> {
                Choice choice = list.getSelectedValue();
                if (choice != null) {
                    popup.cancel();
                    link.select(choice);
                    chosen.accept(choice);
                }
            };
            ScrollingUtil.installActions(list, field.getTextEditor());
            field.getTextEditor().addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent event) {
                    if (event.getKeyCode() == KeyEvent.VK_ENTER) {
                        choose.run();
                    }
                }
            });
            list.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    choose.run();
                }
            });
            field.addDocumentListener(new DocumentAdapter() {
                @Override
                protected void textChanged(DocumentEvent event) {
                    typing.cancelAllRequests();
                    typing.addRequest(() -> restart(field.getText().trim()), SEARCH_DELAY_MS);
                }
            });
            restart("");
        }

        private void restart(String text) {
            query = text;
            generation++;
            model.removeAll();
            ids.clear();
            matched = 0;
            loading = false;
            done = false;
            list.getEmptyText().setText("Loading…");
            if (query.isEmpty()) {
                model.add(fixed);
            }
            load();
        }

        private void load() {
            if (loading || done) {
                return;
            }
            loading = true;
            int current = generation;
            String text = query;
            int skip = matched;
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                Page page;
                try {
                    ArchflowService service = ArchflowService.of(project);
                    GitRepo.CommitInfo exact = skip == 0 && !text.isEmpty() ? revision(service, text) : null;
                    page = new Page(exact, service.history(text, skip, PAGE), null);
                } catch (Exception e) {
                    page = new Page(null, List.of(), e.getMessage());
                }
                Page result = page;
                ApplicationManager.getApplication().invokeLater(() -> loaded(current, result), any -> popup.isDisposed());
            });
        }

        private static GitRepo.CommitInfo revision(ArchflowService service, String text) {
            try {
                return service.commit(text);
            } catch (Exception ignored) {
                return null;
            }
        }

        private void loaded(int current, Page page) {
            if (current != generation) {
                return;
            }
            loading = false;
            matched += page.commits().size();
            done = page.error() != null || page.commits().size() < PAGE;
            List<GitRepo.CommitInfo> commits = new ArrayList<>(page.commits());
            if (page.exact() != null) {
                commits.addFirst(page.exact());
            }
            for (GitRepo.CommitInfo commit : commits) {
                if (ids.add(commit.id())) {
                    model.add(Choice.of(commit));
                }
            }
            if (list.getSelectedIndex() < 0 && model.getSize() > 0) {
                list.setSelectedIndex(0);
            }
            list.getEmptyText().setText(page.error() != null ? page.error() : "No commit matches");
            if (!done && list.getLastVisibleIndex() >= model.getSize() - 10) {
                load();
            }
        }
    }
}
