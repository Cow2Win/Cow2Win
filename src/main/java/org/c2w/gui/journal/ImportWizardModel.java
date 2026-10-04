package org.c2w.gui.journal;

import org.c2w.data.journal.LogDirection;
import org.c2w.data.model.Guild;
import org.c2w.service.journal.*;

import java.time.LocalDate;
import java.util.*;

/**
 * State of the journal import assistant, without any Swing: which steps exist,
 * where the user is, the current answer to every question (starting with the
 * defaults), what blocks going on, and what the import will do. The dialog
 * ({@link JournalImportDialog}) only shows this model and forwards input to it;
 * all checks and suggestions come from the {@link ImportPlan} of the service.
 *
 * <p>Defaults: link the guild = yes; season = the suggested start; player = the
 * first name suggestion if there is one, else {@code OPEN}; unknown name = leave
 * unknown.
 */
public final class ImportWizardModel {

    /** The steps of the assistant, in this order; only those with content are shown. */
    public enum Step {
        OVERVIEW,
        GUILD,
        SEASON,
        PLAYERS,
        NAMES,
        SUMMARY
    }

    /** A guild member as offered in the member choosers. */
    public record MemberChoice(String memberId, String memberName) {
    }

    /**
     * What the import will do, for the summary step.
     *
     * @param battlesNew       battles not yet in the journal
     * @param battlesUpdated   battles in the journal that get a new or replaced log
     * @param battlesUnchanged battles whose imported logs are identical to the stored ones
     * @param seasonsNew       seasons that will be created
     * @param linkGuild        the guild gets linked to the game guild
     * @param membersNew       members that will be created (without teams)
     * @param membersRenamed   members that will be renamed
     * @param assignments      players that will be linked to a member (automatic + answered)
     * @param nameMappings     name mappings that will be stored
     */
    public record Summary(int battlesNew, int battlesUpdated, int battlesUnchanged, int seasonsNew, boolean linkGuild,
                          int membersNew, int membersRenamed, int assignments, int nameMappings) {

        /** True if the Cow2Win guild changes (and is unsaved afterwards). */
        public boolean guildChanges() {
            return linkGuild || membersNew > 0 || membersRenamed > 0;
        }
    }

    private final ImportPlan plan;
    private final List<MemberChoice> members;
    private final List<Step> steps;
    private int index;

    private boolean linkGuild = true;
    private final Map<String, Optional<LocalDate>> seasonStarts = new HashMap<>();
    private final Map<String, PlayerAnswer> playerAnswers = new HashMap<>();
    private final Map<String, String> nameAnswers = new HashMap<>();

    /**
     * @param plan  the plan from {@code JournalImportService#prepare}
     * @param guild the open guild (its members are offered for ASSIGN/RENAME)
     */
    public ImportWizardModel(ImportPlan plan, Guild guild) {
        this.plan = Objects.requireNonNull(plan);
        this.members = guild == null ? List.of() : guild.members().stream()
                .map(m -> new MemberChoice(m.id(), m.name()))
                .sorted(Comparator.comparing(MemberChoice::memberName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        this.steps = computeSteps(plan);
        for (PlayerQuestion q : plan.playerQuestions()) {
            playerAnswers.put(q.id(), defaultAnswer(q));
        }
        for (SeasonQuestion q : plan.seasonQuestions()) {
            seasonStarts.put(q.id(), Optional.of(q.suggestedStart()));
        }
    }

    private static List<Step> computeSteps(ImportPlan plan) {
        if (!plan.isImportable()) {
            return List.of(Step.OVERVIEW);
        }
        List<Step> result = new ArrayList<>();
        result.add(Step.OVERVIEW);
        if (plan.guildLink() != null) {
            result.add(Step.GUILD);
        }
        if (!plan.seasonQuestions().isEmpty()) {
            result.add(Step.SEASON);
        }
        if (!plan.playerQuestions().isEmpty() || !plan.autoAssignments().isEmpty()) {
            result.add(Step.PLAYERS);
        }
        if (!plan.unknownNames().isEmpty()) {
            result.add(Step.NAMES);
        }
        result.add(Step.SUMMARY);
        return List.copyOf(result);
    }

    // --- plan and steps ---

    public ImportPlan plan() {
        return plan;
    }

    /** The guild members, by name. */
    public List<MemberChoice> members() {
        return members;
    }

    public List<Step> steps() {
        return steps;
    }

    public Step currentStep() {
        return steps.get(index);
    }

    /** 0-based index of the current step. */
    public int currentIndex() {
        return index;
    }

    public boolean isLastStep() {
        return index == steps.size() - 1;
    }

    public boolean canGoBack() {
        return index > 0;
    }

    /** True if there is a next step and the current one does not block. */
    public boolean canGoNext() {
        return !isLastStep() && blockingProblems(currentStep()).isEmpty();
    }

    public void next() {
        if (canGoNext()) {
            index++;
        }
    }

    public void back() {
        if (canGoBack()) {
            index--;
        }
    }

    /** True if the import may be started: on the last step, plan importable, nothing blocking. */
    public boolean canImport() {
        return isLastStep() && plan.isImportable() && blockingProblems().isEmpty();
    }

    /** What blocks the import at all (all steps). */
    public Set<Block> blockingProblems() {
        Set<Block> result = EnumSet.noneOf(Block.class);
        for (Step step : steps) {
            result.addAll(blockingProblems(step));
        }
        return result;
    }

    /** What blocks going on from {@code step}. */
    public Set<Block> blockingProblems(Step step) {
        Set<Block> result = EnumSet.noneOf(Block.class);
        switch (step) {
            case OVERVIEW -> {
                if (!plan.isImportable()) {
                    result.add(Block.PLAN_NOT_IMPORTABLE);
                }
            }
            case GUILD -> {
                if (plan.guildLink() != null && !linkGuild) {
                    result.add(Block.GUILD_NOT_LINKED);
                }
            }
            case PLAYERS -> {
                if (isMemberLimitExceeded()) {
                    result.add(Block.MEMBER_LIMIT);
                }
                if (!duplicateRenameQuestionIds().isEmpty()) {
                    result.add(Block.DUPLICATE_RENAME);
                }
            }
            default -> {
            }
        }
        return result;
    }

    /** Reasons why the assistant cannot go on / import. */
    public enum Block {
        /** The plan has errors or nothing importable. */
        PLAN_NOT_IMPORTABLE,
        /** The guild link was declined - no import without it. */
        GUILD_NOT_LINKED,
        /** More new members than the guild has room for. */
        MEMBER_LIMIT,
        /** Two players are to be renamed onto the same member. */
        DUPLICATE_RENAME
    }

    // --- guild ---

    public boolean linkGuild() {
        return linkGuild;
    }

    public void setLinkGuild(boolean link) {
        this.linkGuild = link;
    }

    // --- seasons ---

    /** The chosen start of a season question, empty = no season. */
    public Optional<LocalDate> seasonStart(String questionId) {
        return seasonStarts.getOrDefault(questionId, Optional.empty());
    }

    /** The end (exclusive first day after the season) that follows from the chosen start. */
    public Optional<LocalDate> seasonEnd(String questionId) {
        return seasonStart(questionId).map(start -> start.plus(org.c2w.data.journal.db.Season.DEFAULT_LENGTH));
    }

    public void setSeasonStart(String questionId, LocalDate start) {
        seasonStarts.put(questionId, Optional.of(Objects.requireNonNull(start)));
    }

    public void setNoSeason(String questionId) {
        seasonStarts.put(questionId, Optional.empty());
    }

    // --- players ---

    public PlayerAnswer playerAnswer(String questionId) {
        return playerAnswers.getOrDefault(questionId, PlayerAnswer.of(PlayerAnswer.Kind.OPEN));
    }

    public void setPlayerAnswer(String questionId, PlayerAnswer answer) {
        playerAnswers.put(questionId, Objects.requireNonNull(answer));
    }

    /**
     * Result of {@link #createAllWithoutSuggestion()}.
     *
     * @param created        questions now answered with CREATE (including those that already were)
     * @param skippedByLimit questions left as they were because the guild has no more room
     */
    public record CreateAllResult(int created, int skippedByLimit) {
    }

    /** True if the service had neither a name nor a rename suggestion for the question. */
    public static boolean hasNoSuggestion(PlayerQuestion question) {
        return question.nameSuggestions().isEmpty() && question.renameSuggestions().isEmpty();
    }

    /**
     * "Create all without suggestion as new members": answers every player question
     * without name or rename suggestion that allows {@code CREATE} with {@code CREATE},
     * in question order, as long as the guild has room ({@link Guild#MAX_MEMBERS});
     * the rest stay as they are. For starting with an empty guild.
     */
    public CreateAllResult createAllWithoutSuggestion() {
        int room = Guild.MAX_MEMBERS - memberCountAfterImport();
        int created = 0;
        int skipped = 0;
        for (PlayerQuestion q : plan.playerQuestions()) {
            if (!hasNoSuggestion(q) || !q.allowedAnswers().contains(PlayerAnswer.Kind.CREATE)) {
                continue;
            }
            if (playerAnswer(q.id()).kind() == PlayerAnswer.Kind.CREATE) {
                created++;
            } else if (room > 0) {
                playerAnswers.put(q.id(), PlayerAnswer.of(PlayerAnswer.Kind.CREATE));
                room--;
                created++;
            } else {
                skipped++;
            }
        }
        return new CreateAllResult(created, skipped);
    }

    /** "Reset all": every player question back to its default (first name suggestion, else OPEN). */
    public void resetPlayerAnswers() {
        for (PlayerQuestion q : plan.playerQuestions()) {
            playerAnswers.put(q.id(), defaultAnswer(q));
        }
    }

    private static PlayerAnswer defaultAnswer(PlayerQuestion q) {
        return q.nameSuggestions().isEmpty()
                ? PlayerAnswer.of(PlayerAnswer.Kind.OPEN)
                : PlayerAnswer.assign(q.nameSuggestions().get(0).memberId());
    }

    /** Members for "assign to ...": the name suggestions first (closest first), then all others by name. */
    public List<MemberChoice> assignChoices(PlayerQuestion question) {
        List<MemberChoice> result = new ArrayList<>();
        question.nameSuggestions().forEach(s -> result.add(new MemberChoice(s.memberId(), s.memberName())));
        addRemaining(result);
        return result;
    }

    /** Members for "renamed - was ...": the rename suggestions first (best first), then all others by name. */
    public List<MemberChoice> renameChoices(PlayerQuestion question) {
        List<MemberChoice> result = new ArrayList<>();
        question.renameSuggestions().forEach(s -> result.add(new MemberChoice(s.memberId(), s.memberName())));
        addRemaining(result);
        return result;
    }

    private void addRemaining(List<MemberChoice> result) {
        Set<String> present = new HashSet<>(result.stream().map(MemberChoice::memberId).toList());
        members.stream().filter(m -> !present.contains(m.memberId())).forEach(result::add);
    }

    /** Members after the import: the current ones plus every CREATE answer. */
    public int memberCountAfterImport() {
        return plan.guildMemberCount() + (int) plan.playerQuestions().stream()
                .filter(q -> playerAnswer(q.id()).kind() == PlayerAnswer.Kind.CREATE).count();
    }

    public boolean isMemberLimitExceeded() {
        return memberCountAfterImport() > Guild.MAX_MEMBERS;
    }

    /** Ids of the player questions whose RENAME points to a member another question also renames. */
    public Set<String> duplicateRenameQuestionIds() {
        Map<String, List<String>> byMember = new HashMap<>();
        for (PlayerQuestion q : plan.playerQuestions()) {
            PlayerAnswer a = playerAnswer(q.id());
            if (a.kind() == PlayerAnswer.Kind.RENAME) {
                byMember.computeIfAbsent(a.memberId(), k -> new ArrayList<>()).add(q.id());
            }
        }
        Set<String> result = new HashSet<>();
        byMember.values().stream().filter(ids -> ids.size() > 1).forEach(result::addAll);
        return result;
    }

    // --- unknown names ---

    /** The chosen catalog id (element name for a totem), {@code null} = leave unknown. */
    public String nameAnswer(String questionId) {
        return nameAnswers.get(questionId);
    }

    public void setNameAnswer(String questionId, String catalogIdOrNull) {
        if (catalogIdOrNull == null) {
            nameAnswers.remove(questionId);
        } else {
            nameAnswers.put(questionId, catalogIdOrNull);
        }
    }

    // --- result ---

    /** The answers for {@code JournalImportService#execute}. */
    public ImportAnswers toAnswers() {
        ImportAnswers answers = ImportAnswers.defaults();
        if (plan.guildLink() != null) {
            answers = answers.withGuildLink(linkGuild);
        }
        for (SeasonQuestion q : plan.seasonQuestions()) {
            answers = answers.withSeason(q.id(), seasonStart(q.id()).orElse(null));
        }
        for (PlayerQuestion q : plan.playerQuestions()) {
            answers = answers.withPlayer(q.id(), playerAnswer(q.id()));
        }
        for (UnknownNameQuestion q : plan.unknownNames()) {
            String id = nameAnswer(q.id());
            if (id != null) {
                answers = answers.withName(q.id(), id);
            }
        }
        return answers;
    }

    /** What the import will do with the current answers. */
    public Summary summary() {
        int battlesNew = 0;
        int battlesUpdated = 0;
        int battlesUnchanged = 0;
        for (PlannedBattle b : plan.battles()) {
            if (b.existingBattleId() == null) {
                battlesNew++;
            } else if (b.actions().values().stream().allMatch(a -> a == PlannedBattle.LogAction.UNCHANGED)) {
                battlesUnchanged++;
            } else {
                battlesUpdated++;
            }
        }
        int seasonsNew = (int) plan.seasonQuestions().stream().filter(q -> seasonStart(q.id()).isPresent()).count();
        int membersNew = 0;
        int membersRenamed = 0;
        int assignments = plan.autoAssignments().size();
        for (PlayerQuestion q : plan.playerQuestions()) {
            switch (playerAnswer(q.id()).kind()) {
                case CREATE -> {
                    membersNew++;
                    assignments++;
                }
                case RENAME -> {
                    membersRenamed++;
                    assignments++;
                }
                case ASSIGN -> assignments++;
                default -> {
                }
            }
        }
        return new Summary(battlesNew, battlesUpdated, battlesUnchanged, seasonsNew,
                plan.guildLink() != null && linkGuild, membersNew, membersRenamed, assignments, nameAnswers.size());
    }

    /** The imported log directions of a battle, in order (attack first). */
    public static List<LogDirection> directions(PlannedBattle battle) {
        return Arrays.stream(LogDirection.values()).filter(battle.actions()::containsKey).toList();
    }
}
