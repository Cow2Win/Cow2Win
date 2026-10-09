package org.c2w.gui.journal;

import org.c2w.data.journal.BattleResult;
import org.c2w.data.journal.db.BattleStatus;
import org.c2w.data.journal.db.BattleSummary;

import java.time.LocalDate;
import java.util.Locale;

/**
 * The filters of the battle list - all combined with AND. Swing-free.
 *
 * @param seasonId     only battles of this season, {@code null} = all
 * @param opponentText opponent name contains this text (case-insensitive), blank = all
 * @param from         battle day on or after, {@code null} = open
 * @param to           battle day on or before, {@code null} = open
 * @param status       running / finished / all
 * @param result       victory / defeat / draw / all - a running battle has no result yet
 */
public record BattleListFilter(Integer seasonId, String opponentText, LocalDate from, LocalDate to,
                               StatusFilter status, ResultFilter result) {

    /** Status choices. */
    public enum StatusFilter {ALL, RUNNING, FINISHED}

    /** Result choices. */
    public enum ResultFilter {ALL, WIN, LOSS, DRAW}

    /** No filter at all. */
    public static final BattleListFilter NONE = new BattleListFilter(null, "", null, null, StatusFilter.ALL,
            ResultFilter.ALL);

    public BattleListFilter {
        opponentText = opponentText == null ? "" : opponentText;
        status = status == null ? StatusFilter.ALL : status;
        result = result == null ? ResultFilter.ALL : result;
    }

    public BattleListFilter withSeason(Integer newSeasonId) {
        return new BattleListFilter(newSeasonId, opponentText, from, to, status, result);
    }

    public BattleListFilter withOpponentText(String text) {
        return new BattleListFilter(seasonId, text, from, to, status, result);
    }

    public BattleListFilter withPeriod(LocalDate newFrom, LocalDate newTo) {
        return new BattleListFilter(seasonId, opponentText, newFrom, newTo, status, result);
    }

    public BattleListFilter withStatus(StatusFilter newStatus) {
        return new BattleListFilter(seasonId, opponentText, from, to, newStatus, result);
    }

    public BattleListFilter withResult(ResultFilter newResult) {
        return new BattleListFilter(seasonId, opponentText, from, to, status, newResult);
    }

    /** True if the battle passes every filter. */
    public boolean matches(BattleSummary b) {
        if (seasonId != null && !seasonId.equals(b.seasonId())) {
            return false;
        }
        String text = opponentText.strip().toLowerCase(Locale.ROOT);
        if (!text.isEmpty() && !b.opponent().name().toLowerCase(Locale.ROOT).contains(text)) {
            return false;
        }
        if (from != null && b.date().isBefore(from) || to != null && b.date().isAfter(to)) {
            return false;
        }
        boolean running = b.status() == BattleStatus.RUNNING;
        if (status == StatusFilter.RUNNING && !running || status == StatusFilter.FINISHED && running) {
            return false;
        }
        return switch (result) {
            case ALL -> true;
            case WIN -> !running && b.result() == BattleResult.WIN;
            case LOSS -> !running && b.result() == BattleResult.LOSS;
            case DRAW -> !running && b.result() == BattleResult.DRAW;
        };
    }
}
