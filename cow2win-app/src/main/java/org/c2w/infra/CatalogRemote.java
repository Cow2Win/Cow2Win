package org.c2w.infra;

import java.io.IOException;

/**
 * Where a newer game catalog is downloaded from - the published {@code catalog/} folder on
 * GitHub ({@link GitHubCatalogRemote}), or a local folder in tests. Paths as in the manifest,
 * relative to the catalog root with {@code /}, e.g. {@code manifest.json} or {@code data/heroes.json}.
 */
@FunctionalInterface
public interface CatalogRemote {

    /**
     * The content of one published catalog file.
     *
     * @throws IOException with a one-sentence reason (no network, HTTP status, ...) if it cannot be fetched
     */
    byte[] fetch(String path) throws IOException;
}
