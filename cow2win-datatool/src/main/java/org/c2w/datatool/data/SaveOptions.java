package org.c2w.datatool.data;

import java.time.LocalDate;
import java.util.Set;

/**
 * Answers given before saving.
 *
 * @param createCowScoreFor catalogs whose new entries also get an (empty) entry in their CowScore file
 * @param setDataVersion    set {@code dataVersion} in {@code catalog-version.json} if a catalog file changes
 * @param today             the date for {@code dataVersion}
 */
public record SaveOptions(Set<TableKind> createCowScoreFor, boolean setDataVersion, LocalDate today) {

    public SaveOptions {
        createCowScoreFor = Set.copyOf(createCowScoreFor);
    }
}
