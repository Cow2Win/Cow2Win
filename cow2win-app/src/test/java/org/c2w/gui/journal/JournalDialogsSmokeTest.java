package org.c2w.gui.journal;

import org.c2w.data.journal.parse.BattleLogTestFiles;
import org.c2w.data.model.GuildMember;
import org.c2w.data.repository.GuildRepository;
import org.c2w.service.JournalMaintenanceService;
import org.c2w.service.journal.ImportAnswers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Builds the phase-5 windows with a real journal and paints them off-screen (never
 * shown) - layouts, table models and renderers run once. Skipped without a display.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class JournalDialogsSmokeTest extends JournalGuiTestSupport {

    private JournalOperations operations;

    @BeforeEach
    void importAll() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        setSampleMembers();
        assertTrue(service().execute(service().prepare(BattleLogTestFiles.files("de")), ImportAnswers.defaults()).isSuccess());
        operations = new JournalOperations(new JournalMaintenanceService(context.journal(), BattleLogTestFiles::parser),
                () -> { });
    }

    @AfterEach
    void disposeWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (Window w : Window.getWindows()) {
                w.dispose();
            }
        });
    }

    @Test
    @DisplayName("Battle list, all battle details, players and name mappings build and paint")
    void dialogs() throws Exception {
        JournalBattleListDialog list = onEdt(() -> new JournalBattleListDialog(null, context, new JournalBattleListDialog.Host() {
            @Override
            public void importFiles(List<Path> files) {
            }

            @Override
            public void openDetail(int battleId) {
            }

            @Override
            public JournalOperations operations() {
                return operations;
            }
        }));
        waitFor(() -> find(list, JTable.class).getRowCount() == 6);
        paint(list);
        AbstractButton delete = buttonWithTooltip(list, JournalTexts.text("journal.action.delete"));
        assertNotNull(delete);
        assertFalse(onEdt(delete::isEnabled), "delete needs a selection");
        onEdt(() -> {
            find(list, JTable.class).setRowSelectionInterval(0, 1);
            return null;
        });
        assertTrue(onEdt(delete::isEnabled), "delete works on several battles");

        for (var battle : context.journal().repository(false).orElseThrow().listBattles()) {
            JournalBattleDetailDialog detail = onEdt(() -> new JournalBattleDetailDialog(null, context,
                    new JournalBattleDetailDialog.Host() {
                        @Override
                        public void importWithChooser() {
                        }
                    }, battle.battleId()));
            waitFor(() -> findOrNull(detail, JTabbedPane.class) != null);
            JTabbedPane tabs = find(detail, JTabbedPane.class);
            assertEquals(JournalTexts.text("journal.detail.tab.general"), onEdt(() -> tabs.getTitleAt(0)));
            assertEquals(1, onEdt(tabs::getSelectedIndex), "opens on the defense");
            for (int i = 0; i < onEdt(tabs::getTabCount); i++) {
                int tab = i;
                onEdt(() -> {
                    tabs.setSelectedIndex(tab);
                    return null;
                });
                paint(detail);
            }
            // select a fight: the units panel (17.09. defense, all attack logs) shows the teams
            onEdt(() -> {
                tabs.setSelectedIndex(2);
                JTable fights = findIn(tabs.getComponentAt(2), JTable.class);
                if (fights != null && fights.getRowCount() > 0) {
                    fights.setRowSelectionInterval(0, 0);
                }
                return null;
            });
            paint(detail);
        }

        java.util.concurrent.atomic.AtomicInteger mappingsOpened = new java.util.concurrent.atomic.AtomicInteger();
        JournalPlayersDialog players = onEdt(() -> new JournalPlayersDialog(null, context, operations.service(), () -> { },
                mappingsOpened::incrementAndGet));
        AbstractButton openMappings = buttonWithTooltip(players, JournalTexts.text("journal.action.nameMappings"));
        assertNotNull(openMappings);
        onEdt(() -> {
            openMappings.doClick();
            return null;
        });
        assertEquals(1, mappingsOpened.get(), "the button opens the name mappings via the journal menu");
        waitFor(() -> find(players, JTable.class).getRowCount() > 0);
        onEdt(() -> {
            find(players, JTable.class).setRowSelectionInterval(0, 0);
            return null;
        });
        paint(players);

        operations.service().putNameMapping(org.c2w.data.journal.NameMappingKind.HERO, "Neuheld", "dante");
        JournalNameMappingsDialog mappings = onEdt(() -> new JournalNameMappingsDialog(null, context, operations));
        waitFor(() -> find(mappings, JTable.class).getRowCount() == 1);
        paint(mappings);
    }

    @Test
    @DisplayName("Battle detail: tab order, visual tabs step with Next / Back, a missing log offers the import")
    void replayTabs() throws Exception {
        int battleId = context.journal().repository(false).orElseThrow().listBattles().stream()
                .filter(b -> b.date().equals(java.time.LocalDate.of(2026, 10, 1))).findFirst().orElseThrow().battleId();
        JournalBattleDetailDialog detail = openDetail(battleId);
        JTabbedPane tabs = find(detail, JTabbedPane.class);
        List<String> titles = onEdt(() -> {
            List<String> t = new ArrayList<>();
            for (int i = 0; i < tabs.getTabCount(); i++) {
                t.add(tabs.getTitleAt(i));
            }
            return t;
        });
        assertEquals(List.of(JournalTexts.text("journal.detail.tab.general"),
                JournalTexts.of("direction", org.c2w.data.journal.LogDirection.DEFENSE),
                JournalTexts.of("direction", org.c2w.data.journal.LogDirection.ATTACK),
                JournalTexts.text("journal.detail.tab.attackVisual"),
                JournalTexts.text("journal.detail.tab.defenseVisual"),
                JournalTexts.text("journal.detail.tab.forts")), titles.subList(0, 6));
        assertEquals(1, onEdt(tabs::getSelectedIndex), "opens on the defense");

        for (int tab : new int[]{3, 4}) {
            BattleReplayPanel replay = onEdt(() -> {
                tabs.setSelectedIndex(tab);
                return findIn(tabs.getComponentAt(tab), BattleReplayPanel.class);
            });
            assertNotNull(replay);
            assertEquals(0, onEdt(replay::step), "starts at the start");
            AbstractButton back = buttonWithText(replay, JournalTexts.text("journal.replay.prev"));
            AbstractButton next = buttonWithText(replay, JournalTexts.text("journal.replay.next"));
            assertFalse(onEdt(back::isEnabled), "back is off at the start");
            assertTrue(onEdt(next::isEnabled));
            paint(detail);
            onEdt(() -> {
                next.doClick();
                next.doClick();
                next.doClick();
                return null;
            });
            assertEquals(3, onEdt(replay::step));
            assertTrue(onEdt(back::isEnabled));
            paint(detail);
            onEdt(() -> {
                back.doClick();
                return null;
            });
            assertEquals(2, onEdt(replay::step));
            onEdt(() -> {
                replay.goTo(replay.stepCount());
                return null;
            });
            assertFalse(onEdt(next::isEnabled), "next is off at the end");
            paint(detail);
        }
        assertEquals(92, onEdt(() -> findIn(tabs.getComponentAt(3), BattleReplayPanel.class).stepCount()));
        assertEquals(67, onEdt(() -> findIn(tabs.getComponentAt(4), BattleReplayPanel.class).stepCount()));

        // the step of a visual tab survives a reload
        onEdt(() -> {
            findIn(tabs.getComponentAt(3), BattleReplayPanel.class).goTo(11);
            detail.reload();
            return null;
        });
        waitFor(() -> find(detail, JTabbedPane.class) != tabs);
        JTabbedPane reloaded = find(detail, JTabbedPane.class);
        assertEquals(11, onEdt(() -> findIn(reloaded.getComponentAt(3), BattleReplayPanel.class).step()));

        // another guild with only the attack log: "Defense (visual)" shows the hint and the import
        guildService.createGuild("Beta", false);
        guildService.switchToGuild("Beta");
        assertTrue(service().execute(service().prepare(List.of(BattleLogTestFiles.file("de", "01-10-2026",
                org.c2w.data.journal.LogDirection.ATTACK))), ImportAnswers.defaults()).isSuccess());
        int onlyAttack = context.journal().repository(false).orElseThrow().listBattles().get(0).battleId();
        JournalBattleDetailDialog partial = openDetail(onlyAttack);
        JTabbedPane partialTabs = find(partial, JTabbedPane.class);
        assertNull(onEdt(() -> findIn(partialTabs.getComponentAt(4), BattleReplayPanel.class)));
        assertNotNull(buttonWithText((Container) onEdt(() -> partialTabs.getComponentAt(4)),
                JournalTexts.text("journal.battles.import")));
        assertNotNull(onEdt(() -> findIn(partialTabs.getComponentAt(3), BattleReplayPanel.class)));
        paint(partial);
    }

    private JournalBattleDetailDialog openDetail(int battleId) throws Exception {
        JournalBattleDetailDialog detail = onEdt(() -> new JournalBattleDetailDialog(null, context,
                new JournalBattleDetailDialog.Host() {
                    @Override
                    public void importWithChooser() {
                    }
                }, battleId));
        waitFor(() -> findOrNull(detail, JTabbedPane.class) != null);
        return detail;
    }

    private static AbstractButton buttonWithText(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof AbstractButton b && text.equals(b.getText())) {
                return b;
            }
            if (c instanceof Container inner) {
                AbstractButton found = buttonWithText(inner, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    @DisplayName("'Build teams from logs' builds and paints, also its members and proposals")
    void teamBuilder() throws Exception {
        JournalTeamBuilderDialog dialog = onEdt(() -> JournalTeamBuilderDialog.create(null, context, guildService));
        waitFor(() -> dialog.model() != null);
        assertFalse(dialog.model().members().isEmpty());
        String apply = onEdt(() -> dialog.applyButton().getText());
        assertEquals(JournalTexts.text("journal.teams.apply", String.valueOf(dialog.model().selectedCount())), apply);
        assertTrue(onEdt(() -> dialog.getRootPane().getDefaultButton() == dialog.applyButton()));
        List<String> checkBoxes = new ArrayList<>();
        onEdt(() -> {
            collectCheckBoxTexts(dialog.getContentPane(), checkBoxes);
            return null;
        });
        String selected = JournalTexts.text("journal.teams.selected", String.valueOf(dialog.model().selectedCount()),
                String.valueOf(dialog.model().selectableCount()));
        assertTrue(checkBoxes.contains(selected), checkBoxes.toString());
        assertTrue(checkBoxes.stream().allMatch(t -> t.isEmpty() || t.equals(selected)),
                "no other checkbox with text (no filter 'only members without teams'): " + checkBoxes);
        paint(dialog);
    }

    private static void collectCheckBoxTexts(Component component, List<String> texts) {
        if (component instanceof JCheckBox box) {
            texts.add(box.getText());
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectCheckBoxTexts(child, texts);
            }
        }
    }

    @Test
    @DisplayName("'Update teams (sync)' builds and paints, also its rows")
    void sync() throws Exception {
        // Puschel of the real guild: its teams match the 01.10. defense log
        GuildMember puschel = GuildRepository.load(Path.of("src", "test", "resources", "guild", "deutscher-bund.json"),
                context.catalog()).members().stream().filter(m -> m.id().equals("Puschel")).findFirst().orElseThrow();
        List<GuildMember> members = new ArrayList<>(context.guild().members());
        members.replaceAll(m -> m.id().equals("Puschel") ? puschel : m);
        setMembers(members);

        JournalSyncDialog dialog = onEdt(() -> JournalSyncDialog.create(null, context, guildService));
        waitFor(() -> dialog.model() != null);
        assertFalse(dialog.model().rows().isEmpty(), "Puschel's titan team changed since 01.10.");
        paint(dialog);
    }

    // --- helpers ---

    private static <T> T onEdt(Supplier<T> work) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> result.set(work.get()));
        return result.get();
    }

    private static void waitFor(BooleanSupplier condition) throws Exception {
        long end = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < end) {
            if (onEdt(condition::getAsBoolean)) {
                return;
            }
            Thread.sleep(50);
        }
        fail("condition not reached");
    }

    /** Folder to save the renderings to (system property {@code journal.smoke.out}), for looking at them. */
    private static final String OUT = System.getProperty("journal.smoke.out");
    private static int images;

    private static void paint(Window window) throws Exception {
        BufferedImage image = onEdt(() -> {
            window.setSize(1300, 820);
            window.addNotify();
            window.validate();
            BufferedImage rendered = new BufferedImage(1300, 820, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = rendered.createGraphics();
            try {
                ((RootPaneContainer) window).getRootPane().printAll(g);
            } finally {
                g.dispose();
            }
            return rendered;
        });
        if (OUT != null) {
            javax.imageio.ImageIO.write(image, "png", Path.of(OUT, String.format("%02d.png", ++images)).toFile());
        }
    }

    private static AbstractButton buttonWithTooltip(Container root, String tooltip) {
        for (Component c : root.getComponents()) {
            if (c instanceof AbstractButton b && tooltip.equals(b.getToolTipText())) {
                return b;
            }
            if (c instanceof Container inner) {
                AbstractButton found = buttonWithTooltip(inner, tooltip);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static <T extends Component> T find(Container root, Class<T> type) {
        T found = findOrNull(root, type);
        assertNotNull(found, type.getSimpleName());
        return found;
    }

    private static <T extends Component> T findOrNull(Container root, Class<T> type) {
        return findIn(root, type);
    }

    private static <T extends Component> T findIn(Component component, Class<T> type) {
        if (type.isInstance(component)) {
            return type.cast(component);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                T found = findIn(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
