package org.c2w.gui;

import org.c2w.data.model.Guild;
import org.c2w.i18n.LanguageService;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The content of {@link NewGuildDialog}: the guild name (at most {@link Guild#MAX_NAME_LENGTH}
 * characters - longer input, also pasted text, is cut off) with a "n / 20" counter, and the
 * "I am the guild master" check box (off by default). Knows no dialog, so it can be tested
 * headless.
 */
final class NewGuildForm extends JPanel {

    /** What the user entered - the name already trimmed. */
    record Input(String name, boolean guildMaster) {
    }

    private final JTextField nameField = new JTextField(20);
    private final JLabel counterLabel = new JLabel();
    private final JCheckBox guildMasterCheckBox =
            new JCheckBox(LanguageService.displayName("mainFrame.newGuild.guildMaster"));
    private final List<Runnable> changeListeners = new ArrayList<>();

    NewGuildForm() {
        super(new GridBagLayout());
        ((AbstractDocument) nameField.getDocument()).setDocumentFilter(new MaxLengthFilter(Guild.MAX_NAME_LENGTH));
        nameField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                nameChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                nameChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                nameChanged();
            }
        });

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        add(new JLabel(LanguageService.displayName("mainFrame.newGuild.prompt")), gbc);

        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(nameField, gbc);
        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        add(counterLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        add(guildMasterCheckBox, gbc);
        nameChanged();
    }

    /** Called whenever the name changes - e.g. to enable/disable "OK". */
    void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    /** The trimmed name and the check box. */
    Input input() {
        return new Input(nameField.getText().trim(), guildMasterCheckBox.isSelected());
    }

    /** True if "OK" may be pressed: the name is not empty. */
    boolean canConfirm() {
        return !input().name().isEmpty();
    }

    // --- package-visible for tests and the dialog ---

    JTextField nameField() {
        return nameField;
    }

    JCheckBox guildMasterCheckBox() {
        return guildMasterCheckBox;
    }

    JLabel counterLabel() {
        return counterLabel;
    }

    // --- private ---

    private void nameChanged() {
        counterLabel.setText(LanguageService.displayName("mainFrame.newGuild.nameCounter",
                nameField.getDocument().getLength(), Guild.MAX_NAME_LENGTH));
        List.copyOf(changeListeners).forEach(Runnable::run);
    }

    /** Lets the document hold at most {@code maxLength} characters - longer inserts are cut off. */
    static final class MaxLengthFilter extends DocumentFilter {

        private final int maxLength;

        MaxLengthFilter(int maxLength) {
            this.maxLength = maxLength;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String text, AttributeSet attrs)
                throws BadLocationException {
            replace(fb, offset, 0, text, attrs);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            String insert = text == null ? "" : text;
            int room = maxLength - (fb.getDocument().getLength() - length);
            if (insert.length() > room) {
                insert = insert.substring(0, Math.max(0, room));
            }
            super.replace(fb, offset, length, insert, attrs);
        }
    }
}
