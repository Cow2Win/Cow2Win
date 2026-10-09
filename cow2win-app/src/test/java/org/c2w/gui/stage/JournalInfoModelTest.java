package org.c2w.gui.stage;

import org.c2w.data.journal.LogDirection;
import org.c2w.data.journal.db.BattleSummary;
import org.c2w.data.journal.db.JournalException;
import org.c2w.data.journal.db.JournalRepository;
import org.c2w.data.journal.parse.BattleLogTestFiles;
import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.repository.Catalog;
import org.c2w.infra.Config;
import org.c2w.service.AppContext;
import org.c2w.service.GuildService;
import org.c2w.service.JournalImportService;
import org.c2w.service.RecentFiles;
import org.c2w.service.journal.ImportAnswers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** {@link JournalInfoModel#load} - with no journal, a failing one and real test battle logs. */
class JournalInfoModelTest {

    @TempDir
    Path workspace;

    private String previousWorkspace;
    private AppContext context;
    private GuildService guildService;

    @BeforeEach
    void openWorkspace() throws Exception {
        previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
        context = new AppContext(new Catalog(workspace));
        guildService = new GuildService(context, RecentFiles.NONE);
        guildService.createGuild("Alpha", false);
        guildService.switchToGuild("Alpha");
    }

    @AfterEach
    void closeWorkspace() {
        context.journal().closeCurrent();
        Config.setWorkspacePath(previousWorkspace);
    }

    @Test
    @DisplayName("No journal database: state NO_JOURNAL")
    void noJournal() {
        assertEquals(JournalInfoModel.State.NO_JOURNAL, JournalInfoModel.load(context.journal()).state());
        assertEquals(JournalInfoModel.State.NO_JOURNAL, JournalInfoModel.load(create -> Optional.empty()).state());
    }

    @Test
    @DisplayName("A journal that cannot be read: state ERROR, no exception")
    void error() {
        JournalInfoModel.JournalInfo info = JournalInfoModel.load(create -> {
            throw new JournalException("broken", null);
        });
        assertEquals(JournalInfoModel.State.ERROR, info.state());
    }

    @Test
    @DisplayName("With imported battles: counts, newest defense day and the three newest battles, newest first")
    void loaded() throws Exception {
        List<GuildMember> members = List.of(member("Puschel"), member("team gandagom"), member("Ordensriter"));
        context.setGuild(new Guild(context.guild().id(), context.guild().name(), members, context.guild().gameGuildId()));
        JournalImportService importService = new JournalImportService(context, guildService, context.journal(),
                BattleLogTestFiles::parser);
        assertTrue(importService.execute(importService.prepare(BattleLogTestFiles.files("de")), ImportAnswers.defaults())
                .isSuccess());

        JournalInfoModel.JournalInfo info = JournalInfoModel.load(context.journal());

        JournalRepository repository = context.journal().repository(false).orElseThrow();
        List<BattleSummary> battles = repository.listBattles(null);
        assertEquals(JournalInfoModel.State.LOADED, info.state());
        assertEquals(repository.countAll().battles(), info.battles());
        assertEquals(repository.countAll().logs(), info.logs());
        assertTrue(battles.size() > JournalInfoModel.RECENT_BATTLES, "test data has more battles than are listed");
        assertEquals(battles.subList(0, JournalInfoModel.RECENT_BATTLES), info.recentBattles());
        for (int i = 1; i < info.recentBattles().size(); i++) {
            assertFalse(info.recentBattles().get(i).date().isAfter(info.recentBattles().get(i - 1).date()), "newest first");
        }
        assertEquals(battles.stream().filter(b -> b.directions().contains(LogDirection.DEFENSE)).findFirst()
                .orElseThrow().date(), info.newestDefense());
    }

    private static GuildMember member(String id) {
        return new GuildMember(id, id, List.of(), List.of());
    }
}
