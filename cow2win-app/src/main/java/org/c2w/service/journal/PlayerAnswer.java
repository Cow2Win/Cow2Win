package org.c2w.service.journal;

/**
 * The answer to a {@link PlayerQuestion}.
 *
 * @param kind     what to do
 * @param memberId the member for {@link Kind#ASSIGN} and {@link Kind#RENAME}, else {@code null}
 */
public record PlayerAnswer(Kind kind, String memberId) {

    /** What to do with the player. */
    public enum Kind {
        /** It is this existing member. */
        ASSIGN,
        /** It is this existing member, who renamed themselves in the game: only the member's name changes. */
        RENAME,
        /** Add a new member (without teams). */
        CREATE,
        /** Deliberately not maintained in Cow2Win - not asked again. */
        NOT_IN_COW2WIN,
        /** Former member - not asked again. */
        FORMER,
        /** Decide later - asked again on the next import (default). */
        OPEN
    }

    public PlayerAnswer {
        if (kind == null) {
            throw new IllegalArgumentException("PlayerAnswer needs a kind");
        }
        if ((kind == Kind.ASSIGN || kind == Kind.RENAME) == (memberId == null)) {
            throw new IllegalArgumentException(kind + (memberId == null ? " needs" : " takes no") + " member id");
        }
    }

    public static PlayerAnswer assign(String memberId) {
        return new PlayerAnswer(Kind.ASSIGN, memberId);
    }

    public static PlayerAnswer rename(String memberId) {
        return new PlayerAnswer(Kind.RENAME, memberId);
    }

    public static PlayerAnswer of(Kind kind) {
        return new PlayerAnswer(kind, null);
    }
}
