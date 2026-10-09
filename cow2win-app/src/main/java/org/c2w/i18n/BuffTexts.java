package org.c2w.i18n;

import org.c2w.data.model.Buff;
import org.c2w.data.model.ElementBuff;
import org.c2w.data.model.RoleBuff;

/**
 * Builds the localized, human-readable description of a fortification
 * {@link Buff} (e.g. "Armor increase per Mage hero") from its structured
 * fields - the {@link Buff#effect()} plus the required role
 * ({@link RoleBuff}) or titan element ({@link ElementBuff}) - via
 * {@link LanguageService}. Used everywhere a buff is shown in the UI and in
 * the report, so the text always follows the configured language.
 */
public final class BuffTexts {

    private BuffTexts() {
        // Utility class, no instantiation
    }

    /** Localized description of {@code buff}, or {@code ""} for {@code null}. */
    public static String describe(Buff buff) {
        if (buff == null) {
            return "";
        }
        String effect = LanguageService.displayName("buffEffect." + buff.effect().name());
        if (buff instanceof RoleBuff roleBuff) {
            return LanguageService.displayName("buff.perRole", effect,
                    LanguageService.displayName("role." + roleBuff.role().name()));
        }
        ElementBuff elementBuff = (ElementBuff) buff;
        return LanguageService.displayName("buff.perElement", effect,
                LanguageService.displayName("titanElement." + elementBuff.element().name()));
    }
}
