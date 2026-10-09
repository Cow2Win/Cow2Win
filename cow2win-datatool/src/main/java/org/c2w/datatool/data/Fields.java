package org.c2w.datatool.data;

/** Row field names. Most are the JSON keys; buff fields are flattened, marks are per fortification. */
public final class Fields {

    public static final String ID = "id";
    public static final String IMAGE = "image";
    public static final String ROLES = "roles";
    public static final String ELEMENT = "element";
    public static final String SUPER_TITAN = "superTitan";
    public static final String TYPE = "type";
    public static final String CAPACITY = "capacity";
    public static final String CAPTURE_BONUS = "captureBonus";
    public static final String ROW = "row";
    public static final String COLUMN = "column";
    public static final String BUFF = "buff";
    public static final String BUFF_KIND = "buff.kind";
    public static final String BUFF_EFFECT = "buff.effect";
    public static final String BUFF_PERCENT = "buff.bonusPercent";
    public static final String BUFF_ROLE = "buff.role";
    public static final String BUFF_ELEMENT = "buff.element";
    public static final String PREREQUISITES = "prerequisites";
    public static final String STRATEGIC_IMPORTANCE = "strategicImportance";
    public static final String FORT_MARKS = "fortMarks";
    public static final String HERO_IDS = "heroIds";
    public static final String TITAN_IDS = "titanIds";
    public static final String NAME = "name";
    public static final String SOURCE = "source";
    public static final String DEACTIVATED = "deactivated";
    public static final String SLOT = "slot";
    /** Read-only display name column of the CowScore tabs. */
    public static final String DISPLAY_NAME = "displayName";

    /** Prefix of the per-fortification mark fields of the CowScore tabs. */
    public static final String MARK_PREFIX = "mark:";

    public static final String BUFF_KIND_ROLE = "ROLE";
    public static final String BUFF_KIND_ELEMENT = "ELEMENT";

    private Fields() {
    }

    public static String mark(String fortificationId) {
        return MARK_PREFIX + fortificationId;
    }
}
