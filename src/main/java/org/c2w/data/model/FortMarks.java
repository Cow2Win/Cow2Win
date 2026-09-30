package org.c2w.data.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The manually curated {@link FortMark}s of one hero, pet or war flag, keyed
 * by {@link Fortification#id()} - replaces {@link CowScore} for these three
 * entity types (CowScore concept of 2026-09-30). Sparse by design: a
 * fortification without an entry is "neutral" (unmarked).
 *
 * <p>Persisted in {@code cowScore.json} (heroes), {@code petCowScore.json}
 * and {@code warFlagCowScore.json}, see {@code FortMarkFiles}.
 *
 * @param marks fortification id -&gt; mark; never null, unmodifiable
 */
public record FortMarks(Map<String, FortMark> marks) {

    /** No marks at all - the default for every entity without a deliberate assessment. */
    public static final FortMarks NONE = new FortMarks(null);

    public FortMarks {
        marks = marks == null ? Map.of() : Map.copyOf(marks);
    }

    /** The mark for the given fortification, or null if this entity is unmarked (neutral) there. */
    public FortMark markFor(String fortificationId) {
        return marks.get(fortificationId);
    }

    /** True if this entity carries {@link FortMark#POSITIVE} for the given fortification. */
    public boolean isPositive(String fortificationId) {
        return marks.get(fortificationId) == FortMark.POSITIVE;
    }

    /** True if this entity carries {@link FortMark#NEGATIVE} for the given fortification. */
    public boolean isNegative(String fortificationId) {
        return marks.get(fortificationId) == FortMark.NEGATIVE;
    }

    /** True if there is no mark at all. */
    public boolean isEmpty() {
        return marks.isEmpty();
    }

    /** A copy with the mark for {@code fortificationId} set to {@code mark} - or removed if {@code mark} is null. */
    public FortMarks with(String fortificationId, FortMark mark) {
        Map<String, FortMark> copy = new LinkedHashMap<>(marks);
        if (mark == null) {
            copy.remove(fortificationId);
        } else {
            copy.put(fortificationId, mark);
        }
        return new FortMarks(copy);
    }
}
