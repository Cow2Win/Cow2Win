package org.c2w.gui.guild;

import org.c2w.data.model.Hero;
import org.c2w.data.repository.Catalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import javax.swing.text.Document;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/** {@link TeamEditorPanel}'s power field rejects any input above {@link org.c2w.domain.TeamPower#MAX}. */
class TeamEditorPanelPowerTest {

    /** The catalogs seed their CowScore workspace files here instead of the real workspace. */
    @TempDir
    Path workspace;

    private TeamDraft<Hero> draft;
    private JTextField powerField;
    private int rejections;

    @BeforeEach
    void buildPanel() {
        System.setProperty("java.awt.headless", "true");
        Catalog catalog = new Catalog(workspace);
        draft = new TeamDraft<>();
        TeamEditorPanel<Hero> panel = new TeamEditorPanel<>(catalog.heroes().findAll(), Hero::id, null, null, draft, "-",
                Comparator.comparing(Hero::id));
        panel.setPowerRejectedHandler(() -> rejections++);
        powerField = (JTextField) panel.getComponent(0);
        powerField.setText("");
    }

    private void type(String text) throws Exception {
        Document document = powerField.getDocument();
        for (char c : text.toCharArray()) {
            document.insertString(document.getLength(), String.valueOf(c), null);
        }
    }

    @Test
    @DisplayName("typing exactly 2000000 is accepted")
    void maxAccepted() throws Exception {
        type("2000000");
        assertEquals("2000000", powerField.getText());
        assertEquals(2_000_000, draft.totalPower);
        assertEquals(0, rejections);
    }

    @Test
    @DisplayName("one more digit after 2000000 is rejected, field and draft stay unchanged")
    void extraDigitRejected() throws Exception {
        type("2000000");
        type("1");
        assertEquals("2000000", powerField.getText());
        assertEquals(2_000_000, draft.totalPower);
        assertEquals(1, rejections);
    }

    @Test
    @DisplayName("pasting 2500000 (or an overlong number) is rejected, field and draft stay unchanged")
    void pasteTooHighRejected() throws Exception {
        type("123");
        powerField.selectAll();
        powerField.replaceSelection("2500000");
        assertEquals("123", powerField.getText());
        assertEquals(123, draft.totalPower);

        powerField.getDocument().insertString(3, "99999999999999999999", null);
        assertEquals("123", powerField.getText());
        assertEquals(2, rejections);
    }

    @Test
    @DisplayName("pasting 1.999.999 with grouping separators is accepted")
    void pasteWithSeparatorsAccepted() {
        powerField.replaceSelection("1.999.999");
        assertEquals(1_999_999, draft.totalPower);
        assertEquals(0, rejections);
    }
}
