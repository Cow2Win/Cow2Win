package org.c2w.datatool.data;

/** How a column is shown, edited and checked. */
public enum ColumnType {
    /** Free text. */
    TEXT,
    /** Whole number, optionally with bounds. */
    INTEGER,
    /** One value out of a fixed list (an enum's constants) - combo box. */
    ENUM,
    /** Several ids out of a catalog or enum - shown comma-separated, edited in a selection dialog. */
    ID_LIST,
    /** One id out of a catalog - combo box (the id column of the CowScore tabs). */
    ID_REF,
    /** Avatar file name with preview; edited by choosing a PNG. */
    IMAGE,
    /** Display name in one language - stored in the language file under the row's id. */
    NAME,
    /** Read-only display name of the entry referenced by the row's id. */
    DISPLAY_NAME,
    /** Fortification mark (empty, POSITIVE, NEGATIVE) of one fortification. */
    MARK
}
