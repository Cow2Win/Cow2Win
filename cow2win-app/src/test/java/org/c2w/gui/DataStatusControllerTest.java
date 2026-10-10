package org.c2w.gui;

import org.c2w.data.journal.LogDirection;
import org.c2w.data.journal.parse.BattleLogTestFiles;
import org.c2w.data.repository.Catalog;
import org.c2w.gui.action.Stage;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Config;
import org.c2w.service.AppContext;
import org.c2w.service.DataStatus;
import org.c2w.service.DataStatus.Level;
import org.c2w.service.DataStatus.StageResult;
import org.c2w.service.DataStatus.TextPart;
import org.c2w.service.GuildService;
import org.c2w.service.JournalImportService;
import org.c2w.service.RecentFiles;
import org.c2w.service.journal.ImportAnswers;
import org.c2w.service.journal.ImportPlan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** {@link DataStatusController} hands a data status to process bar, status bar and listeners - headless. */
class DataStatusControllerTest {

    @TempDir
    Path workspace;

    private AppContext context;
    private ProcessBar processBar;
    private StatusBar statusBar;
    private final List<Stage> switched = new ArrayList<>();
    private DataStatusController controller;

    @BeforeEach
    void setUp() {
        context = new AppContext(new Catalog(workspace));
        processBar = new ProcessBar();
        statusBar = new StatusBar();
        controller = new DataStatusController(context, new GuildService(context, RecentFiles.NONE), processBar,
                statusBar, switched::add);
    }

    @AfterEach
    void tearDown() {
        context.journal().closeCurrent();
    }

    private static StageResult result(Level level, TextPart... texts) {
        return new StageResult(level, List.of(texts));
    }

    @Test
    @DisplayName("Traffic lights and short texts of all three tiles; NONE without text keeps the static subtitle")
    void tiles() {
        DataStatus status = new DataStatus(
                result(Level.ACTION_NEEDED, TextPart.of("status.input.staleJournal", 3)),
                result(Level.ATTENTION, TextPart.of("status.concept.freeSlots", 2), TextPart.of("status.concept.unsaved")),
                result(Level.NONE),
                null, 30, 3, 0, null, Map.of());
        controller.show(status);

        assertEquals(StageStatus.ACTION_NEEDED, processBar.tile(Stage.INPUT).status());
        assertEquals(StageStatus.ATTENTION, processBar.tile(Stage.CONCEPT).status());
        assertEquals(StageStatus.NONE, processBar.tile(Stage.OUTPUT).status());
        assertEquals(LanguageService.displayName("status.input.staleJournal", 3),
                processBar.tile(Stage.INPUT).shownSubtitle());
        assertEquals(LanguageService.displayName("status.concept.freeSlots", 2) + ", "
                + LanguageService.displayName("status.concept.unsaved"), processBar.tile(Stage.CONCEPT).shownSubtitle());
        assertEquals(processBar.tile(Stage.OUTPUT).subtitle(), processBar.tile(Stage.OUTPUT).shownSubtitle());
        assertSame(status, controller.current());
    }

    @Test
    @DisplayName("\"Next step → {stage}\" goes to the first red stage; hidden when nothing is to be done")
    void nextStep() {
        controller.show(new DataStatus(result(Level.ATTENTION), result(Level.OK), result(Level.ACTION_NEEDED),
                null, 0, 0, 0, null, Map.of()));
        assertTrue(processBar.isNextStepVisible());
        assertEquals(LanguageService.displayName("processBar.nextStepTo",
                LanguageService.displayName(Stage.OUTPUT.stageTextKey())), processBar.nextStepText().replace("->", "→"));
        controller.nextStepAction().actionPerformed(null);
        assertEquals(List.of(Stage.OUTPUT), switched);

        controller.show(new DataStatus(result(Level.OK), result(Level.NONE), result(Level.OK),
                null, 0, 0, 0, null, Map.of()));
        assertFalse(processBar.isNextStepVisible());
    }

    @Test
    @DisplayName("Status bar: newest defense log, outdated teams (journal before age), lineup status; missing values left out")
    void statusBar() {
        DataStatus journal = new DataStatus(result(Level.ACTION_NEEDED), result(Level.OK, TextPart.of("status.concept.ok")),
                result(Level.OK), LocalDate.of(2026, 10, 1), 30, 2, 5, 30, Map.of());
        List<String> parts = DataStatusController.statusBarParts(journal);
        assertEquals(3, parts.size(), parts.toString());
        assertEquals(LanguageService.displayName("statusBar.staleJournal", 2, 30), parts.get(1));
        assertEquals(LanguageService.displayName("statusBar.lineup", LanguageService.displayName("status.concept.ok")),
                parts.get(2));

        DataStatus age = new DataStatus(result(Level.ATTENTION), result(Level.NONE), result(Level.NONE),
                null, 30, 0, 5, 30, Map.of());
        assertEquals(List.of(LanguageService.displayName("statusBar.staleAge", 5, 30, 30)),
                DataStatusController.statusBarParts(age));

        DataStatus nothing = new DataStatus(result(Level.OK), result(Level.NONE), result(Level.NONE),
                null, 30, 0, 0, null, Map.of());
        assertEquals(List.of(), DataStatusController.statusBarParts(nothing));

        controller.show(journal);
        assertEquals(String.join("  ·  ", parts), statusBar.text());
    }

    @Test
    @DisplayName("\"Outdated per journal\" only for a guild master's guild - without the flag the journal is not read, though its defense log shows other powers")
    void staleJournalOnlyForGuildMaster() throws Exception {
        String previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
        try {
            GuildService guildService = new GuildService(context, RecentFiles.NONE);
            Path folder = Files.createDirectories(workspace.resolve("Deutscher Bund"));
            Files.copy(Path.of("src", "test", "resources", "guild", "deutscher-bund.json"), folder.resolve("guild.json"));
            guildService.switchToGuild("Deutscher Bund");
            JournalImportService importer = new JournalImportService(context, guildService, context.journal(),
                    BattleLogTestFiles::parser);
            ImportPlan plan = importer.prepare(List.of(BattleLogTestFiles.file("de", "01-10-2026", LogDirection.ATTACK),
                    BattleLogTestFiles.file("de", "01-10-2026", LogDirection.DEFENSE)));
            assertTrue(importer.execute(plan, ImportAnswers.defaults()).isSuccess());
            guildService.saveGuild();

            // Without the flag: age check only, the journal stays closed.
            context.journal().closeCurrent();
            guildService.switchToGuild("Deutscher Bund");
            controller.requestUpdate();
            DataStatus plain = awaitStatus(status -> status.memberCount() > 0);
            assertEquals(0, plain.staleJournal());
            assertNull(plain.newestDefense());
            assertTrue(plain.members().values().stream().noneMatch(f -> f.state() == DataStatus.MemberState.STALE_JOURNAL));
            assertFalse(context.journal().isOpen(), "the journal was read");
            assertTrue(DataStatusController.statusBarParts(plain).stream()
                    .noneMatch(part -> part.contains(LanguageService.displayName("menu.journal"))), "status bar mentions the journal");

            // With the flag: as before.
            Path guildFile = folder.resolve("guild.json");
            Files.writeString(guildFile, Files.readString(guildFile).replaceFirst("\\{", "{\n  \"guildMaster\": true,"));
            guildService.switchToGuild("Deutscher Bund");
            assertTrue(context.isGuildMaster());
            controller.requestUpdate();
            DataStatus master = awaitStatus(status -> status.staleJournal() > 0);
            assertEquals(LocalDate.of(2026, 10, 1), master.newestDefense());
        } finally {
            Config.setWorkspacePath(previousWorkspace);
        }
    }

    /** Waits (up to 10 s) until the controller's current data status matches {@code condition}. */
    private DataStatus awaitStatus(java.util.function.Predicate<DataStatus> condition) throws InterruptedException {
        long end = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < end) {
            DataStatus current = controller.current();
            if (current != null && condition.test(current)) {
                return current;
            }
            Thread.sleep(20);
        }
        fail("no matching data status, last: " + controller.current());
        return null;
    }

    @Test
    @DisplayName("Listeners get every new data status")
    void listeners() {
        List<DataStatus> received = new ArrayList<>();
        controller.addListener(received::add);
        DataStatus status = new DataStatus(result(Level.OK), result(Level.OK), result(Level.OK),
                null, 0, 0, 0, null, Map.of());
        controller.show(status);
        assertEquals(List.of(status), received);
    }
}
