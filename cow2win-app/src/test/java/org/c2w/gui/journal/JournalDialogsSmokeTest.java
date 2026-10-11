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
    @DisplayName("'Build teams from logs' builds and paints, also its members and proposals")
    void teamBuilder() throws Exception {
        JournalTeamBuilderDialog dialog = onEdt(() -> JournalTeamBuilderDialog.create(null, context, guildService));
        waitFor(() -> dialog.model() != null);
        assertFalse(dialog.model().plan().members().isEmpty());
        paint(dialog);
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
