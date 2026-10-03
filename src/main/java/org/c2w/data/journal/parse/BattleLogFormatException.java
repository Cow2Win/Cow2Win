package org.c2w.data.journal.parse;

import java.io.IOException;

/**
 * A file that is not a Clash of Worlds battle log at all: empty, or without a
 * known column header row. Everything below that level is reported as a
 * {@link org.c2w.data.journal.ParseProblem} instead.
 */
public class BattleLogFormatException extends IOException {

    public BattleLogFormatException(String message) {
        super(message);
    }
}
