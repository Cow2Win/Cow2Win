package org.c2w.data.repository;

import org.c2w.data.model.Hero;

import java.nio.file.Path;

/**
 * The catalogs whose CowScores live in the workspace - heroes, titans, pets
 * and war flags, plus the hero combos and the hero/titan team templates -
 * loaded together for one workspace folder. Created once at startup (see
 * {@code WorkspaceBootstrap#start}) and reachable through {@code
 * AppContext#catalog()}; tests simply create their own for a temp folder.
 *
 * <p>{@link FortificationRepository} is not part of this: fortifications are
 * pure classpath data without a workspace copy, so there is nothing to
 * isolate or reload there.
 */
public final class Catalog {

    private final HeroRepository heroes;
    private final TitanRepository titans;
    private final PetRepository pets;
    private final WarFlagRepository warFlags;
    private final HeroComboRepository heroCombos;
    private final TeamTemplateRepository heroTemplates;
    private final TeamTemplateRepository titanTemplates;

    /**
     * Loads all four catalogs, with their CowScores from (and, if missing, created in) {@code workspaceDir},
     * and the hero combos (validated against the hero catalog) and the hero/titan team templates
     * (the titan templates' workspace file is created from the shipped defaults if missing).
     */
    public Catalog(Path workspaceDir) {
        this(workspaceDir, new HeroRepository(workspaceDir));
    }

    private Catalog(Path workspaceDir, HeroRepository heroes) {
        this(heroes, new TitanRepository(workspaceDir), new PetRepository(workspaceDir),
                new WarFlagRepository(workspaceDir),
                new HeroComboRepository(workspaceDir, heroes.findAll().stream().map(Hero::id).toList()),
                TeamTemplateRepository.forHeroes(workspaceDir), TeamTemplateRepository.forTitans(workspaceDir));
    }

    public Catalog(HeroRepository heroes, TitanRepository titans, PetRepository pets, WarFlagRepository warFlags,
                   HeroComboRepository heroCombos, TeamTemplateRepository heroTemplates,
                   TeamTemplateRepository titanTemplates) {
        if (heroes == null || titans == null || pets == null || warFlags == null || heroCombos == null
                || heroTemplates == null || titanTemplates == null) {
            throw new IllegalArgumentException("Catalog needs all seven repositories");
        }
        this.heroes = heroes;
        this.titans = titans;
        this.pets = pets;
        this.warFlags = warFlags;
        this.heroCombos = heroCombos;
        this.heroTemplates = heroTemplates;
        this.titanTemplates = titanTemplates;
    }

    public HeroRepository heroes() {
        return heroes;
    }

    public TitanRepository titans() {
        return titans;
    }

    public PetRepository pets() {
        return pets;
    }

    public WarFlagRepository warFlags() {
        return warFlags;
    }

    public HeroComboRepository heroCombos() {
        return heroCombos;
    }

    /** The workspace-wide hero team templates (F1-F5 in a hero team row). */
    public TeamTemplateRepository heroTemplates() {
        return heroTemplates;
    }

    /** The workspace-wide titan team templates (F1-F5 in a titan team row). */
    public TeamTemplateRepository titanTemplates() {
        return titanTemplates;
    }
}
