package org.c2w.service;

import org.c2w.data.journal.Fight;
import org.c2w.data.journal.parse.BattleLogParser;
import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.Catalog;
import org.c2w.data.repository.GuildRepository;
import org.c2w.data.repository.LineupRepository;
import org.c2w.infra.Config;
import org.c2w.infra.JsonSupport;
import org.c2w.service.journal.ImportPlan;
import org.c2w.service.journal.PlayerAutoAssignment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The bundled demo data ({@code data/guild.json}, {@code data/default.lineup},
 * {@code data/demo-journal}) fits together: the lineup only uses members of the guild,
 * and the battle logs can be imported into it without errors, unknown names or
 * questions about players.
 */
class DemoDataTest {

    private static final Path GUILD = JsonSupport.resolveDataFile("data", "guild.json");
    private static final Path LINEUP = JsonSupport.resolveDataFile("data", "default.lineup");
    private static final Path JOURNAL = JsonSupport.resolveDataFile("data", "demo-journal");

    @TempDir
    Path workspace;

    private String previousWorkspace;
    private Catalog catalog;

    @BeforeEach
    void setUp() {
        previousWorkspace = Config.getWorkspacePath();
        Config.setWorkspacePath(workspace.toString());
        catalog = new Catalog(workspace);
    }

    @AfterEach
    void tearDown() {
        Config.setWorkspacePath(previousWorkspace);
    }

    private static List<Path> demoLogs() throws Exception {
        try (Stream<Path> files = Files.list(JOURNAL)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".csv")).sorted().toList();
        }
    }

    @Test
    @DisplayName("every team of the demo lineup belongs to a member of the demo guild")
    void lineupUsesGuildMembers() throws Exception {
        Guild guild = GuildRepository.load(GUILD, catalog);
        Lineup lineup = LineupRepository.load(LINEUP);
        Set<String> memberIds = guild.members().stream().map(GuildMember::id).collect(Collectors.toSet());

        assertEquals("demo", lineup.guildId());
        assertEquals("Demo", lineup.guildName());
        assertFalse(lineup.entries().isEmpty());
        for (Lineup.Entry entry : lineup.entries()) {
            assertTrue(memberIds.contains(entry.teamMemberId()), "not a member: " + entry.teamMemberId());
        }
    }

    @Test
    @DisplayName("the demo battle logs make an importable plan without errors, unknown names or player questions")
    void demoLogsAreImportable() throws Exception {
        Guild guild = GuildRepository.load(GUILD, catalog);
        Path guildDir = workspace.resolve("Demo");
        Files.createDirectories(guildDir);
        Path guildFile = guildDir.resolve(GuildService.GUILD_FILE_NAME);
        GuildRepository.save(guild, guildFile);
        AppContext context = new AppContext(catalog);
        context.set(guild, guildFile);

        List<Path> logs = demoLogs();
        assertEquals(2, logs.size());
        ImportPlan plan = new JournalImportService(context, new GuildService(context)).prepare(logs);

        assertTrue(plan.errors().isEmpty(), plan.errors().toString());
        assertTrue(plan.isImportable());
        assertEquals(1, plan.battles().size());
        assertEquals(1L, plan.ownGameGuildId());
        assertNull(plan.guildLink(), "the demo guild is linked already");
        assertTrue(plan.unknownNames().isEmpty(), plan.unknownNames().toString());
        assertTrue(plan.playerQuestions().isEmpty(), plan.playerQuestions().toString());
        assertFalse(Files.exists(guildDir.resolve("journal.mv.db")), "prepare writes nothing");

        // every defender whose (trimmed) name is a member's is assigned to that member
        Map<String, String> assigned = new HashMap<>();
        for (PlayerAutoAssignment a : plan.autoAssignments()) {
            assigned.put(a.rawName(), a.memberId());
        }
        Map<String, String> memberIdByName = new HashMap<>();
        guild.members().forEach(m -> memberIdByName.put(m.name().strip(), m.id()));
        BattleLogParser parser = BattleLogParser.createDefault();
        int defenders = 0;
        for (Path log : logs) {
            if (!log.getFileName().toString().endsWith("Defense Log.csv")) {
                continue;
            }
            for (Fight fight : parser.parse(log).log().fights()) {
                String name = fight.defender().playerName();
                String memberId = memberIdByName.get(name.strip());
                if (memberId != null) {
                    assertEquals(memberId, assigned.get(name), "defender not assigned: " + name);
                    defenders++;
                }
            }
        }
        assertTrue(defenders > 0, "the defense log has defenders of the demo guild");
    }
}
