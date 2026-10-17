package org.c2w.gui;

import org.c2w.data.repository.CatalogUpdateService;
import org.c2w.data.repository.CatalogUpdateService.Outcome;
import org.c2w.data.repository.CatalogUpdateService.Phase;
import org.c2w.data.repository.CatalogValidator;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Config;
import org.c2w.infra.GitHubCatalogRemote;
import org.c2w.infra.GitHubProject;
import org.c2w.infra.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * "File" &gt; "Update catalog ...": the surface over {@link CatalogUpdateService} - a cancellable
 * modal progress dialog while it works in the background (never on the Swing thread), then the
 * message for its outcome, the differences with Apply/Cancel, and the installed notice. The new
 * catalog only applies after a restart - nothing is reloaded here.
 */
public final class CatalogUpdateDialog {

    /** How many problems of a rejected catalog the message lists (all of them are in the log). */
    private static final int SHOWN_PROBLEMS = 5;

    /** The data files in the order of the differences, with the text key of their kind. */
    private static final Map<String, String> KINDS = kinds();

    private final Frame owner;
    private final CatalogUpdateService service;
    private final Consumer<String> openWeb;

    private CatalogUpdateDialog(Frame owner, CatalogUpdateService service, Consumer<String> openWeb) {
        this.owner = owner;
        this.service = service;
        this.openWeb = openWeb;
    }

    /** Runs the whole update against the published catalog on GitHub. */
    public static void open(Frame owner, Consumer<String> openWeb) {
        CatalogUpdateService service = new CatalogUpdateService(new GitHubCatalogRemote(),
                Config.getDownloadedCatalogDir(), Config.getPreviousCatalogDir(),
                Config.getCatalogDownloadDir());
        new CatalogUpdateDialog(owner, service, openWeb).run();
    }

    private void run() {
        Outcome checked = withProgress(service::check);
        switch (checked.status()) {
            case FAILED -> error(text("catalogUpdate.failed") + "\n" + checked.reason());
            case NEEDS_NEWER_PROGRAM -> needsNewerProgram();
            case UP_TO_DATE -> info(LanguageService.displayName("catalogUpdate.upToDate", checked.installed()));
            case REJECTED -> rejected(checked);
            case UPDATE_AVAILABLE -> offer(checked);
            default -> {
                // CANCELLED: nothing changed, nothing to say.
            }
        }
    }

    private void offer(Outcome checked) {
        Object[] options = {text("catalogUpdate.apply"), text("catalogUpdate.cancel")};
        int answer = JOptionPane.showOptionDialog(owner, differences(checked), text("catalogUpdate.title"),
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (answer != 0) {
            service.discard();
            return;
        }
        Outcome installed = withProgress(service::install);
        switch (installed.status()) {
            case INSTALLED -> info(LanguageService.displayName("catalogUpdate.installed", installed.available()));
            case REJECTED -> rejected(installed);
            case FAILED -> error(text("catalogUpdate.installFailed") + "\n" + installed.reason());
            default -> {
                // CANCELLED: nothing changed.
            }
        }
    }

    /** The text of the differences dialog, names in the configured language. */
    private static String differences(Outcome checked) {
        CatalogUpdateService.Diff diff = checked.diff();
        String language = LanguageService.configuredLanguage();
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> kind : KINDS.entrySet()) {
            List<String> ids = diff.newIds().getOrDefault(kind.getKey(), List.of());
            if (!ids.isEmpty()) {
                parts.add(text(kind.getValue()) + ": "
                        + String.join(", ", ids.stream().map(id -> diff.name(id, language)).toList()));
            }
        }
        List<String> lines = new ArrayList<>();
        lines.add(LanguageService.displayName("catalogUpdate.diff.versions", checked.installed(), checked.available()));
        lines.add(parts.isEmpty() ? text("catalogUpdate.diff.nothingNew")
                : LanguageService.displayName("catalogUpdate.diff.new", String.join(" · ", parts)));
        lines.add(LanguageService.displayName("catalogUpdate.diff.changed", diff.changed()));
        lines.add("");
        lines.add(text("catalogUpdate.diff.question"));
        return String.join("\n", lines);
    }

    private void needsNewerProgram() {
        Object[] options = {text("catalogUpdate.openReleases"), text("catalogUpdate.close")};
        int answer = JOptionPane.showOptionDialog(owner, text("catalogUpdate.needsNewerProgram"),
                text("catalogUpdate.title"), JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
                options, options[0]);
        if (answer == 0) {
            openWeb.accept(GitHubProject.RELEASES_PAGE_URL);
        }
    }

    private void rejected(Outcome outcome) {
        List<String> lines = new ArrayList<>();
        lines.add(text("catalogUpdate.rejected"));
        outcome.problems().stream().limit(SHOWN_PROBLEMS).forEach(problem -> lines.add("· " + problem));
        if (outcome.problems().size() > SHOWN_PROBLEMS) {
            lines.add(LanguageService.displayName("catalogUpdate.moreProblems", outcome.problems().size() - SHOWN_PROBLEMS));
        }
        error(String.join("\n", lines));
    }

    // --- progress ---

    /**
     * Runs {@code step} on a background thread while a modal progress dialog with a Cancel
     * button is shown; returns its outcome. Closing the dialog cancels as well.
     */
    private Outcome withProgress(Function<CatalogUpdateService.Progress, Outcome> step) {
        JDialog dialog = new JDialog(owner, text("catalogUpdate.title"), Dialog.ModalityType.APPLICATION_MODAL);
        JLabel status = new JLabel(text("catalogUpdate.progress.manifest"));
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        JButton cancel = new JButton(text("catalogUpdate.cancel"));
        AtomicBoolean cancelled = new AtomicBoolean();
        cancel.addActionListener(event -> {
            cancelled.set(true);
            cancel.setEnabled(false);
        });
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cancelled.set(true);
                cancel.setEnabled(false);
            }
        });

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(status, BorderLayout.NORTH);
        content.add(bar, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.add(cancel);
        content.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.setSize(new Dimension(420, dialog.getPreferredSize().height + 20));
        dialog.setLocationRelativeTo(owner);

        CatalogUpdateService.Progress progress = new CatalogUpdateService.Progress() {
            @Override
            public void update(Phase phase, int done, int total) {
                SwingUtilities.invokeLater(() -> {
                    status.setText(progressText(phase, done, total));
                    boolean counted = phase == Phase.DATA_FILES || phase == Phase.IMAGES;
                    bar.setIndeterminate(!counted);
                    if (counted) {
                        bar.setMaximum(Math.max(1, total));
                        bar.setValue(done);
                    }
                });
            }

            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }
        };

        Outcome[] result = new Outcome[1];
        SwingWorker<Outcome, Void> worker = new SwingWorker<>() {
            @Override
            protected Outcome doInBackground() {
                return step.apply(progress);
            }

            @Override
            protected void done() {
                try {
                    result[0] = get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Logger.logException("Catalog update failed", e.getCause());
                }
                dialog.dispose();
            }
        };
        worker.execute();
        dialog.setVisible(true);
        if (result[0] == null) {
            service.discard();
            return new Outcome(CatalogUpdateService.Status.FAILED, "Unexpected error - see the log.",
                    null, null, null, null);
        }
        return result[0];
    }

    private static String progressText(Phase phase, int done, int total) {
        return switch (phase) {
            case MANIFEST -> text("catalogUpdate.progress.manifest");
            case DATA_FILES -> LanguageService.displayName("catalogUpdate.progress.dataFiles", done + 1, total);
            case IMAGES -> LanguageService.displayName("catalogUpdate.progress.images", done + 1, total);
            case CHECKING -> text("catalogUpdate.progress.checking");
            case INSTALLING -> text("catalogUpdate.progress.installing");
        };
    }

    // --- helpers ---

    private void info(String message) {
        JOptionPane.showMessageDialog(owner, message, text("catalogUpdate.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    private void error(String message) {
        JOptionPane.showMessageDialog(owner, message, text("catalogUpdate.title"), JOptionPane.ERROR_MESSAGE);
    }

    private static String text(String key) {
        return LanguageService.displayName(key);
    }

    private static Map<String, String> kinds() {
        Map<String, String> kinds = new LinkedHashMap<>();
        kinds.put(CatalogValidator.HEROES, "catalogUpdate.kind.heroes");
        kinds.put(CatalogValidator.TITANS, "catalogUpdate.kind.titans");
        kinds.put(CatalogValidator.PETS, "catalogUpdate.kind.pets");
        kinds.put(CatalogValidator.WAR_FLAGS, "catalogUpdate.kind.warFlags");
        kinds.put(CatalogValidator.FORTIFICATIONS, "catalogUpdate.kind.fortifications");
        return kinds;
    }
}
