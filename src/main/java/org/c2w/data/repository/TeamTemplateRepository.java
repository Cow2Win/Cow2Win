package org.c2w.data.repository;

import org.c2w.data.model.TeamTemplate;
import org.c2w.infra.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * The {@link TeamTemplate}s of one kind (heroes or titans) of one workspace,
 * from {@code heroTemplates.json} / {@code titanTemplates.json} directly in
 * the workspace folder - see {@link TeamTemplateFiles} for the file format
 * and validation. Templates are workspace-wide (shared by all guilds) and
 * are loaded (F1-F5) and saved (Shift+F1-F5) from any team row, see {@code
 * org.c2w.gui.guild.TeamEditorPanel}.
 *
 * <p>Only titans ship default templates (one per element, classpath {@code
 * /data/titanTemplates.json}); they are copied into the workspace once, when
 * the workspace file does not exist yet - afterwards the file belongs to the
 * user and is never compared with the defaults again. Use {@link
 * #forHeroes}/{@link #forTitans}.
 */
public class TeamTemplateRepository {

    /** File name of the hero templates' workspace file. */
    public static final String HERO_FILE_NAME = "heroTemplates.json";

    /** File name of the titan templates' workspace file. */
    public static final String TITAN_FILE_NAME = "titanTemplates.json";

    static final String HERO_MEMBERS_FIELD = "heroIds";
    static final String TITAN_MEMBERS_FIELD = "titanIds";

    private static final String TITAN_DEFAULTS_PATH = "/data/" + TITAN_FILE_NAME;

    private final Path templateFile;
    private final String membersField;
    private final SortedMap<Integer, TeamTemplate> templates;

    /** True while the file still holds content that was ignored on load - see {@link #save}. */
    private boolean fileHasInvalidContent;

    private TeamTemplateRepository(Path templateFile, String membersField) {
        this.templateFile = templateFile;
        this.membersField = membersField;
        TeamTemplateFiles.LoadResult loaded = TeamTemplateFiles.loadWorkspace(templateFile, membersField);
        this.templates = loaded.templates();
        this.fileHasInvalidContent = loaded.hadProblems();
    }

    /** The hero templates of {@code workspaceDir} - there are no shipped defaults for heroes. */
    public static TeamTemplateRepository forHeroes(Path workspaceDir) {
        return new TeamTemplateRepository(requireDir(workspaceDir).resolve(HERO_FILE_NAME), HERO_MEMBERS_FIELD);
    }

    /**
     * The titan templates of {@code workspaceDir} - the workspace file is
     * first created from the shipped defaults if it does not exist yet.
     */
    public static TeamTemplateRepository forTitans(Path workspaceDir) {
        Path file = requireDir(workspaceDir).resolve(TITAN_FILE_NAME);
        TeamTemplateFiles.createFromDefaultsIfMissing(file, TeamTemplateRepository.class, TITAN_DEFAULTS_PATH);
        return new TeamTemplateRepository(file, TITAN_MEMBERS_FIELD);
    }

    /** The template in {@code slot} ({@value TeamTemplate#MIN_SLOT}..{@value TeamTemplate#MAX_SLOT}), empty if that slot is free. */
    public synchronized Optional<TeamTemplate> template(int slot) {
        return Optional.ofNullable(templates.get(slot));
    }

    /** All templates, ordered by slot. */
    public synchronized List<TeamTemplate> templates() {
        return List.copyOf(templates.values());
    }

    /**
     * Stores {@code memberIds} as the template in {@code slot}, replacing
     * whatever was there, and writes the file right away (atomically, see
     * {@link TeamTemplateFiles#writeAtomically}). The in-memory state is only
     * changed if writing succeeded. If the file had content that was ignored
     * on load, it is first backed up (see {@link TeamTemplateFiles#backupInvalid}).
     *
     * @throws IllegalArgumentException if slot/ids are not a valid {@link TeamTemplate}
     * @throws IOException              if the file could not be written
     */
    public synchronized void save(int slot, List<String> memberIds) throws IOException {
        TeamTemplate template = new TeamTemplate(slot, memberIds);
        SortedMap<Integer, TeamTemplate> updated = new TreeMap<>(templates);
        updated.put(slot, template);
        if (fileHasInvalidContent && !TeamTemplateFiles.backupInvalid(templateFile)) {
            throw new IOException("could not back up " + templateFile + " before overwriting it");
        }
        TeamTemplateFiles.writeAtomically(TeamTemplateFiles.toJson(updated.values(), membersField), templateFile);
        templates.clear();
        templates.putAll(updated);
        fileHasInvalidContent = false;
        Logger.log(templateFile.getFileName() + ": saved template " + slot + " " + template.memberIds());
    }

    /** The workspace file these templates are read from and saved to. */
    public Path templateFile() {
        return templateFile;
    }

    private static Path requireDir(Path workspaceDir) {
        if (workspaceDir == null) {
            throw new IllegalArgumentException("workspaceDir must not be null");
        }
        return workspaceDir;
    }
}
