package org.c2w.datatool.data;

import org.c2w.datatool.RepositoryFolder;

import java.nio.file.Path;

/**
 * The languages of the app. Display names of catalog entries live in {@code catalog/names.json}
 * (keyed by id and {@link #folder()}), the UI texts in {@code language/<folder>/<folder>.properties}.
 */
public enum Language {
    DE("deutsch", "Name DE", true),
    EN("english", "Name EN", true),
    /**
     * Optional: a name only seen in no Spanish battle log yet stays empty (the app shows the English
     * one) instead of being guessed.
     */
    ES("espanol", "Name ES", false),
    FR("francais", "Name FR", true);

    private final String folder;
    private final String header;
    private final boolean nameRequired;

    Language(String folder, String header, boolean nameRequired) {
        this.folder = folder;
        this.header = header;
        this.nameRequired = nameRequired;
    }

    /** Name of the language folder - also the key of this language in {@code catalog/names.json}. */
    public String folder() {
        return folder;
    }

    /** Column header in the catalog tabs. */
    public String header() {
        return header;
    }

    /**
     * True if every catalog entry must have a name in this language (an empty one is an error);
     * false if it may be empty (a warning - the app falls back to the English name).
     */
    public boolean nameRequired() {
        return nameRequired;
    }

    /** Row field holding the display name in this language, e.g. {@code name.de}. */
    public String field() {
        return "name." + name().toLowerCase();
    }

    /** Path of the language file relative to the repository root (in the app resources). */
    public Path relativePath() {
        return RepositoryFolder.APP_RESOURCES.resolve(Path.of("language", folder, folder + ".properties"));
    }
}
