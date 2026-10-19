package org.c2w.gui;

import org.c2w.data.model.BuffEffect;
import org.c2w.domain.HodorSettings;
import org.c2w.i18n.LanguageService;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.text.NumberFormat;
import java.util.EnumMap;
import java.util.Map;

/**
 * The "Algorithms" area of {@link SettingsDialog}: the default hero and titan algorithm (the combo
 * boxes are owned by the dialog, which saves them) and the values of the HODOR algorithms (see
 * {@link HodorSettings}) - the reserve and one weight per buff effect, with a "Defaults" button.
 * Styled like {@link CowScoreBonusesPanel}.
 */
final class AlgorithmSettingsPanel extends JPanel {

    private final SpinnerNumberModel reserveModel = new SpinnerNumberModel(HodorSettings.DEFAULT_RESERVE,
            HodorSettings.MIN_RESERVE, HodorSettings.MAX_RESERVE, 1);
    private final Map<BuffEffect, SpinnerNumberModel> weightModels = new EnumMap<>(BuffEffect.class);

    AlgorithmSettingsPanel(JComboBox<String> heroAlgorithmComboBox, JComboBox<String> titanAlgorithmComboBox,
                           HodorSettings initial) {
        super(new BorderLayout(0, 6));
        setOpaque(false);

        JLabel title = new JLabel(LanguageService.displayName("settings.algorithms.title"));
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setBorder(BorderFactory.createCompoundBorder(new MatteBorder(0, 0, 1, 0, Color.WHITE),
                BorderFactory.createEmptyBorder(0, 0, 4, 0)));
        add(title, BorderLayout.NORTH);

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        int row = 0;
        heroAlgorithmComboBox.setToolTipText(LanguageService.displayName("settings.algorithms.defaultAlgorithm.tooltip"));
        titanAlgorithmComboBox.setToolTipText(LanguageService.displayName("settings.algorithms.defaultAlgorithm.tooltip"));
        addRow(content, row++, new JLabel(LanguageService.displayName("settingsDialog.defaultHeroAlgorithm")),
                heroAlgorithmComboBox);
        addRow(content, row++, new JLabel(LanguageService.displayName("settingsDialog.defaultTitanAlgorithm")),
                titanAlgorithmComboBox);

        JLabel hodorTitle = new JLabel(LanguageService.displayName("settings.algorithms.hodor"));
        hodorTitle.setFont(hodorTitle.getFont().deriveFont(Font.BOLD));
        addFullWidth(content, row++, hodorTitle, new Insets(12, 0, 2, 0));
        JLabel hint = new JLabel("<html>" + LanguageService.displayName("settings.algorithms.hodorHint") + "</html>");
        addFullWidth(content, row++, hint, new Insets(0, 0, 6, 0));

        JSpinner reserveSpinner = new JSpinner(reserveModel);
        reserveSpinner.setToolTipText(LanguageService.displayName("settings.algorithms.reserve.tooltip",
                HodorSettings.MIN_RESERVE, HodorSettings.MAX_RESERVE));
        addRow(content, row++, new JLabel(LanguageService.displayName("settings.algorithms.reserve")),
                reserveSpinner);

        JLabel weightsTitle = new JLabel(LanguageService.displayName("settings.algorithms.weights"));
        weightsTitle.setFont(weightsTitle.getFont().deriveFont(Font.BOLD));
        addFullWidth(content, row++, weightsTitle, new Insets(8, 0, 2, 0));
        for (BuffEffect effect : BuffEffect.values()) {
            SpinnerNumberModel model = new SpinnerNumberModel(HodorSettings.defaultWeight(effect),
                    HodorSettings.MIN_WEIGHT, HodorSettings.MAX_WEIGHT, HodorSettings.STEP);
            weightModels.put(effect, model);
            JSpinner spinner = new JSpinner(model);
            spinner.setLocale(LanguageService.locale());
            spinner.setEditor(new JSpinner.NumberEditor(spinner, "0.00"));
            String effectName = LanguageService.displayName("buffEffect." + effect.name());
            spinner.setToolTipText(LanguageService.displayName("settings.algorithms.weight.tooltip", effectName,
                    number(HodorSettings.MIN_WEIGHT), number(HodorSettings.MAX_WEIGHT)));
            addRow(content, row++, new JLabel(effectName), spinner);
        }
        // Takes the extra width and height, so the rows stay together at the top left.
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 2;
        filler.gridy = row;
        filler.weightx = 1;
        filler.weighty = 1;
        content.add(Box.createGlue(), filler);
        add(content, BorderLayout.CENTER);

        JButton defaultsButton = new JButton(LanguageService.displayName("settings.algorithms.defaults"));
        defaultsButton.setToolTipText(LanguageService.displayName("settings.algorithms.defaults.tooltip"));
        defaultsButton.addActionListener(e -> resetToDefaults());
        JPanel bottom = new JPanel(new BorderLayout(12, 0));
        bottom.setOpaque(false);
        bottom.add(defaultsButton, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        setValues(initial.normalized());
    }

    /** The values of the spinners, normalized (see {@link HodorSettings#normalized()}). */
    HodorSettings hodorSettings() {
        Map<BuffEffect, Double> weights = new EnumMap<>(BuffEffect.class);
        weightModels.forEach((effect, model) -> weights.put(effect, model.getNumber().doubleValue()));
        return new HodorSettings(reserveModel.getNumber().intValue(), weights).normalized();
    }

    /** Reserve and weights back to {@link HodorSettings#DEFAULTS} - saved only with the dialog. */
    void resetToDefaults() {
        setValues(HodorSettings.DEFAULTS);
    }

    // --- package-visible for tests ---

    SpinnerNumberModel reserveModel() {
        return reserveModel;
    }

    SpinnerNumberModel weightModel(BuffEffect effect) {
        return weightModels.get(effect);
    }

    // --- private ---

    private void setValues(HodorSettings settings) {
        reserveModel.setValue(settings.reserve());
        weightModels.forEach((effect, model) -> model.setValue(settings.weight(effect)));
    }

    /** Label in the first column, {@code component} in the second. */
    private static void addRow(JPanel panel, int row, JLabel label, JComponent component) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 0, 2, 6);
        gbc.gridx = 0;
        panel.add(label, gbc);
        gbc.gridx = 1;
        panel.add(component, gbc);
    }

    private static void addFullWidth(JPanel panel, int row, JComponent component, Insets insets) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = insets;
        panel.add(component, gbc);
    }

    private static String number(double value) {
        NumberFormat format = NumberFormat.getNumberInstance(LanguageService.locale());
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }
}
