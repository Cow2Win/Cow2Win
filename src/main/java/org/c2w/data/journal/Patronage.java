package org.c2w.data.journal;

/**
 * The "Patronat"/"Patronage" column of a hero row: the pet that patronizes the hero.
 *
 * @param petId   catalog id of the pet, {@code null} if the name is unknown
 * @param petName raw pet name from the log
 * @param power   patronage power
 */
public record Patronage(String petId, String petName, long power) {
    public Patronage {
        petName = petName == null ? "" : petName;
    }
}
