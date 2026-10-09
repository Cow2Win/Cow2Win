package org.c2w.gui.stage;

import org.c2w.data.journal.LogDirection;
import org.c2w.data.journal.db.BattleSummary;
import org.c2w.data.journal.db.JournalCounts;
import org.c2w.data.journal.db.JournalException;
import org.c2w.data.journal.db.JournalRepository;
import org.c2w.infra.Logger;
import org.c2w.service.JournalStore;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * GUI-free reading of the Weltenschlacht journal of the open guild for the info panel of
 * {@link InputStageView}: counts, the day of the newest defense log and the latest battles.
 * Never creates a journal.
 */
public final class JournalInfoModel {

    /** How many of the latest battles the info panel lists. */
    static final int RECENT_BATTLES = 3;

    private JournalInfoModel() {
    }

    public enum State {
        /** The guild has no journal yet. */
        NO_JOURNAL,
        /** The journal could not be read (details in the log). */
        ERROR,
        /** Read - see the values of {@link JournalInfo}. */
        LOADED
    }

    /**
     * What the journal section shows.
     *
     * @param newestDefense day of the newest battle with a defense log, null if there is none
     * @param recentBattles the latest battles, newest first (at most {@link #RECENT_BATTLES})
     */
    public record JournalInfo(State state, int battles, int logs, LocalDate newestDefense,
                              List<BattleSummary> recentBattles) {

        static JournalInfo of(State state) {
            return new JournalInfo(state, 0, 0, null, List.of());
        }
    }

    /** Reads the journal through {@code store} (e.g. {@code AppContext#journal()}). */
    public static JournalInfo load(JournalStore store) {
        try {
            Optional<JournalRepository> repository = store.repository(false);
            if (repository.isEmpty()) {
                return JournalInfo.of(State.NO_JOURNAL);
            }
            JournalCounts counts = repository.get().countAll();
            // listBattles is newest first (day, then id) - see JournalSyncService#newestDefenseBattleDate.
            List<BattleSummary> battles = repository.get().listBattles(null);
            LocalDate newestDefense = battles.stream()
                    .filter(b -> b.directions() != null && b.directions().contains(LogDirection.DEFENSE))
                    .map(BattleSummary::date)
                    .findFirst().orElse(null);
            return new JournalInfo(State.LOADED, counts.battles(), counts.logs(), newestDefense,
                    List.copyOf(battles.subList(0, Math.min(RECENT_BATTLES, battles.size()))));
        } catch (JournalException | RuntimeException e) {
            Logger.logException("Could not read the journal for the input stage view", e);
            return JournalInfo.of(State.ERROR);
        }
    }
}
