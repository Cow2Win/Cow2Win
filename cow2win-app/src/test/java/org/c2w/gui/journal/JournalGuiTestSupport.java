package org.c2w.gui.journal;

import org.c2w.data.journal.LogDirection;
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
import org.c2w.service.journal.ImportPlan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Temp workspace with an open guild "Alpha" and a real {@link JournalImportService}
 * (shared test parser), for the Swing-free journal GUI models.
 */
abstract class JournalGuiTestSupport {

    static final String SEP_24 = "24-09-2026";

    @TempDir
    Path workspace;

    private String previousWorkspace;
    AppContext context;
    GuildService guildService;

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

    JournalImportService service() {
        return new JournalImportService(context, guildService, context.journal(), BattleLogTestFiles::parser);
    }

    ImportPlan prepare(String folder, String day, LogDirection... directions) throws Exception {
        List<Path> files = new ArrayList<>();
        for (LogDirection direction : directions) {
            files.add(BattleLogTestFiles.file(folder, day, direction));
        }
        return service().prepare(files);
    }

    void importWithDefaults(ImportPlan plan) {
        assertTrue(service().execute(plan, ImportAnswers.defaults()).isSuccess());
    }

    /** Members for the 24.09. defense log: Puschel (exact), team gandagom (normalized), Ordensriter (typo). */
    void setSampleMembers() {
        setMembers(List.of(member("Puschel"), member("team gandagom"), member("Ordensriter")));
    }

    void setMembers(List<GuildMember> members) {
        context.setGuild(new Guild(context.guild().id(), context.guild().name(), members, context.guild().gameGuildId()));
        context.setGuildDirty(false);
    }

    static GuildMember member(String id) {
        return new GuildMember(id, id, List.of(), List.of());
    }

    static List<GuildMember> dummyMembers(int count) {
        List<GuildMember> members = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            members.add(member("m" + i));
        }
        return members;
    }
}
