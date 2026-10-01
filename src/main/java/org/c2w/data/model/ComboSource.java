package org.c2w.data.model;

/**
 * Where a {@link TeamCombo} comes from - decides how the start-up merge of
 * the shipped defaults into the workspace file treats it (see {@code
 * TeamComboFiles#merge}).
 */
public enum ComboSource {

    /**
     * Shipped with Cow2Win: replaced by the current default version (by id)
     * on every start, and removed again once it is no longer shipped.
     */
    C2W,

    /**
     * Added or adapted by the user: never touched by the merge. A shipped
     * combo that is edited or deactivated by hand must be switched to this
     * source - otherwise the next start replaces it with the default again.
     */
    USER
}
