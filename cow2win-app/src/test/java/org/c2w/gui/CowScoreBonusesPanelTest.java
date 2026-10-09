package org.c2w.gui;

import org.c2w.domain.CowScoreBonuses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The "CowScore bonuses" area of the settings dialog - never shown. */
class CowScoreBonusesPanelTest {

    private static final double EPS = 1e-9;

    private final CowScoreBonusesPanel panel = new CowScoreBonusesPanel(CowScoreBonuses.DEFAULTS);

    @Test
    @DisplayName("Combo raised to 1.8: the role buff follows to 1.8 (with hint), the element buff stays")
    void comboRaisesRoleBuff() {
        panel.comboModel().setValue(1.8);

        assertEquals(1.8, panel.bonuses().rolePercent(), EPS);
        assertEquals(1.5, panel.bonuses().elementPercent(), EPS);
        assertTrue(panel.roleRaisedHint().isVisible());
        assertEquals(1.8, ((Number) panel.roleModel().getMinimum()).doubleValue(), EPS,
                "the buff cannot be set below the combo");

        panel.comboModel().setValue(1.3);
        assertEquals(1.8, panel.bonuses().rolePercent(), EPS, "lowering a small bonus leaves the buff");
        assertEquals(1.3, ((Number) panel.roleModel().getMinimum()).doubleValue(), EPS,
                "the minimum follows the biggest small bonus");
    }

    @Test
    @DisplayName("Totem raised to 1.9: the element buff follows to 1.9")
    void totemRaisesElementBuff() {
        panel.totemModel().setValue(1.9);

        assertEquals(1.9, panel.bonuses().elementPercent(), EPS);
        assertEquals(1.5, panel.bonuses().rolePercent(), EPS);
    }

    @Test
    @DisplayName("The relation: both spinners show the same value, both buffs follow if needed")
    void relationSharedByBothSides() {
        List<JSpinner> relationSpinners = spinnersOf(panel.relationModel());
        assertEquals(2, relationSpinners.size(), "one spinner per side, one model");

        panel.relationModel().setValue(2.0);

        relationSpinners.forEach(spinner -> assertEquals(2.0, ((Number) spinner.getValue()).doubleValue(), EPS));
        assertEquals(2.0, panel.bonuses().relationPercent(), EPS);
        assertEquals(2.0, panel.bonuses().rolePercent(), EPS);
        assertEquals(2.0, panel.bonuses().elementPercent(), EPS);
    }

    @Test
    @DisplayName("Defaults resets all seven values")
    void defaultsResetsEverything() {
        panel.comboModel().setValue(2.0);
        panel.totemModel().setValue(1.7);
        panel.relationModel().setValue(1.1);

        panel.resetToDefaults();

        assertEquals(CowScoreBonuses.DEFAULTS, panel.bonuses());
        assertFalse(panel.roleRaisedHint().isVisible());
    }

    @Test
    @DisplayName("Values from the settings are shown; small spinner steps do not leave binary remainders")
    void initialValuesAndSteps() {
        CowScoreBonuses initial = new CowScoreBonuses(2.0, 1.75, 1.5, 1.25, 1.3, 1.4, 1.05);
        CowScoreBonusesPanel own = new CowScoreBonusesPanel(initial);
        assertEquals(initial, own.bonuses());

        own.comboModel().setValue(own.comboModel().getNextValue());
        assertEquals(1.45, own.bonuses().comboPercent(), 0.0);
    }

    private List<JSpinner> spinnersOf(SpinnerModel model) {
        return findSpinners(panel).stream().filter(spinner -> spinner.getModel() == model).toList();
    }

    private static List<JSpinner> findSpinners(java.awt.Container container) {
        java.util.List<JSpinner> found = new java.util.ArrayList<>();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof JSpinner spinner) {
                found.add(spinner);
            } else if (child instanceof java.awt.Container nested) {
                found.addAll(findSpinners(nested));
            }
        }
        return found;
    }
}
