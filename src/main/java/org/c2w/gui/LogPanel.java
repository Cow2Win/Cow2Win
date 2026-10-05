package org.c2w.gui;

import org.c2w.i18n.LanguageService;
import org.c2w.infra.Logger;
import org.c2w.service.AppContext;
import org.c2w.service.GuildLog;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Shows the guild log ({@link GuildLog}) of the open guild - including entries of
 * earlier sessions - read-only, but selectable and copyable. Follows the open
 * guild: on every guild change the file of the new guild is read in the
 * background; new events of the open guild show up right away, events of other
 * guilds never. Without a guild log a muted hint is shown instead.
 */
public class LogPanel extends JPanel {

    /** Preferred height of the scrollable log area (width is stretched to fill {@link java.awt.BorderLayout#SOUTH}). */
    private static final int PREFERRED_HEIGHT = 140;

    private static final String CARD_TEXT = "text";
    private static final String CARD_EMPTY = "empty";

    private final AppContext appContext;
    private final JTextArea textArea = new JTextArea();
    private final JLabel emptyLabel = new JLabel(LanguageService.displayName("logPanel.empty"), SwingConstants.CENTER);
    private final CardLayout cards = new CardLayout();

    /** The guild folder whose log is shown (or being loaded), null before the first load. */
    private Path shownGuildDir;
    /** Counts the loads, so only the result of the latest one is shown. */
    private int loadGeneration;

    public LogPanel(AppContext appContext) {
        if (appContext == null) {
            throw new IllegalArgumentException("LogPanel needs an appContext");
        }
        this.appContext = appContext;
        setLayout(cards);
        textArea.setEditable(false);
        textArea.setLineWrap(false);

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(0, PREFERRED_HEIGHT));
        add(scrollPane, CARD_TEXT);

        Color muted = UIManager.getColor("Label.disabledForeground");
        emptyLabel.setForeground(muted == null ? Color.GRAY : muted);
        JPanel emptyPanel = new JPanel(new BorderLayout());
        emptyPanel.add(emptyLabel, BorderLayout.CENTER);
        add(emptyPanel, CARD_EMPTY);

        appContext.addListener(new AppContext.Listener() {
            @Override
            public void guildChanged() {
                onEdt(LogPanel.this::reloadIfGuildChanged);
            }
        });
        GuildLog.addListener((guildDir, line) -> onEdt(() -> {
            if (GuildLog.sameDir(guildDir, shownGuildDir)) {
                reload();
            }
        }));
        reload();
    }

    /** Folder of the open guild, null if none is open. */
    private Path currentGuildDir() {
        return GuildLog.dirOf(appContext.guildFilePath());
    }

    private void reloadIfGuildChanged() {
        if (!GuildLog.sameDir(currentGuildDir(), shownGuildDir)) {
            reload();
        }
    }

    /**
     * Reads the guild log of the open guild in the background and shows it,
     * scrolled to the end. Re-reading the file (instead of appending single
     * lines) keeps the panel exactly in line with the file, also when an event
     * arrives while a load is running.
     */
    private void reload() {
        Path guildDir = currentGuildDir();
        shownGuildDir = guildDir;
        int generation = ++loadGeneration;
        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() {
                return GuildLog.read(guildDir);
            }

            @Override
            protected void done() {
                if (generation != loadGeneration) {
                    return;
                }
                try {
                    show(get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Logger.logException("Could not show the guild log of " + guildDir, e.getCause());
                    show(List.of());
                }
            }
        }.execute();
    }

    private void show(List<String> lines) {
        if (lines.isEmpty()) {
            textArea.setText("");
            cards.show(this, CARD_EMPTY);
            return;
        }
        textArea.setText(String.join(System.lineSeparator(), lines) + System.lineSeparator());
        textArea.setCaretPosition(textArea.getDocument().getLength());
        cards.show(this, CARD_TEXT);
    }

    private static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }
}
