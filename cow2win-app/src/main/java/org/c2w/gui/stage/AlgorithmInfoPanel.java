package org.c2w.gui.stage;

import org.c2w.data.model.BuffEffect;
import org.c2w.domain.HodorSettings;
import org.c2w.eval.AlgorithmDescriptions;
import org.c2w.eval.AlgorithmSetting;
import org.c2w.eval.HodorAlgorithm;
import org.c2w.eval.LineupAlgorithm;
import org.c2w.gui.common.AppColors;
import org.c2w.i18n.LanguageService;

import javax.swing.*;
import java.awt.*;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The content of {@link AlgorithmInfoDialog}: the name and the full description of an algorithm
 * (wrapped, selectable) and, read-only, the current values of the settings it uses (see {@link
 * LineupAlgorithm#usedSettings()}) with a hint where to change them.
 */
final class AlgorithmInfoPanel extends JPanel {

    /** Width the description wraps at. */
    static final int TEXT_WIDTH = 420;

    private final Supplier<HodorSettings> settings;
    private LineupAlgorithm algorithm;

    AlgorithmInfoPanel() {
        this(HodorAlgorithm::settings);
    }

    /** With its own settings - for tests. */
    AlgorithmInfoPanel(Supplier<HodorSettings> settings) {
        this.settings = settings;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(12, 14, 8, 14));
    }

    /** Shows {@code algorithm} with the current values of its settings. */
    void show(LineupAlgorithm algorithm) {
        this.algorithm = algorithm;
        removeAll();
        add(InfoSections.titleLabel(algorithm.localizedName()));
        add(Box.createVerticalStrut(8));
        add(textArea(AlgorithmDescriptions.forAlgorithm(algorithm)));

        JLabel settingsHeading = new JLabel(LanguageService.displayName("algorithmInfo.settings"));
        settingsHeading.setFont(settingsHeading.getFont().deriveFont(Font.BOLD));
        settingsHeading.setBorder(BorderFactory.createEmptyBorder(14, 0, 6, 0));
        add(settingsHeading);
        addSettings(algorithm.usedSettings(), settings.get().normalized());
        for (Component child : getComponents()) {
            ((JComponent) child).setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        revalidate();
        repaint();
    }

    /** The algorithm shown, null before the first {@link #show}. */
    LineupAlgorithm algorithm() {
        return algorithm;
    }

    /** Every label and text area text, in display order - for tests. */
    List<String> texts() {
        List<String> texts = new ArrayList<>();
        collectTexts(this, texts);
        return texts;
    }

    // --- private ---

    private void addSettings(Set<AlgorithmSetting> used, HodorSettings values) {
        if (used.isEmpty()) {
            add(muted(LanguageService.displayName("algorithmInfo.noSettings")));
            return;
        }
        JPanel table = new JPanel(new GridBagLayout());
        table.setOpaque(false);
        int row = 0;
        if (used.contains(AlgorithmSetting.RESERVE)) {
            addValueRow(table, row++, LanguageService.displayName("settings.algorithms.reserve"),
                    String.valueOf(values.reserve()), 0);
        }
        if (used.contains(AlgorithmSetting.BUFF_WEIGHTS)) {
            JLabel weights = new JLabel(LanguageService.displayName("settings.algorithms.weights"));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(row == 0 ? 0 : 6, 0, 2, 0);
            gbc.gridy = row++;
            gbc.gridwidth = 2;
            gbc.anchor = GridBagConstraints.WEST;
            table.add(weights, gbc);
            for (BuffEffect effect : BuffEffect.values()) {
                addValueRow(table, row++, LanguageService.displayName("buffEffect." + effect.name()),
                        weight(values.weight(effect)), 14);
            }
        }
        add(table);
        if (!used.contains(AlgorithmSetting.RESERVE)) {
            add(hint(LanguageService.displayName("algorithmInfo.noReserve")));
        }
        if (used.contains(AlgorithmSetting.COW_SCORE_BONUSES)) {
            add(hint(LanguageService.displayName("algorithmInfo.cowScoreBonuses")));
        }
        add(hint(LanguageService.displayName("algorithmInfo.changeHint")));
    }

    /** Name in the first column (indented by {@code indent}), the value right-aligned in the second. */
    private static void addValueRow(JPanel table, int row, String name, String value, int indent) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(1, indent, 1, 16);
        gbc.gridx = 0;
        table.add(new JLabel(name), gbc);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(1, 0, 1, 0);
        table.add(new JLabel(value), gbc);
    }

    /** {@code text} wrapped at {@link #TEXT_WIDTH}, not editable but selectable and copyable. */
    private static JTextArea textArea(String text) {
        JTextArea area = new JTextArea(text);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setOpaque(false);
        // The look and feel sets text areas in bold - the description reads like a label.
        area.setFont(UIManager.getFont("Label.font"));
        area.setBorder(BorderFactory.createEmptyBorder());
        // Known width, so the preferred height is that of the wrapped text.
        area.setSize(TEXT_WIDTH, Short.MAX_VALUE);
        Dimension size = new Dimension(TEXT_WIDTH, area.getPreferredSize().height);
        area.setPreferredSize(size);
        area.setMaximumSize(size);
        return area;
    }

    private static JComponent hint(String text) {
        JTextArea area = textArea(text);
        area.setForeground(AppColors.MUTED);
        area.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        Dimension size = area.getPreferredSize();
        size.height += 6;
        area.setPreferredSize(size);
        area.setMaximumSize(size);
        return area;
    }

    private static JComponent muted(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(AppColors.MUTED);
        return label;
    }

    private static String weight(double value) {
        NumberFormat format = NumberFormat.getNumberInstance(LanguageService.locale());
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }

    private static void collectTexts(Container container, List<String> texts) {
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel label && label.getText() != null) {
                texts.add(label.getText());
            } else if (child instanceof JTextArea area) {
                texts.add(area.getText());
            }
            if (child instanceof Container nested) {
                collectTexts(nested, texts);
            }
        }
    }
}
