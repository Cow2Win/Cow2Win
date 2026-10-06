package org.c2w.data.repository;

import org.c2w.data.model.TeamCombo;
import org.c2w.data.model.TeamCombos;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The hero {@link org.c2w.data.model.TeamCombo}s of one workspace, from
 * {@code heroCombos.json} - see {@link TeamComboFiles} for the file format,
 * validation and how the shipped defaults are merged into the workspace
 * copy on every start. Maintained in the "Hero combos" tab of the CowScore
 * dialog ({@code HeroComboPanel}), which saves via {@link #save}; the file can
 * still be edited by hand (a shipped combo that is edited or deactivated has to
 * get {@code "source": "USER"}, otherwise the next start replaces it).
 *
 * <p>The loaded combos are handed to {@code TeamScoreCalculator} at startup
 * (see {@code WorkspaceBootstrap}) and again after every {@link #save} by the dialog.
 */
public class HeroComboRepository {

    private static final String DEFAULTS_PATH = "/data/heroCombos.json";

    /** File name of the workspace copy - see {@link #comboFile()}. */
    public static final String FILE_NAME = "heroCombos.json";

    /** JSON field listing a hero combo's members. */
    static final String MEMBERS_FIELD = "heroIds";

    private final Path workspaceDir;
    private final Set<String> knownHeroIds;
    private TeamCombos combos;

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
        this.knownHeroIds = Set.copyOf(knownHeroIds);
        var entries = TeamComboFiles.loadWorkspace(comboFile(),
                TeamComboFiles.loadDefaults(HeroComboRepository.class, DEFAULTS_PATH));
        this.combos = TeamComboFiles.toCombos(entries, MEMBERS_FIELD, this.knownHeroIds, FILE_NAME);
    }

    /** All valid hero combos of the workspace, active and deactivated ones. */
    public synchronized TeamCombos combos() {
        return combos;
    }

    /** The shipped combos (all with source {@code C2W}) - what "restore defaults" goes back to. Only reads. */
    public TeamCombos defaultCombos() {
        return TeamComboFiles.defaultCombos(TeamComboFiles.loadDefaults(HeroComboRepository.class, DEFAULTS_PATH),
                MEMBERS_FIELD, knownHeroIds, FILE_NAME);
    }

    /**
     * Writes {@code newCombos} as the workspace copy (after a {@code .before-update-<date>.bak}
     * backup of the old one) and makes them this repository's combos.
     *
     * @throws IOException if the backup or the write failed - then nothing changed
     */
    public synchronized void save(List<TeamCombo> newCombos) throws IOException {
        TeamComboFiles.save(comboFile(), newCombos, MEMBERS_FIELD);
        combos = new TeamCombos(newCombos);
    }

    /** The workspace copy of {@code heroCombos.json} - directly in the workspace folder. */
    public Path comboFile() {
        return workspaceDir.resolve(FILE_NAME);
    }
}
