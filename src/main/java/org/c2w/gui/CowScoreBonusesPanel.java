package org.c2w.gui;

import org.c2w.domain.CowScoreBonuses;
import org.c2w.gui.journal.JournalTexts;
import org.c2w.i18n.LanguageService;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.text.NumberFormat;

/**
 * The "CowScore bonuses" area of {@link SettingsDialog}: one spinner per adjustable percentage of
 * {@link CowScoreBonuses}, heroes on the left, titans on the right. The relation applies to both
 * sides - its two spinners share one model. Each buff is the biggest bonus of its side: raising a
 * small bonus above it raises the buff too (with a short hint), and the buff's minimum is the
 * biggest small bonus of its side. Lowering a small bonus leaves the buff where it is.
 */
final class CowScoreBonusesPanel extends JPanel {

    private final SpinnerNumberModel roleModel = buffModel();
    private final SpinnerNumberModel elementModel = buffModel();
    private final SpinnerNumberModel relationModel = bonusModel();
    private final SpinnerNumberModel petModel = bonusModel();
    private final SpinnerNumberModel warFlagModel = bonusModel();
    private final SpinnerNumberModel comboModel = bonusModel();
    private final SpinnerNumberModel totemModel = bonusModel();
    private final JLabel roleRaisedHint = raisedHint();
    private final JLabel elementRaisedHint = raisedHint();
    /** True while a buff is raised by this panel - so that change does not hide the hint again. */
    private boolean raising;

    CowScoreBonusesPanel(CowScoreBonuses initial) {
        super(new BorderLayout(0, 6));
        setOpaque(false);

        JLabel title = new JLabel(LanguageService.displayName("settings.cowScore.title"));
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setBorder(BorderFactory.createCompoundBorder(new MatteBorder(0, 0, 1, 0, Color.WHITE),
                BorderFactory.createEmptyBorder(0, 0, 4, 0)));
        add(title, BorderLayout.NORTH);

        JPanel columns = new JPanel(new GridLayout(1, 2, 24, 0));
        columns.setOpaque(false);
        JPanel heroes = column("settings.cowScore.heroes");
        addSpinnerRow(heroes, 1, "settings.cowScore.role", roleModel, true, roleRaisedHint);
        addSpinnerRow(heroes, 2, "settings.cowScore.relation", relationModel, false, null);
        addSpinnerRow(heroes, 3, "settings.cowScore.pet", petModel, false, null);
        addSpinnerRow(heroes, 4, "settings.cowScore.warFlag", warFlagModel, false, null);
        addSpinnerRow(heroes, 5, "settings.cowScore.combo", comboModel, false, null);
        JPanel titans = column("settings.cowScore.titans");
        addSpinnerRow(titans, 1, "settings.cowScore.element", elementModel, true, elementRaisedHint);
        addSpinnerRow(titans, 2, "settings.cowScore.relation", relationModel, false, null);
        addSpinnerRow(titans, 3, "settings.cowScore.totem", totemModel, false, null);
        columns.add(heroes);
        columns.add(titans);
        add(columns, BorderLayout.CENTER);

        JButton defaultsButton = new JButton(LanguageService.displayName("settings.cowScore.defaults"));
        defaultsButton.addActionListener(e -> resetToDefaults());
        JLabel hint = new JLabel(LanguageService.displayName("settings.cowScore.hint"));
        JPanel bottom = new JPanel(new BorderLayout(12, 0));
        bottom.setOpaque(false);
        bottom.add(hint, BorderLayout.CENTER);
        bottom.add(defaultsButton, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        setValues(initial.normalized());
        relationModel.addChangeListener(e -> raiseBuffs());
        petModel.addChangeListener(e -> raiseBuffs());
        warFlagModel.addChangeListener(e -> raiseBuffs());
        comboModel.addChangeListener(e -> raiseBuffs());
        totemModel.addChangeListener(e -> raiseBuffs());
        roleModel.addChangeListener(e -> buffChanged(roleRaisedHint));
        elementModel.addChangeListener(e -> buffChanged(elementRaisedHint));
    }

    /** The values of the spinners, normalized (see {@link CowScoreBonuses#normalized()}). */
    CowScoreBonuses bonuses() {
        return new CowScoreBonuses(value(roleModel), value(elementModel), value(relationModel), value(petModel),
                value(warFlagModel), value(comboModel), value(totemModel)).normalized();
    }

    /** All seven spinners back to {@link CowScoreBonuses#DEFAULTS} - saved only with the dialog. */
    void resetToDefaults() {
        setValues(CowScoreBonuses.DEFAULTS);
        roleRaisedHint.setVisible(false);
        elementRaisedHint.setVisible(false);
    }

    // --- package-visible for tests ---

    SpinnerNumberModel roleModel() {
        return roleModel;
    }

    SpinnerNumberModel elementModel() {
        return elementModel;
    }

    SpinnerNumberModel relationModel() {
        return relationModel;
    }

    SpinnerNumberModel comboModel() {
        return comboModel;
    }

    SpinnerNumberModel totemModel() {
        return totemModel;
    }

    JLabel roleRaisedHint() {
        return roleRaisedHint;
    }

    // --- private ---

    /** Small bonuses first, then the buffs - so a buff is never below a small bonus in between. */
    private void setValues(CowScoreBonuses b) {
        raising = true;
        try {
            relationModel.setValue(b.relationPercent());
            petModel.setValue(b.petPercent());
            warFlagModel.setValue(b.warFlagPercent());
            comboModel.setValue(b.comboPercent());
            totemModel.setValue(b.totemPercent());
            updateBuffMinimums();
            roleModel.setValue(b.rolePercent());
            elementModel.setValue(b.elementPercent());
        } finally {
            raising = false;
        }
    }

    /** After a small bonus changed: raises each buff that is now below a small bonus of its side. */
    private void raiseBuffs() {
        double heroMaximum = Math.max(Math.max(value(relationModel), value(petModel)),
                Math.max(value(warFlagModel), value(comboModel)));
        double titanMaximum = Math.max(value(relationModel), value(totemModel));
        raising = true;
        try {
            if (value(roleModel) < heroMaximum) {
                roleModel.setValue(heroMaximum);
                roleRaisedHint.setVisible(true);
            }
            if (value(elementModel) < titanMaximum) {
                elementModel.setValue(titanMaximum);
                elementRaisedHint.setVisible(true);
            }
            updateBuffMinimums();
        } finally {
            raising = false;
        }
    }

    /** The buff's minimum is the biggest small bonus of its side. */
    private void updateBuffMinimums() {
        double heroMaximum = Math.max(Math.max(value(relationModel), value(petModel)),
                Math.max(value(warFlagModel), value(comboModel)));
        double titanMaximum = Math.max(value(relationModel), value(totemModel));
        roleModel.setMinimum(Math.max(CowScoreBonuses.MIN_BUFF, heroMaximum));
        elementModel.setMinimum(Math.max(CowScoreBonuses.MIN_BUFF, titanMaximum));
    }

    /** The user changed a buff - the "raised" hint no longer applies. */
    private void buffChanged(JLabel hint) {
        if (!raising) {
            hint.setVisible(false);
        }
    }

    /** The value rounded to two decimals - spinner steps of 0.05 leave tiny binary remainders. */
    private static double value(SpinnerNumberModel model) {
        return Math.round(model.getNumber().doubleValue() * 100) / 100.0;
    }

    private static SpinnerNumberModel buffModel() {
        return new SpinnerNumberModel(CowScoreBonuses.DEFAULT_BUFF, CowScoreBonuses.MIN_BUFF, CowScoreBonuses.MAX_BUFF,
                CowScoreBonuses.STEP);
    }

    private static SpinnerNumberModel bonusModel() {
        return new SpinnerNumberModel(CowScoreBonuses.DEFAULT_BONUS, CowScoreBonuses.MIN_BONUS,
                CowScoreBonuses.MAX_BONUS, CowScoreBonuses.STEP);
    }

    private static JLabel raisedHint() {
        JLabel hint = new JLabel(LanguageService.displayName("settings.cowScore.buffRaised"));
        hint.setFont(hint.getFont().deriveFont(Font.ITALIC));
        hint.setVisible(false);
        return hint;
    }

    private static JPanel column(String titleKey) {
        JPanel column = new JPanel(new GridBagLayout());
        column.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 0, 4, 0);
        JLabel title = new JLabel(LanguageService.displayName(titleKey));
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        column.add(title, gbc);
        // Takes the extra width and height, so the rows stay together at the top left of the column.
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 3;
        filler.gridy = 100;
        filler.weightx = 1;
        filler.weighty = 1;
        column.add(Box.createGlue(), filler);
        return column;
    }

    /** Label, spinner with two decimals in the UI language, "%" - and, for a buff, the "raised" hint below. */
    private static void addSpinnerRow(JPanel column, int row, String key, SpinnerNumberModel model, boolean buff,
                                      JLabel raisedHint) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = row * 2;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 0, 2, 6);

        JLabel label = new JLabel(LanguageService.displayName(key));
        if (buff) {
            label.setFont(label.getFont().deriveFont(Font.BOLD));
        }
        gbc.gridx = 0;
        column.add(label, gbc);

        JSpinner spinner = new JSpinner(model);
        spinner.setLocale(JournalTexts.locale());
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "0.00"));
        double min = buff ? CowScoreBonuses.MIN_BUFF : CowScoreBonuses.MIN_BONUS;
        double max = buff ? CowScoreBonuses.MAX_BUFF : CowScoreBonuses.MAX_BONUS;
        spinner.setToolTipText(LanguageService.displayName(key + ".tooltip", percent(min), percent(max)));
        gbc.gridx = 1;
        column.add(spinner, gbc);

        gbc.gridx = 2;
        column.add(new JLabel("%"), gbc);

        if (raisedHint != null) {
            gbc.gridx = 0;
            gbc.gridy = row * 2 + 1;
            gbc.gridwidth = 3;
            gbc.insets = new Insets(0, 0, 4, 0);
            column.add(raisedHint, gbc);
        }
    }

    private static String percent(double value) {
        NumberFormat format = NumberFormat.getNumberInstance(JournalTexts.locale());
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }
}
