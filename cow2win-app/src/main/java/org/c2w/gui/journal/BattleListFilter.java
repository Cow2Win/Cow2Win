package org.c2w.gui.journal;

import org.c2w.data.journal.BattleResult;
import org.c2w.data.journal.db.BattleStatus;
import org.c2w.data.journal.db.BattleSummary;

import java.util.Locale;

/**
 * The filters of the battle list - all combined with AND. Swing-free.
 *
 * @param seasonNumber only battles of this season (number, see {@code BattleSummary#seasonNumber}),
 *                     {@code null} = all
 * @param opponentText opponent name contains this text (case-insensitive), blank = all
 * @param result       victory / defeat / draw / all - a running battle has no result yet and
 *                     is only shown under "all"
 */
public record BattleListFilter(Integer seasonNumber, String opponentText, ResultFilter result) {

    /** Result choices. */
    public enum ResultFilter {ALL, WIN, LOSS, DRAW}

    /** No filter at all. */
    public static final BattleListFilter NONE = new BattleListFilter(null, "", ResultFilter.ALL);

    public BattleListFilter {
        opponentText = opponentText == null ? "" : opponentText;
        result = result == null ? ResultFilter.ALL : result;
    }

    public BattleListFilter withSeason(Integer newSeasonNumber) {
        return new BattleListFilter(newSeasonNumber, opponentText, result);
    }

    public BattleListFilter withOpponentText(String text) {
        return new BattleListFilter(seasonNumber, text, result);
    }

    public BattleListFilter withResult(ResultFilter newResult) {
        return new BattleListFilter(seasonNumber, opponentText, newResult);
    }

    /** True if the battle passes every filter. */
    public boolean matches(BattleSummary b) {
        if (seasonNumber != null && !seasonNumber.equals(b.seasonNumber())) {
            return false;
        }
        String text = opponentText.strip().toLowerCase(Locale.ROOT);
        if (!text.isEmpty() && !b.opponent().name().toLowerCase(Locale.ROOT).contains(text)) {
            return false;
        }
        boolean running = b.status() == BattleStatus.RUNNING;
        return switch (result) {
            case ALL -> true;
            case WIN -> !running && b.result() == BattleResult.WIN;
            case LOSS -> !running && b.result() == BattleResult.LOSS;
            case DRAW -> !running && b.result() == BattleResult.DRAW;
        };
    }
}
