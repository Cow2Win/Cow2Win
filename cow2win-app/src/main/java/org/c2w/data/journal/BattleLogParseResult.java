package org.c2w.data.journal;

import java.util.List;

/**
 * Result of parsing one battle log file: the log plus everything that could
 * not be (fully) understood. Depending on the problem, the affected row is
 * skipped or kept with a {@code null} id - see {@code BattleLogParser}.
 */
public record BattleLogParseResult(BattleLog log, List<ParseProblem> problems) {
    public BattleLogParseResult {
        if (log == null) {
            throw new IllegalArgumentException("BattleLogParseResult needs a log");
        }
        problems = problems == null ? List.of() : List.copyOf(problems);
    }

    /** True if the file was understood completely. */
    public boolean isClean() {
        return problems.isEmpty();
    }
}
