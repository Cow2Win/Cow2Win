package org.c2w.datatool.data;

import java.nio.file.Path;

/**
 * The three language files of the app. Display names of catalog entries live there with
 * the entry's id as key ({@code language/<folder>/<folder>.properties}).
 */
public enum Language {
    DE("deutsch", "Name DE"),
    EN("english", "Name EN"),
    FR("francais", "Name FR");

    private final String folder;
    private final String header;

    Language(String folder, String header) {
        this.folder = folder;
        this.header = header;
    }

    /** Column header in the catalog tabs. */
    public String header() {
        return header;
    }

    /** Row field holding the display name in this language, e.g. {@code name.de}. */
    public String field() {
        return "name." + name().toLowerCase();
    }

    /** Path of the language file relative to the resources folder. */
    public Path relativePath() {
        return Path.of("language", folder, folder + ".properties");
    }
}
