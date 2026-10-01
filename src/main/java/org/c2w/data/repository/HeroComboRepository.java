package org.c2w.data.repository;

import org.c2w.data.model.TeamCombos;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;

/**
 * The hero {@link org.c2w.data.model.TeamCombo}s of one workspace, from
 * {@code heroCombos.json} - see {@link TeamComboFiles} for the file format,
 * validation and how the shipped defaults are merged into the workspace
 * copy on every start. There is no in-app editor yet: the user maintains
 * the workspace copy by hand (a shipped combo that is edited or deactivated
 * has to get {@code "source": "USER"}, otherwise the next start replaces it).
 *
 * <p>The loaded combos are handed to {@code TeamScoreCalculator} at startup
 * (see {@code WorkspaceBootstrap}).
 */
public class HeroComboRepository {

    private static final String DEFAULTS_PATH = "/data/heroCombos.json";

    /** File name of the workspace copy - see {@link #comboFile()}. */
    public static final String FILE_NAME = "heroCombos.json";

    /** JSON field listing a hero combo's members. */
    static final String MEMBERS_FIELD = "heroIds";

    private final Path workspaceDir;
    private final TeamCombos combos;

    /**
     * Loads the combos right away: merges the shipped defaults into the
     * workspace copy in {@code workspaceDir} (creating it if needed) and
     * validates every combo against {@code knownHeroIds}. Never throws for a
     * missing or broken file - see {@link TeamComboFiles#loadWorkspace}.
     */
    public HeroComboRepository(Path workspaceDir, Collection<String> knownHeroIds) {
        if (workspaceDir == null) {
            throw new IllegalArgumentException("workspaceDir must not be null");
        }
        this.workspaceDir = workspaceDir;
        var entries = TeamComboFiles.loadWorkspace(comboFile(),
                TeamComboFiles.loadDefaults(HeroComboRepository.class, DEFAULTS_PATH));
        this.combos = TeamComboFiles.toCombos(entries, MEMBERS_FIELD, Set.copyOf(knownHeroIds), FILE_NAME);
    }

    /** All valid hero combos of the workspace, active and deactivated ones. */
    public TeamCombos combos() {
        return combos;
    }

    /** The workspace copy of {@code heroCombos.json} - directly in the workspace folder. */
    public Path comboFile() {
        return workspaceDir.resolve(FILE_NAME);
    }
}
