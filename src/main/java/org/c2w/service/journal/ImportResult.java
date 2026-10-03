package org.c2w.service.journal;

import org.c2w.data.journal.GuildRef;
import org.c2w.data.journal.db.BattleStatus;
import org.c2w.data.journal.db.SaveResult;
import org.c2w.data.journal.parse.BattleLogCheck;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * What {@code JournalImportService#execute} did - only data (enums, ids,
 * numbers); the GUI turns it into texts.
 *
 * @param errors             why nothing was imported (empty on success); nothing was changed then
 * @param files              per file what happened
 * @param battles            per imported battle the stored state
 * @param fights             number of single fights written
 * @param createdMembers     ids of the members added to the guild
 * @param renamedMembers     members whose name changed
 * @param assignments        number of player assignments set in the journal
 * @param nameMappings       number of name mappings added
 * @param guildLinked        true if the guild's game guild id was set
 * @param warnings           warnings of the journal (in English, for the log)
 */
public record ImportResult(
        List<ImportError> errors,
        List<FileResult> files,
        List<BattleOutcome> battles,
        int fights,
        List<String> createdMembers,
        List<RenamedMember> renamedMembers,
        int assignments,
        int nameMappings,
        boolean guildLinked,
        List<String> warnings
) {
    public ImportResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
        files = files == null ? List.of() : List.copyOf(files);
        battles = battles == null ? List.of() : List.copyOf(battles);
        createdMembers = createdMembers == null ? List.of() : List.copyOf(createdMembers);
        renamedMembers = renamedMembers == null ? List.of() : List.copyOf(renamedMembers);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /** A result that only reports errors - nothing was changed. */
    public static ImportResult failed(List<ImportError> errors) {
        return new ImportResult(errors, List.of(), List.of(), 0, List.of(), List.of(), 0, 0, false, List.of());
    }

    public boolean isSuccess() {
        return errors.isEmpty();
    }

    /**
     * What happened to one file.
     *
     * @param path    the file
     * @param outcome stored as new / replacing / unchanged; {@code null} if skipped or failed in the plan
     * @param status  status from the plan
     */
    public record FileResult(Path path, SaveResult.Outcome outcome, PlannedFile.Status status) {
    }

    /**
     * The stored state of one battle.
     *
     * @param battleId journal id
     * @param date     battle day
     * @param opponent opponent guild
     * @param seasonId season, {@code null} if none
     * @param status   running or finished
     * @param verdict  control calculation
     */
    public record BattleOutcome(int battleId, LocalDate date, GuildRef opponent, Integer seasonId,
                                BattleStatus status, BattleLogCheck.Verdict verdict) {
    }

    /**
     * A member renamed because the player renamed themselves in the game (id unchanged).
     *
     * @param memberId member id (unchanged)
     * @param oldName  name before
     * @param newName  name after
     */
    public record RenamedMember(String memberId, String oldName, String newName) {
    }

    /**
     * Why an import was refused.
     *
     * @param kind       what is wrong
     * @param questionId the question concerned, may be {@code null}
     * @param detail     technical detail (for the log), may be {@code null}
     */
    public record ImportError(Kind kind, String questionId, String detail) {

        /** What is wrong. */
        public enum Kind {
            /** The plan has errors or nothing importable. */
            PLAN_NOT_IMPORTABLE,
            /** The guild changed since the plan was made (other guild opened). */
            GUILD_CHANGED,
            /** An answer is not one of the allowed answers of its question. */
            ANSWER_NOT_ALLOWED,
            /** ASSIGN/RENAME points to a member that does not exist. */
            UNKNOWN_MEMBER,
            /** Two players are to be renamed onto the same member. */
            DUPLICATE_RENAME,
            /** The guild would get more than {@code Guild.MAX_MEMBERS} members. */
            MEMBER_LIMIT,
            /** A season would overlap another one. */
            SEASON_OVERLAP,
            /** The catalog id of a name answer does not exist. */
            INVALID_CATALOG_ID,
            /** Writing the journal failed - everything was rolled back. */
            WRITE_FAILED
        }
    }
}
