package org.c2w.data.repository;

import com.google.gson.JsonArray;
import org.c2w.data.model.TeamCombo;
import org.c2w.data.model.TeamCombos;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The {@link org.c2w.data.model.TeamCombo}s of one kind (heroes or titans) of
 * one workspace, from {@code heroCombos.json} / {@code titanCombos.json} -
 * see {@link TeamComboFiles} for the file format, validation and how the
 * shipped defaults are merged into the workspace copy on every start. Use
 * {@link #forHeroes}/{@link #forTitans}; the two only differ in file name,
 * members field and shipped defaults. Maintained in the "Hero combos" /
 * "Titan combos" tabs of the CowScore dialog ({@code TeamComboPanel}), which
 * save via {@link #save}; the files can still be edited by hand (a shipped
 * combo that is edited or deactivated has to get {@code "source": "USER"},
 * otherwise the next start replaces it).
 *
 * <p>The loaded combos are handed to {@code TeamScoreCalculator} at startup
 * (see {@code WorkspaceBootstrap}) and again after every {@link #save} by the dialog.
 */
public class TeamComboRepository {

    /** File name of the hero combos' workspace copy. */
    public static final String HERO_FILE_NAME = "heroCombos.json";

    /** File name of the titan combos' workspace copy. */
    public static final String TITAN_FILE_NAME = "titanCombos.json";

    /** JSON field listing a hero combo's members. */
    static final String HERO_MEMBERS_FIELD = "heroIds";

    /** JSON field listing a titan combo's members. */
    static final String TITAN_MEMBERS_FIELD = "titanIds";

    private final Path workspaceDir;
    private final String fileName;
    private final String membersField;
    private final String defaultsPath;
    private final Set<String> knownMemberIds;
    private TeamCombos combos;

    /**
     * Loads the combos right away: merges the shipped defaults into the
     * workspace copy in {@code workspaceDir} (creating it if needed) and
     * validates every combo against {@code knownMemberIds}. Never throws for a
     * missing or broken file - see {@link TeamComboFiles#loadWorkspace}.
     */
    private TeamComboRepository(Path workspaceDir, String fileName, String membersField,
                                Collection<String> knownMemberIds) {
        if (workspaceDir == null) {
            throw new IllegalArgumentException("workspaceDir must not be null");
        }
        this.workspaceDir = workspaceDir;
        this.fileName = fileName;
        this.membersField = membersField;
        this.defaultsPath = "/data/" + fileName;
        this.knownMemberIds = Set.copyOf(knownMemberIds);
        var entries = TeamComboFiles.loadWorkspace(comboFile(), loadDefaults());
        this.combos = TeamComboFiles.toCombos(entries, membersField, this.knownMemberIds, fileName);
    }

    /** The hero combos of {@code workspaceDir} ({@code heroCombos.json}), validated against {@code knownHeroIds}. */
    public static TeamComboRepository forHeroes(Path workspaceDir, Collection<String> knownHeroIds) {
        return new TeamComboRepository(workspaceDir, HERO_FILE_NAME, HERO_MEMBERS_FIELD, knownHeroIds);
    }

    /** The titan combos of {@code workspaceDir} ({@code titanCombos.json}), validated against {@code knownTitanIds}. */
    public static TeamComboRepository forTitans(Path workspaceDir, Collection<String> knownTitanIds) {
        return new TeamComboRepository(workspaceDir, TITAN_FILE_NAME, TITAN_MEMBERS_FIELD, knownTitanIds);
    }

    /** All valid combos of the workspace, active and deactivated ones. */
    public synchronized TeamCombos combos() {
        return combos;
    }

    /** The shipped combos (all with source {@code C2W}) - what "restore defaults" goes back to. Only reads. */
    public TeamCombos defaultCombos() {
        return TeamComboFiles.defaultCombos(loadDefaults(), membersField, knownMemberIds, fileName);
    }

    /**
     * Writes {@code newCombos} as the workspace copy (after a {@code .before-update-<date>.bak}
     * backup of the old one) and makes them this repository's combos.
     *
     * @throws IOException if the backup or the write failed - then nothing changed
     */
    public synchronized void save(List<TeamCombo> newCombos) throws IOException {
        TeamComboFiles.save(comboFile(), newCombos, membersField);
        combos = new TeamCombos(newCombos);
    }

    /** The workspace copy of the combo file - directly in the workspace folder. */
    public Path comboFile() {
        return workspaceDir.resolve(fileName);
    }

    /** The file name of the combo file, e.g. {@value #HERO_FILE_NAME}. */
    public String fileName() {
        return fileName;
    }

    private JsonArray loadDefaults() {
        return TeamComboFiles.loadDefaults(TeamComboRepository.class, defaultsPath);
    }
}
