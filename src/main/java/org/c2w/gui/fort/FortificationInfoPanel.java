package org.c2w.gui.fort;

import org.c2w.data.model.*;
import org.c2w.util.Config;
import org.c2w.util.LanguageService;

import javax.swing.*;
import java.awt.*;
import java.util.stream.Collectors;

public class FortificationInfoPanel extends JPanel {

    private final Fortification fortification;

    //private final JComboBox<Integer> strategicImportanceCombo = new JComboBox<>(STRATEGIC_IMPORTANCE_VALUES);

    public FortificationInfoPanel(Fortification fortification) {
        if (fortification == null) {
            throw new IllegalArgumentException("FortificationInfoPanel needs a fortification");
        }
        this.fortification = fortification;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        add(buildInfoPanel());

        loadFromFortification();
    }

    private void loadFromFortification() {
        //strategicImportanceCombo.setSelectedItem(clampToStrategicImportanceRange(fortification.strategicImportance()));
    }

    private static int clampToStrategicImportanceRange(int value) {
        return Math.max(1, Math.min(10, value));
    }

    private JPanel buildInfoPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 6, 3, 6);
        gbc.anchor = GridBagConstraints.WEST;
        int y = 0;

        addInfoRow(panel, gbc, y++, "Buff:", buffText());
        addInfoRow(panel, gbc, y++, LanguageService.displayName("fortificationDetail.prerequisites"), prerequisitesText());

        gbc.gridx = 0;
        gbc.gridy = y;
        panel.add(new JLabel("Strategic Importance:"), gbc);
        gbc.gridx = 1;
        panel.add(new JLabel(fortification.strategicImportance()+""), gbc);
        return panel;
    }

    private static void addInfoRow(JPanel panel, GridBagConstraints gbc, int y, String label, String value) {
        gbc.gridx = 0;
        gbc.gridy = y;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        panel.add(new JLabel(value), gbc);
    }

    private String buffText() {
        Buff buff = fortification.buff();
        if (buff == null) {
            return LanguageService.displayName("common.none");
        }
        String text = buff.display();
        if (text == null || text.isBlank()) {
            text = buff.effect().name() + " (" + Config.NUMBER_FORMAT.format(buff.bonusPercent()) + "%)";
        }
        return text;
    }


    private String prerequisitesText() {
        if (fortification.prerequisites().isEmpty()) {
            return LanguageService.displayName("common.none");
        }
        return fortification.prerequisites().stream()
                .map(LanguageService::displayName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));
    }


    /**
     * Applies this panel's edits (currently none - {@code strategicImportanceCombo} is
     * commented out above) to {@code base}, returning the resulting {@link Fortification}.
     * Kept as its own method/hook (see {@link org.c2w.gui.fort.FortificationEntryDialog}'s
     * javadoc on its {@code infoPanel} field) for when a catalog field becomes editable here again.
     */
    public Fortification applyEditsTo(Fortification base) {
        return base;
    }
}

