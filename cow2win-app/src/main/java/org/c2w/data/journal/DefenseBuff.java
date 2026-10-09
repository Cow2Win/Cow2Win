package org.c2w.data.journal;

import org.c2w.data.model.BuffEffect;

/**
 * The fortification buff of a defender, e.g. {@code Rüstung erhöht (56%)}.
 *
 * @param effect  the effect, {@code null} if the text is unknown
 * @param percent the value in parentheses, {@code null} if there was none
 * @param rawText the whole cell exactly as in the log
 */
public record DefenseBuff(BuffEffect effect, Integer percent, String rawText) {
    public DefenseBuff {
        rawText = rawText == null ? "" : rawText;
    }
}
