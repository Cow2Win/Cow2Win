package org.c2w.service;

import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.Catalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link AppContext}'s change notifications and dirty state. */
class AppContextTest {

    private static final Path GUILD_A = Path.of("a", "guild.json");
    private static final Path GUILD_B = Path.of("b", "guild.json");
    private static final Path LINEUP_A = Path.of("a", "default.lineup");
    private static final Path LINEUP_B = Path.of("b", "default.lineup");

    /** Records every callback as "guild"/"lineup"/"dirty", in order. */
    private static final class RecordingListener implements AppContext.Listener {
        final List<String> events = new ArrayList<>();

        @Override
        public void guildChanged() {
            events.add("guild");
        }

        @Override
        public void lineupChanged() {
            events.add("lineup");
        }

        @Override
        public void dirtyStateChanged() {
            events.add("dirty");
        }
    }

    private static Guild guild(String id) {
        return new Guild(id, id, List.of());
    }

    private static Lineup lineup(String guildId) {
        return new Lineup(guildId, guildId, "", LocalDateTime.now(), List.of());
    }

    @TempDir
    Path workspace;

    private AppContext openContext() {
        AppContext context = new AppContext(new Catalog(workspace));
        context.set(guild("a"), GUILD_A);
        context.set(lineup("a"), LINEUP_A);
        return context;
    }

    @Test
    @DisplayName("every setter notifies the matching listener callback")
    void settersNotifyListeners() {
        AppContext context = openContext();
        RecordingListener listener = new RecordingListener();
        context.addListener(listener);

        context.setGuild(guild("a2"));
        context.setLineup(lineup("a2"));
        context.set(guild("b"), GUILD_B);
        context.set(lineup("b"), LINEUP_B);

        assertEquals(List.of("guild", "lineup", "guild", "lineup"), listener.events);
    }

    @Test
    @DisplayName("switchTo only notifies once guild and lineup are both replaced")
    void switchToNotifiesWithConsistentState() {
        AppContext context = openContext();
        List<Path> lineupPathsSeenOnGuildChange = new ArrayList<>();
        context.addListener(new AppContext.Listener() {
            @Override
            public void guildChanged() {
                lineupPathsSeenOnGuildChange.add(context.lineupFilePath());
            }
        });

        context.switchTo(guild("b"), GUILD_B, lineup("b"), LINEUP_B);

        assertEquals(List.of(LINEUP_B), lineupPathsSeenOnGuildChange);
        assertEquals("b", context.guild().id());
        assertEquals(GUILD_B, context.guildFilePath());
    }

    @Test
    @DisplayName("dirty flags notify only when they actually change")
    void dirtyFlagsNotifyOnChangeOnly() {
        AppContext context = openContext();
        RecordingListener listener = new RecordingListener();
        context.addListener(listener);

        assertFalse(context.hasUnsavedChanges());
        context.setLineupDirty(true);
        context.setLineupDirty(true);
        assertTrue(context.isLineupDirty());
        assertTrue(context.hasUnsavedChanges());

        context.setGuildDirty(true);
        context.setLineupDirty(false);
        assertTrue(context.hasUnsavedChanges());
        context.setGuildDirty(false);
        assertFalse(context.hasUnsavedChanges());

        assertEquals(List.of("dirty", "dirty", "dirty", "dirty"), listener.events);
    }

    @Test
    @DisplayName("a removed listener is no longer notified")
    void removedListenerIsNotNotified() {
        AppContext context = openContext();
        RecordingListener listener = new RecordingListener();
        context.addListener(listener);
        context.removeListener(listener);

        context.setGuild(guild("a2"));

        assertTrue(listener.events.isEmpty());
    }
}
