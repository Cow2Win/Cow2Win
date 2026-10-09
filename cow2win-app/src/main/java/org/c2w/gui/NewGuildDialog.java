package org.c2w.gui;

import org.c2w.data.model.Guild;
import org.c2w.i18n.LanguageService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The modal "New guild" dialog: name (see {@link NewGuildForm}) and "I am the guild master".
 * Enter = OK, Escape = cancel; OK is only enabled while the name is not empty. On OK the name
 * is checked (see {@link #problem}) - if it is not acceptable, a message is shown and the dialog
 * stays open with the input.
 */
final class NewGuildDialog extends JDialog {

    private final NewGuildForm form = new NewGuildForm();
    private final Predicate<String> guildExists;
    private NewGuildForm.Input result;

    private NewGuildDialog(Window owner, Predicate<String> guildExists) {
        super(owner, LanguageService.displayName("mainFrame.newGuild.title"), ModalityType.APPLICATION_MODAL);
        this.guildExists = guildExists;

        JButton okButton = new JButton(LanguageService.displayName("mainFrame.newGuild.ok"));
        JButton cancelButton = new JButton(LanguageService.displayName("mainFrame.newGuild.cancel"));
        okButton.addActionListener(e -> onOk());
        cancelButton.addActionListener(e -> dispose());
        form.addChangeListener(() -> okButton.setEnabled(form.canConfirm()));
        okButton.setEnabled(form.canConfirm());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(okButton);
        buttons.add(cancelButton);
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        content.add(form, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);

        getRootPane().setDefaultButton(okButton);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /**
     * Shows the dialog and waits until it is closed. Returns the accepted input, or empty if the
     * user cancelled.
     *
     * @param guildExists whether a guild folder with the given name already exists
     */
    static Optional<NewGuildForm.Input> show(Component owner, Predicate<String> guildExists) {
        Window window = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
        if (owner instanceof Window ownerWindow) {
            window = ownerWindow;
        }
        NewGuildDialog dialog = new NewGuildDialog(window, guildExists);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }

    /**
     * Why {@code name} (already trimmed) cannot be the name of a new guild, as a message in the
     * UI language - null if it can: empty, longer than {@link Guild#MAX_NAME_LENGTH} (in case the
     * field's limit was bypassed), characters not allowed in a folder name, or a guild folder of
     * that name already exists.
     */
    static String problem(String name, Predicate<String> guildExists) {
        if (name.isEmpty()) {
            return LanguageService.displayName("common.enterName");
        }
        if (name.length() > Guild.MAX_NAME_LENGTH) {
            return LanguageService.displayName("mainFrame.newGuild.nameTooLong", Guild.MAX_NAME_LENGTH);
        }
        if (ActionBar.containsIllegalFilenameChar(name)) {
            return LanguageService.displayName("common.illegalFilenameChars", ActionBar.ILLEGAL_FILENAME_CHARS);
        }
        if (guildExists.test(name)) {
            return LanguageService.displayName("mainFrame.newGuild.alreadyExists", name);
        }
        return null;
    }

    private void onOk() {
        NewGuildForm.Input input = form.input();
        String problem = problem(input.name(), guildExists);
        if (problem != null) {
            JOptionPane.showMessageDialog(this, problem, getTitle(), JOptionPane.WARNING_MESSAGE);
            form.nameField().requestFocusInWindow();
            return;
        }
        result = input;
        dispose();
    }
}
