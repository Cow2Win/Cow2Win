package org.c2w.data.journal;

import org.c2w.data.model.HeroColor;
import org.c2w.data.model.TitanElement;

/**
 * One unit row of a team in a single fight: hero, pet, titan or totem.
 * Formats in the log:
 * <ul>
 *   <li>hero / pet: {@code Name | Color[ +n] | n stars | Level | Power}</li>
 *   <li>titan: {@code Name | n stars | Level | Power} (twin titans are one unit)</li>
 *   <li>totem: {@code Name | n stars | Level} (no power)</li>
 * </ul>
 * followed by damage dealt, damage taken, healing and - for heroes - the patronage.
 *
 * @param kind         hero, pet, titan or totem
 * @param name         raw name from the log
 * @param catalogId    hero/pet/titan id, {@code null} for a totem or an unknown name
 * @param totemElement totem only: its element, {@code null} if unknown or not a totem
 * @param color        hero/pet only: the color, {@code null} if unknown or not a hero/pet
 * @param colorText    hero/pet only: raw color text including the plus level (e.g. "Rot +2"), else {@code ""}
 * @param colorLevel   hero/pet only: the plus level ({@code 0} without one)
 * @param stars        stars
 * @param level        level
 * @param power        power, {@code null} for a totem
 * @param damageDealt  damage dealt
 * @param damageTaken  damage taken
 * @param healing      healing
 * @param patronage    hero only: the patronage pet, {@code null} if the column is empty
 */
public record FightUnit(
        UnitKind kind,
        String name,
        String catalogId,
        TitanElement totemElement,
        HeroColor color,
        String colorText,
        int colorLevel,
        int stars,
        int level,
        Integer power,
        int damageDealt,
        int damageTaken,
        int healing,
        Patronage patronage
) {
    public FightUnit {
        if (kind == null) {
            throw new IllegalArgumentException("FightUnit needs a kind");
        }
        name = name == null ? "" : name;
        colorText = colorText == null ? "" : colorText;
    }

    /** True if the name could be mapped to the catalog (id, or element for a totem). */
    public boolean isResolved() {
        return kind == UnitKind.TOTEM ? totemElement != null : catalogId != null;
    }
}
