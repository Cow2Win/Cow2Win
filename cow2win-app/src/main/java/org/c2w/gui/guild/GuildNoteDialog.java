package org.c2w.gui.guild;

import org.c2w.data.model.Guild;
import org.c2w.data.repository.GuildNoteRepository;
import org.c2w.gui.common.AppColors;
import org.c2w.gui.common.FlatButton;
import org.c2w.gui.common.IconLoader;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Logger;
import org.c2w.service.AppContext;
import org.c2w.service.GuildLog;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.IntSupplier;

/**
 * Edits the free text note of the open guild ({@link GuildNoteRepository}, {@code note.txt} in the
 * guild folder). Non-modal, and only one instance exists at a time (see {@link #open}). The note
 * has its own unsaved state, independent of the guild's and the lineup's ({@link AppContext#isGuildDirty()}):
 * the save button turns red while the text differs from the last loaded/saved one.
 *
 * <p>Follows the open guild: a guild switch asks first via {@link #confirmLeave} (called by
 * {@code ContextBar#confirmDiscardUnsavedChanges()} and on exit), then the dialog shows the new
 * guild's note. It closes itself when the new guild has no folder or the open guild was deleted.
 */
public class GuildNoteDialog extends JDialog {

    private static final String KEY_TITLE = "guildNote.title";
    private static final String KEY_SAVE = "guildNote.save";
    private static final String KEY_UNSAVED_QUESTION = "guildNote.unsavedQuestion";
    private static final String KEY_LOAD_ERROR = "guildNote.loadError";
    private static final String KEY_UNSAVED_SUFFIX = "toolbar.unsavedSuffix";
    private static final String KEY_CLOSE = "common.close";
    private static final String KEY_SAVE_ERROR = "common.saveError";
    private static final String KEY_SAVE_ERROR_TITLE = "common.saveErrorTitle";
    private static final String KEY_LOG_SAVED = "guildLog.guildNoteSaved";
    private static final String KEY_LOG_CLEARED = "guildLog.guildNoteCleared";
    private static final String ICON_SAVE = "/images/app/save.png";
    /** Size of the save icon - the main window's toolbar icon size. */
    private static final int ICON_SIZE = 20;

    private static final int WIDTH = 600;
    private static final int HEIGHT = 450;

    /** The open dialog, or null - see {@link #open}. */
    private static GuildNoteDialog instance;

    private final AppContext appContext;
    private final JTextArea textArea = new JTextArea();
    private final FlatButton saveButton = new FlatButton(IconLoader.iconFor(ICON_SAVE, ICON_SIZE, AppColors.ACCENT));

    /** Folder of the guild whose note is shown. */
    private Path guildDir;
    /** The note as last loaded or saved - the text area differs from it while there are unsaved changes. */
    private String savedText = "";

    private final AppContext.Listener contextListener = new AppContext.Listener() {
        @Override
        public void guildChanged() {
            onGuildChanged();
        }
    };

    /**
     * Brings the open dialog to front, or creates and shows it if none is open. Does nothing (and
     * returns null) if the open guild has no folder.
     */
    public static GuildNoteDialog open(Frame owner, AppContext appContext) {
        if (instance == null) {
            Path dir = guildDirOf(appContext);
            if (dir == null) {
                return null;
            }
            instance = new GuildNoteDialog(owner, appContext, dir);
        }
        instance.setVisible(true);
        instance.toFront();
        instance.textArea.requestFocusInWindow();
        return instance;
    }

    /** The open dialog, if any. */
    public static GuildNoteDialog openInstance() {
        return instance;
    }

    /**
     * True if the open guild may be left (switched, removed, the application closed): no note
     * dialog is open, its note is saved, or the user saves or discards its changes when asked. False
     * if the user cancels or saving fails.
     */
    public static boolean confirmLeave(Component parent) {
        return confirmLeave(() -> JOptionPane.showConfirmDialog(parent,
                LanguageService.displayName(KEY_UNSAVED_QUESTION),
                LanguageService.displayName("common.unsavedChangesTitle"),
                JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE));
    }

    /** {@link #confirmLeave(Component)} with the user's answer (a {@link JOptionPane} option) from {@code askUser}. */
    static boolean confirmLeave(IntSupplier askUser) {
        return instance == null || instance.confirmDiscard(askUser);
    }

    /** The folder of the open guild, or null if it has none (yet). */
    public static Path guildDirOf(AppContext appContext) {
        Path dir = GuildLog.dirOf(appContext.guildFilePath());
        return dir != null && Files.isDirectory(dir) ? dir : null;
    }

    private GuildNoteDialog(Frame owner, AppContext appContext, Path guildDir) {
        super(owner, "", false);
        this.appContext = appContext;
        this.guildDir = guildDir;
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onClose();
            }
        });

        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        Font textFieldFont = UIManager.getFont("TextField.font");
        if (textFieldFont != null) {
            textArea.setFont(textFieldFont);
        }
        textArea.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateSaveButton();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateSaveButton();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateSaveButton();
            }
        });
        JScrollPane scrollPane = new JScrollPane(textArea,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        saveButton.addActionListener(e -> save());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        top.add(saveButton);

        JButton closeButton = new JButton(LanguageService.displayName(KEY_CLOSE));
        closeButton.addActionListener(e -> onClose());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(closeButton);

        JPanel content = new JPanel(new BorderLayout(0, 4));
        content.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        content.add(top, BorderLayout.NORTH);
        content.add(scrollPane, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);
        setContentPane(content);

        // Ctrl+S saves, Esc closes (with the usual question). No default button: Enter stays a line break.
        JRootPane root = getRootPane();
        root.registerKeyboardAction(e -> save(), KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        root.registerKeyboardAction(e -> onClose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        load();
        appContext.addListener(contextListener);

        setSize(WIDTH, HEIGHT);
        setLocationRelativeTo(owner);
    }

    @Override
    public void dispose() {
        appContext.removeListener(contextListener);
        if (instance == this) {
            instance = null;
        }
        super.dispose();
    }

    // --- state ---

    /** True while the text differs from the last loaded or saved note (line breaks normalized). */
    public boolean hasUnsavedChanges() {
        return !GuildNoteRepository.normalize(textArea.getText()).equals(savedText);
    }

    /** Folder of the guild whose note is shown - for tests. */
    Path guildDir() {
        return guildDir;
    }

    /** The text area - for tests. */
    JTextArea textArea() {
        return textArea;
    }

    /** The save button - for tests. */
    AbstractButton saveButton() {
        return saveButton;
    }

    /** Reads the note of {@link #guildDir} into the text area (empty if it cannot be read) and adapts the title. */
    private void load() {
        String text;
        try {
            text = GuildNoteRepository.read(guildDir);
        } catch (IOException e) {
            Logger.logException("Could not read the guild note of " + guildDir, e);
            JOptionPane.showMessageDialog(getOwner(), LanguageService.displayName(KEY_LOAD_ERROR) + "\n" + e.getMessage(),
                    LanguageService.displayName("common.loadGuildErrorTitle"), JOptionPane.ERROR_MESSAGE);
            text = "";
        }
        savedText = text;
        textArea.setText(text);
        textArea.setCaretPosition(text.length());
        updateTitle();
        updateSaveButton();
    }

    private void updateTitle() {
        Guild guild = appContext.guild();
        String name = guild != null && guild.name() != null ? guild.name() : guildDir.getFileName().toString();
        setTitle(LanguageService.displayName(KEY_TITLE, name));
    }

    /** Colors the save icon {@link AppColors#ERROR} and extends its tooltip while there are unsaved changes. */
    private void updateSaveButton() {
        boolean dirty = hasUnsavedChanges();
        saveButton.setIcon(IconLoader.iconFor(ICON_SAVE, ICON_SIZE, dirty ? AppColors.ERROR : AppColors.ACCENT));
        String tooltip = LanguageService.displayName(KEY_SAVE);
        saveButton.setToolTipText(dirty ? tooltip + " – " + LanguageService.displayName(KEY_UNSAVED_SUFFIX) : tooltip);
    }

    // --- actions ---

    /**
     * Writes the note (an empty one deletes the file) and logs it in the guild log. On failure an
     * error message is shown and the text stays unsaved.
     *
     * @return true if the note was saved
     */
    boolean save() {
        String text = GuildNoteRepository.normalize(textArea.getText());
        try {
            GuildNoteRepository.write(guildDir, text);
        } catch (IOException e) {
            Logger.logException("Could not save the guild note of " + guildDir, e);
            JOptionPane.showMessageDialog(this, LanguageService.displayName(KEY_SAVE_ERROR) + "\n" + e.getMessage(),
                    LanguageService.displayName(KEY_SAVE_ERROR_TITLE), JOptionPane.ERROR_MESSAGE);
            return false;
        }
        savedText = text;
        GuildLog.event(guildDir, text.isBlank() ? KEY_LOG_CLEARED : KEY_LOG_SAVED);
        updateSaveButton();
        return true;
    }

    /**
     * True if the note may be left: nothing unsaved, or {@code askUser} answers "save" (and saving
     * works) or "discard". Cancelling or closing the question returns false.
     */
    private boolean confirmDiscard(IntSupplier askUser) {
        if (!hasUnsavedChanges()) {
            return true;
        }
        int choice = askUser.getAsInt();
        if (choice == JOptionPane.YES_OPTION) {
            return save();
        }
        if (choice == JOptionPane.NO_OPTION) {
            Logger.log("Guild note of " + guildDir + " - unsaved changes discarded");
            return true;
        }
        return false;
    }

    /** "Close", Esc and the window's "X": asks save / discard / cancel while there are unsaved changes. */
    private void onClose() {
        if (confirmLeave(this)) {
            dispose();
        }
    }

    /**
     * Follows the open guild: the same guild (saved, reloaded) keeps the text and only adapts the
     * title; another guild shows its note - the question about unsaved changes came before, see
     * {@link #confirmLeave}. Closes without a question if the shown guild was deleted or the new one
     * has no folder.
     */
    private void onGuildChanged() {
        Path newDir = guildDirOf(appContext);
        if (newDir != null && GuildLog.sameDir(newDir, guildDir)) {
            updateTitle();
            return;
        }
        if (newDir == null || !Files.isDirectory(guildDir)) {
            dispose();
            return;
        }
        guildDir = newDir;
        load();
    }
}
