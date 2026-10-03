package org.c2w.data.journal.db;

/** Status of an own-guild player in the journal with respect to the Cow2Win guild members. */
public enum AssignmentStatus {
    /** Linked to a Cow2Win guild member (by member id). */
    ASSIGNED,
    /** Deliberately not maintained in Cow2Win - not asked again. */
    NOT_IN_COW2WIN,
    /** Former guild member. */
    FORMER,
    /** Not decided yet - asked again on the next import. */
    OPEN
}
