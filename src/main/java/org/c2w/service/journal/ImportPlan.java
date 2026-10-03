package org.c2w.service.journal;

import java.nio.file.Path;
import java.util.List;

/**
 * What an import would do and what it needs to know - the result of
 * {@code JournalImportService#prepare}, which writes nothing. The GUI shows it,
 * collects {@link ImportAnswers} and hands both to {@code execute}.
 *
 * @param files             every selected file with its status
 * @param battles           the battles of the importable files
 * @param errors            reasons why nothing can be imported (empty if importable)
 * @param ownGameGuildId    game guild id of the exporting guild, {@code null} if unknown
 * @param guildLink         asked if the open guild has no game guild id yet, else {@code null}
 * @param seasonQuestions   seasons to decide
 * @param autoAssignments   defenders assigned without asking (information)
 * @param playerQuestions   defenders to decide (only from defense logs)
 * @param unknownNames      names without catalog id (both logs)
 * @param guildMemberCount  members of the open guild when the plan was made
 * @param guildFile         guild file of the open guild when the plan was made ({@code execute} refuses another)
 */
public record ImportPlan(
        List<PlannedFile> files,
        List<PlannedBattle> battles,
        List<PlanError> errors,
        Long ownGameGuildId,
        GuildLinkQuestion guildLink,
        List<SeasonQuestion> seasonQuestions,
        List<PlayerAutoAssignment> autoAssignments,
        List<PlayerQuestion> playerQuestions,
        List<UnknownNameQuestion> unknownNames,
        int guildMemberCount,
        Path guildFile
) {
    public ImportPlan {
        files = files == null ? List.of() : List.copyOf(files);
        battles = battles == null ? List.of() : List.copyOf(battles);
        errors = errors == null ? List.of() : List.copyOf(errors);
        seasonQuestions = seasonQuestions == null ? List.of() : List.copyOf(seasonQuestions);
        autoAssignments = autoAssignments == null ? List.of() : List.copyOf(autoAssignments);
        playerQuestions = playerQuestions == null ? List.of() : List.copyOf(playerQuestions);
        unknownNames = unknownNames == null ? List.of() : List.copyOf(unknownNames);
    }

    /** True if something can be imported. */
    public boolean isImportable() {
        return errors.isEmpty() && files.stream().anyMatch(f -> f.status() == PlannedFile.Status.READY);
    }

    /** The files that will be imported. */
    public List<PlannedFile> readyFiles() {
        return files.stream().filter(f -> f.status() == PlannedFile.Status.READY).toList();
    }
}
