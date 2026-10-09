package org.c2w.service;

import org.c2w.data.journal.LogDirection;
import org.c2w.data.journal.db.JournalDatabase;
import org.c2w.data.journal.db.JournalRepository;
import org.c2w.data.journal.parse.BattleLogTestFiles;
import org.c2w.data.model.Guild;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Lifecycle of the journal connection, bound to the open guild (see {@link JournalService}). */
class JournalServiceTest extends ServiceTestSupport {

    @Test
    @DisplayName("The journal is opened lazily and only created when asked to")
    void lazyOpening() throws Exception {
        JournalService journal = context.journal();
        Path alpha = guildService.guildDir("Alpha");

        assertFalse(journal.isOpen());
        assertEquals(Optional.empty(), journal.repository(false));
        assertFalse(JournalDatabase.exists(alpha), "no file without createIfMissing");

        JournalRepository repository = journal.repository(true).orElseThrow();

        assertTrue(journal.isOpen());
        assertTrue(JournalDatabase.exists(alpha));
        assertEquals(Optional.of(alpha), journal.openGuildDir());
        assertSame(repository, journal.repository(false).orElseThrow(), "kept open, not reopened");
    }

    @Test
    @DisplayName("Switching the guild closes the journal; editing the same guild does not")
    void guildSwitchCloses() throws Exception {
        JournalService journal = context.journal();
        JournalRepository alphaRepository = journal.repository(true).orElseThrow();

        context.setGuild(new Guild(context.guild().id(), "Alpha edited", List.of()));
        assertTrue(journal.isOpen(), "in-memory edit of the same guild keeps the journal open");

        guildService.createGuild("Beta");
        guildService.switchToGuild("Beta");

        assertFalse(journal.isOpen());
        assertTrue(alphaRepository.database().isClosed());
        assertEquals(Optional.empty(), journal.repository(false), "Beta has no journal");
        assertEquals(guildService.guildDir("Beta"), journal.repository(true).orElseThrow().database().guildDir());
    }

    @Test
    @DisplayName("deleteGuild closes the open journal first, so the folder can be deleted")
    void deleteGuildClosesFirst() throws Exception {
        guildService.createGuild("Beta");
        guildService.switchToGuild("Beta");
        JournalRepository repository = context.journal().repository(true).orElseThrow();
        Path file = BattleLogTestFiles.file("de", "24-09-2026", LogDirection.ATTACK);
        repository.saveLog(BattleLogTestFiles.parse(file), Files.readAllBytes(file), null);
        Path beta = guildService.guildDir("Beta");

        guildService.deleteGuild("Beta");

        assertFalse(Files.exists(beta), "folder incl. journal.mv.db deleted");
        assertFalse(context.journal().isOpen());
        assertTrue(repository.database().isClosed());
    }

    @Test
    @DisplayName("Deleting another guild leaves the open journal alone")
    void deletingAnotherGuildKeepsTheJournal() throws Exception {
        context.journal().repository(true).orElseThrow();
        guildService.createGuild("Beta");

        guildService.deleteGuild("Beta");

        assertTrue(context.journal().isOpen());
    }

    @Test
    @DisplayName("closeCurrent and closeIfOpenFor")
    void explicitClose() throws Exception {
        JournalService journal = context.journal();
        journal.repository(true).orElseThrow();

        journal.closeIfOpenFor(guildService.guildDir("Other"));
        assertTrue(journal.isOpen());
        journal.closeIfOpenFor(guildService.guildDir("Alpha"));
        assertFalse(journal.isOpen());

        journal.repository(false).orElseThrow();
        journal.closeCurrent();
        journal.closeCurrent();
        assertFalse(journal.isOpen());
    }

    @Test
    @DisplayName("Without an open guild there is no journal")
    void noGuild() throws Exception {
        JournalService journal = new JournalService(() -> null);

        assertEquals(Optional.empty(), journal.repository(true));
    }
}
