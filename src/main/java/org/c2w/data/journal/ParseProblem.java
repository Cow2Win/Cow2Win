package org.c2w.data.journal;

/**
 * Something the battle log parser could not (fully) understand - collected
 * instead of aborting, so a changed export format or a new name shows up
 * without losing the rest of the log.
 *
 * @param lineNumber 1-based line in the file; {@code 0} for the file name
 * @param line       the raw line (or file name)
 * @param reason     what was wrong, in English
 */
public record ParseProblem(int lineNumber, String line, String reason) {
    public ParseProblem {
        line = line == null ? "" : line;
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("ParseProblem needs a reason");
        }
    }

    @Override
    public String toString() {
        return "line " + lineNumber + ": " + reason + " [" + line + "]";
    }
}
