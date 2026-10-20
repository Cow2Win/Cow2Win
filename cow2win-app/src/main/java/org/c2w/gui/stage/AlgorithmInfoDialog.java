package org.c2w.gui.stage;

import org.c2w.i18n.LanguageService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * The explanation of the strategic concept's chosen algorithm (the "i" next to its combo box):
 * not modal, so it stays open while working; shows an {@link AlgorithmInfoPanel} owned by the
 * view, which keeps it following the chosen algorithm. "Close" and Esc hide it.
 */
final class AlgorithmInfoDialog extends JDialog {

    private final AlgorithmInfoPanel content;

    AlgorithmInfoDialog(Window owner, AlgorithmInfoPanel content) {
        super(owner, ModalityType.MODELESS);
        this.content = content;
        setDefaultCloseOperation(HIDE_ON_CLOSE);

        JButton close = new JButton(LanguageService.displayName("common.close"));
        close.addActionListener(e -> setVisible(false));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(close);

        JPanel root = new JPanel(new BorderLayout());
        root.add(content, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(close);
        getRootPane().registerKeyboardAction(e -> setVisible(false),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        refit();
        setLocationRelativeTo(owner);
    }

    /** Title and size after the content changed (another algorithm, current settings). */
    void refit() {
        if (content.algorithm() != null) {
            setTitle(content.algorithm().localizedName());
        }
        pack();
    }
}
