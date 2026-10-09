package org.c2w.gui.journal;

import org.c2w.data.journal.LogDirection;
import org.c2w.service.journal.ImportPlan;
import org.c2w.service.journal.PlanError;
import org.c2w.service.journal.PlayerAnswer;
import org.c2w.service.journal.PlayerQuestion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Swing pages of the import assistant built from real plans (no window):
 * they render without errors, and input reaches the {@link ImportWizardModel}.
 */
class JournalPanelsSmokeTest extends JournalGuiTestSupport {

    @Test
    @DisplayName("Overview with battles, and with plan errors incl. the switch-guild button")
    void overview() throws Exception {
        ImportPlan plan = prepare("de", SEP_24, LogDirection.ATTACK, LogDirection.DEFENSE);
        OverviewStepPanel panel = new OverviewStepPanel(plan, e -> fail("no switch expected"));
        JTable table = find(panel, JTable.class).get(0);
        assertEquals(1, table.getRowCount());
        assertEquals(8, table.getColumnCount());

        List<PlanError> switched = new ArrayList<>();
        ImportPlan foreign = new ImportPlan(plan.files(), List.of(),
                List.of(new PlanError(PlanError.Kind.LOGS_OF_OTHER_GUILD, "Beta", 193861L)), 193861L,
                null, List.of(), List.of(), List.of(), List.of(), 0, plan.guildFile());
        OverviewStepPanel errors = new OverviewStepPanel(foreign, switched::add);
        JButton switchButton = find(errors, JButton.class).get(0);
        switchButton.doClick();
        assertEquals(1, switched.size());
    }

    @Test
    @DisplayName("Players page: choosing an answer updates the model, member limit is counted")
    void players() throws Exception {
        setSampleMembers();
        ImportPlan plan = prepare("de", SEP_24, LogDirection.DEFENSE);
        ImportWizardModel model = new ImportWizardModel(plan, context.guild());
        AtomicInteger changes = new AtomicInteger();
        PlayersStepPanel panel = new PlayersStepPanel(model, changes::incrementAndGet);
        PlayerQuestion faern = plan.playerQuestions().stream().filter(q -> q.rawName().equals("Faern")).findFirst().orElseThrow();

        @SuppressWarnings("unchecked")
        JComboBox<PlayerAnswer.Kind> kind = (JComboBox<PlayerAnswer.Kind>) find(panel, JComboBox.class).stream()
                .filter(c -> c.getItemCount() > 0 && c.getItemAt(0) instanceof PlayerAnswer.Kind)
                .filter(c -> isRowOf(panel, c, "Faern")).findFirst().orElseThrow();
        kind.setSelectedItem(PlayerAnswer.Kind.CREATE);
        assertEquals(PlayerAnswer.of(PlayerAnswer.Kind.CREATE), model.playerAnswer(faern.id()));
        kind.setSelectedItem(PlayerAnswer.Kind.RENAME);
        assertEquals(PlayerAnswer.Kind.RENAME, model.playerAnswer(faern.id()).kind());
        assertNotNull(model.playerAnswer(faern.id()).memberId(), "first member preselected");
        assertTrue(changes.get() > 0);
    }

    @Test
    @DisplayName("Names page builds from a plan")
    void names() throws Exception {
        setSampleMembers();
        ImportPlan plan = prepare("de", SEP_24, LogDirection.ATTACK);
        ImportWizardModel model = new ImportWizardModel(plan, context.guild());

        NamesStepPanel panel = new NamesStepPanel(model, kind -> List.of(
                new NamesStepPanel.CatalogChoice("dante", "Dante", null)), () -> { });

        assertNotNull(panel);
    }

    /** True if {@code combo} is in the same grid row as the label showing {@code name}. */
    private static boolean isRowOf(Container root, JComponent combo, String name) {
        Container parent = combo.getParent();
        GridBagLayout layout = (GridBagLayout) parent.getLayout();
        int row = layout.getConstraints(combo).gridy;
        for (Component c : parent.getComponents()) {
            if (c instanceof JLabel label && name.equals(label.getText()) && layout.getConstraints(c).gridy == row) {
                return true;
            }
        }
        return false;
    }

    private static <T> List<T> find(Container root, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) {
                result.add(type.cast(c));
            }
            if (c instanceof Container container) {
                result.addAll(find(container, type));
            }
        }
        return result;
    }
}
