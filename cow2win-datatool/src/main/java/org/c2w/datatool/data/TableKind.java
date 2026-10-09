package org.c2w.datatool.data;

import org.c2w.data.model.FortificationType;

import java.nio.file.Path;
import java.util.List;

/**
 * One editable system data file - one tab of the tool, in tab order. Catalog files hold the
 * entities (their display names live in the language files), the CowScore files hold the
 * fortification marks of one catalog, combos and templates refer to heroes and titans.
 */
public enum TableKind {
    HEROES("Heroes", "heroes.json", "heroes", ""),
    TITANS("Titans", "titans.json", "titans", ""),
    PETS("Pets", "pets.json", "pets", ""),
    WAR_FLAGS("War flags", "warFlags.json", "flags", "flag-"),
    FORTIFICATIONS("Fortifications", "fortifications.json", null, ""),
    HERO_COWSCORE("Hero CowScore", "cowScore.json", null, ""),
    TITAN_COWSCORE("Titan CowScore", "titanCowScore.json", null, ""),
    PET_COWSCORE("Pet CowScore", "petCowScore.json", null, ""),
    WAR_FLAG_COWSCORE("War flag CowScore", "warFlagCowScore.json", null, ""),
    HERO_COMBOS("Hero combos", "heroCombos.json", null, ""),
    TITAN_COMBOS("Titan combos", "titanCombos.json", null, ""),
    TITAN_TEMPLATES("Titan templates", "titanTemplates.json", null, "");

    /** The five catalog files whose change is recorded in {@code catalog-version.json}. */
    public static final List<TableKind> CATALOGS = List.of(HEROES, TITANS, PETS, WAR_FLAGS, FORTIFICATIONS);

    private final String title;
    private final String fileName;
    private final String imageFolder;
    private final String idPrefix;

    TableKind(String title, String fileName, String imageFolder, String idPrefix) {
        this.title = title;
        this.fileName = fileName;
        this.imageFolder = imageFolder;
        this.idPrefix = idPrefix;
    }

    public String title() {
        return title;
    }

    /** Path of the JSON file relative to the resources folder. */
    public Path relativePath() {
        return Path.of("data", fileName);
    }

    /** Folder below {@code images/} holding the avatars, or null if the entries have no image. */
    public String imageFolder() {
        return imageFolder;
    }

    /** Mandatory id prefix ({@code flag-} for war flags), empty otherwise. */
    public String idPrefix() {
        return idPrefix;
    }

    public boolean isCatalog() {
        return CATALOGS.contains(this);
    }

    /** The hero or titan combos - same format, only the members field and catalog differ. */
    public boolean isCombos() {
        return this == HERO_COMBOS || this == TITAN_COMBOS;
    }

    public boolean isCowScore() {
        return catalogOfCowScore() != null;
    }

    /** For a CowScore file: the catalog whose entries it marks; null otherwise. */
    public TableKind catalogOfCowScore() {
        return switch (this) {
            case HERO_COWSCORE -> HEROES;
            case TITAN_COWSCORE -> TITANS;
            case PET_COWSCORE -> PETS;
            case WAR_FLAG_COWSCORE -> WAR_FLAGS;
            default -> null;
        };
    }

    /** For a catalog with a CowScore file (all but fortifications): that file; null otherwise. */
    public TableKind cowScoreOfCatalog() {
        for (TableKind kind : values()) {
            if (kind.catalogOfCowScore() == this) {
                return kind;
            }
        }
        return null;
    }

    /**
     * For a CowScore file: the fortification type its marks refer to - as in the app's
     * CowScore panels: titan marks on titan fortifications, hero, pet and war flag marks on
     * hero fortifications.
     */
    public FortificationType markedFortificationType() {
        return this == TITAN_COWSCORE ? FortificationType.TITAN : FortificationType.HERO;
    }

    /** Field that identifies a row - {@code slot} for the titan templates, {@code id} otherwise. */
    public String keyField() {
        return this == TITAN_TEMPLATES ? Fields.SLOT : Fields.ID;
    }
}
