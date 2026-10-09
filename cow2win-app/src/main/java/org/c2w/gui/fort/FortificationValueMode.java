package org.c2w.gui.fort;

import org.c2w.i18n.LanguageService;

/**
 * What the tiles of the {@link FortificationMapPanel} show as their values - chosen in the
 * "fortification values" combo box of the concept stage's action list.
 */
public enum FortificationValueMode {

    /** The current total power and buff percentage of each fortification. */
    POWER("fortificationMap.valueMode.power"),
    /** The change since the lineup was loaded or last saved (see AppContext#fortificationDiffFromLoaded). */
    CHANGES("fortificationMap.valueMode.changes"),
    /** The change against the guild's live lineup - against an empty lineup if there is none. */
    LIVE_COMPARISON("fortificationMap.valueMode.live");

    private final String textKey;

    FortificationValueMode(String textKey) {
        this.textKey = textKey;
    }

    /** True if the tiles show a change instead of the current value. */
    public boolean showsChange() {
        return this != POWER;
    }

    /** Display name in the configured language - shown in the combo box. */
    @Override
    public String toString() {
        return LanguageService.displayName(textKey);
    }
}
