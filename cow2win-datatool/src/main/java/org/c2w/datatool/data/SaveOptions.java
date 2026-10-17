package org.c2w.datatool.data;

import java.time.LocalDate;
import java.util.Set;

/**
 * Answers given before saving.
 *
 * @param createCowScoreFor catalogs whose new entries also get an (empty) entry in their CowScore file
 * @param today             the date for a new {@code dataVersion} in {@code catalog/manifest.json}
 */
public record SaveOptions(Set<TableKind> createCowScoreFor, LocalDate today) {

    public SaveOptions {
        createCowScoreFor = Set.copyOf(createCowScoreFor);
    }
}
