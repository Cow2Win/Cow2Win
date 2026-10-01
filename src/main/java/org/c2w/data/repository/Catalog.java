package org.c2w.data.repository;

import java.nio.file.Path;

/**
 * The catalogs whose CowScores live in the workspace - heroes, titans, pets
 * and war flags - loaded together for one workspace folder. Created once at
 * startup (see {@code WorkspaceBootstrap#start}) and reachable through {@code
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

    /** Loads all four catalogs, with their CowScores from (and, if missing, created in) {@code workspaceDir}. */
    public Catalog(Path workspaceDir) {
        this(new HeroRepository(workspaceDir), new TitanRepository(workspaceDir),
                new PetRepository(workspaceDir), new WarFlagRepository(workspaceDir));
    }

    public Catalog(HeroRepository heroes, TitanRepository titans, PetRepository pets, WarFlagRepository warFlags) {
        if (heroes == null || titans == null || pets == null || warFlags == null) {
            throw new IllegalArgumentException("Catalog needs all four repositories");
        }
        this.heroes = heroes;
        this.titans = titans;
        this.pets = pets;
        this.warFlags = warFlags;
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
}
