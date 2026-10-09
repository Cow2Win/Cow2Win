package org.c2w.gui.journal;

import org.c2w.data.journal.LogDirection;
import org.c2w.data.journal.parse.BattleLogTestFiles;
import org.c2w.gui.journal.ImportWizardModel.Block;
import org.c2w.gui.journal.ImportWizardModel.Step;
import org.c2w.service.journal.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The assistant's state logic with real plans from the import service and the sample logs. */
class ImportWizardModelTest extends JournalGuiTestSupport {

    @Test
    @DisplayName("Without questions: only overview and summary")
    void onlyOverviewAndSummary() throws Exception {
        importWithDefaults(prepare("de", SEP_24, LogDirection.ATTACK));

        ImportWizardModel model = new ImportWizardModel(prepare("de", SEP_24, LogDirection.ATTACK), context.guild());

        assertEquals(List.of(Step.OVERVIEW, Step.SUMMARY), model.steps());
        assertEquals(0, model.summary().battlesNew());
        assertEquals(1, model.summary().battlesUnchanged());
        assertFalse(model.summary().guildChanges());
    }

    @Test
    @DisplayName("Steps exactly for the questions of the plan, in order; navigation")
    void stepsAndNavigation() throws Exception {
        setSampleMembers();
        ImportWizardModel model = new ImportWizardModel(
                prepare("de", SEP_24, LogDirection.ATTACK, LogDirection.DEFENSE), context.guild());

        assertEquals(List.of(Step.OVERVIEW, Step.GUILD, Step.SEASON, Step.PLAYERS, Step.SUMMARY), model.steps());
        assertEquals(Step.OVERVIEW, model.currentStep());
        assertFalse(model.canGoBack());
        assertFalse(model.canImport(), "only on the last step");

        model.next();
        model.next();
        model.next();
        assertEquals(Step.PLAYERS, model.currentStep());
        model.back();
        assertEquals(Step.SEASON, model.currentStep());
        model.next();
        model.next();
        assertEquals(Step.SUMMARY, model.currentStep());
        assertTrue(model.isLastStep());
        assertFalse(model.canGoNext());
        assertTrue(model.canImport());
        assertEquals(4, model.currentIndex());
    }

    @Test
    @DisplayName("Unknown names add the names step")
    void namesStep() throws Exception {
        importWithDefaults(prepare("de", SEP_24, LogDirection.ATTACK));
        Path original = BattleLogTestFiles.file("de", "28-09-2026", LogDirection.ATTACK);
        Path dir = Files.createDirectories(workspace.resolve("import"));
        Path file = dir.resolve(original.getFileName().toString());
        Files.writeString(file, Files.readString(original, StandardCharsets.UTF_8).replace("Lara Croft |", "Neuheld |"),
                StandardCharsets.UTF_8);

        ImportWizardModel model = new ImportWizardModel(service().prepare(List.of(file)), context.guild());

        assertEquals(List.of(Step.OVERVIEW, Step.NAMES, Step.SUMMARY), model.steps());
        UnknownNameQuestion q = model.plan().unknownNames().get(0);
        assertNull(model.nameAnswer(q.id()), "default: leave unknown");
        model.setNameAnswer(q.id(), "lara");
        assertEquals("lara", model.toAnswers().names().get(q.id()));
        assertEquals(1, model.summary().nameMappings());
        model.setNameAnswer(q.id(), null);
        assertEquals(0, model.toAnswers().names().size());
    }

    @Test
    @DisplayName("Default answers: link = yes, season = suggestion, player = first name suggestion or OPEN")
    void defaults() throws Exception {
        setSampleMembers();
        ImportPlan plan = prepare("de", SEP_24, LogDirection.ATTACK, LogDirection.DEFENSE);
        ImportWizardModel model = new ImportWizardModel(plan, context.guild());

        assertTrue(model.linkGuild());
        SeasonQuestion season = plan.seasonQuestions().get(0);
        assertEquals(Optional.of(season.suggestedStart()), model.seasonStart(season.id()));
        assertEquals(Optional.of(LocalDate.of(2026, 12, 17)), model.seasonEnd(season.id()));
        assertEquals(PlayerAnswer.assign("Ordensriter"), model.playerAnswer(question(plan, "Ordensritter").id()));
        assertEquals(PlayerAnswer.of(PlayerAnswer.Kind.OPEN), model.playerAnswer(question(plan, "Faern").id()));

        ImportAnswers answers = model.toAnswers();
        assertEquals(Boolean.TRUE, answers.linkGuild());
        assertEquals(Optional.of(season.suggestedStart()), answers.seasons().get(season.id()));
        assertEquals(PlayerAnswer.assign("Ordensriter"), answers.players().get(question(plan, "Ordensritter").id()));
    }

    @Test
    @DisplayName("Answers become ImportAnswers (changed season start, no season, RENAME, CREATE) and are accepted")
    void answersToImportAnswers() throws Exception {
        setSampleMembers();
        ImportPlan plan = prepare("de", SEP_24, LogDirection.ATTACK, LogDirection.DEFENSE);
        ImportWizardModel model = new ImportWizardModel(plan, context.guild());
        SeasonQuestion season = plan.seasonQuestions().get(0);

        model.setSeasonStart(season.id(), LocalDate.of(2026, 9, 21));
        assertEquals(Optional.of(LocalDate.of(2026, 9, 21)), model.toAnswers().seasons().get(season.id()));
        model.setNoSeason(season.id());
        assertEquals(Optional.empty(), model.toAnswers().seasons().get(season.id()));
        model.setPlayerAnswer(question(plan, "Faern").id(), PlayerAnswer.of(PlayerAnswer.Kind.CREATE));
        model.setPlayerAnswer(question(plan, "Xavior").id(), PlayerAnswer.rename("Ordensriter"));
        model.setPlayerAnswer(question(plan, "Ordensritter").id(), PlayerAnswer.of(PlayerAnswer.Kind.FORMER));

        ImportAnswers answers = model.toAnswers();
        assertEquals(PlayerAnswer.of(PlayerAnswer.Kind.CREATE), answers.players().get(question(plan, "Faern").id()));
        assertEquals(PlayerAnswer.rename("Ordensriter"), answers.players().get(question(plan, "Xavior").id()));

        ImportResult result = service().execute(plan, answers);
        assertTrue(result.isSuccess(), result.errors().toString());
        assertEquals(List.of("Faern"), result.createdMembers());
        assertEquals("Xavior", result.renamedMembers().get(0).newName());
        assertNull(result.battles().get(0).seasonId(), "no season");
    }

    @Test
    @DisplayName("Member choosers: suggestions first, then everybody else by name")
    void memberChoices() throws Exception {
        setSampleMembers();
        ImportPlan plan = prepare("de", SEP_24, LogDirection.DEFENSE);
        ImportWizardModel model = new ImportWizardModel(plan, context.guild());

        List<ImportWizardModel.MemberChoice> choices = model.assignChoices(question(plan, "Ordensritter"));

        assertEquals("Ordensriter", choices.get(0).memberId());
        assertEquals(3, choices.size());
        assertEquals(3, model.renameChoices(question(plan, "Faern")).size());
    }

    @Test
    @DisplayName("Plan errors: only the overview, no import")
    void planErrors() throws Exception {
        setMembers(List.of());
        context.setGuild(context.guild().withGameGuildId(1L));

        ImportWizardModel model = new ImportWizardModel(prepare("de", SEP_24, LogDirection.ATTACK), context.guild());

        assertEquals(List.of(Step.OVERVIEW), model.steps());
        assertFalse(model.canGoNext());
        assertFalse(model.canImport());
        assertEquals(Set.of(Block.PLAN_NOT_IMPORTABLE), model.blockingProblems());
    }

    @Test
    @DisplayName("Guild link declined: no import")
    void guildDeclined() throws Exception {
        ImportWizardModel model = new ImportWizardModel(prepare("de", SEP_24, LogDirection.ATTACK), context.guild());

        model.setLinkGuild(false);
        model.next();

        assertEquals(Step.GUILD, model.currentStep());
        assertEquals(Set.of(Block.GUILD_NOT_LINKED), model.blockingProblems(Step.GUILD));
        assertFalse(model.canGoNext());
        assertFalse(model.toAnswers().linkGuild());
        model.setLinkGuild(true);
        assertTrue(model.canGoNext());
    }

    @Test
    @DisplayName("Member limit exceeded by CREATE: next blocked on the players step")
    void memberLimit() throws Exception {
        setMembers(dummyMembers(29));
        ImportPlan plan = prepare("de", SEP_24, LogDirection.DEFENSE);
        ImportWizardModel model = new ImportWizardModel(plan, context.guild());
        while (model.currentStep() != Step.PLAYERS) {
            model.next();
        }

        model.setPlayerAnswer(question(plan, "Faern").id(), PlayerAnswer.of(PlayerAnswer.Kind.CREATE));
        assertEquals(30, model.memberCountAfterImport());
        assertTrue(model.canGoNext());
        model.setPlayerAnswer(question(plan, "Vivien").id(), PlayerAnswer.of(PlayerAnswer.Kind.CREATE));

        assertTrue(model.isMemberLimitExceeded());
        assertFalse(model.canGoNext());
        assertTrue(model.blockingProblems().contains(Block.MEMBER_LIMIT));
    }

    @Test
    @DisplayName("The same member renamed twice: both questions are marked, next blocked")
    void duplicateRename() throws Exception {
        setSampleMembers();
        ImportPlan plan = prepare("de", SEP_24, LogDirection.DEFENSE);
        ImportWizardModel model = new ImportWizardModel(plan, context.guild());
        String faern = question(plan, "Faern").id();
        String vivien = question(plan, "Vivien").id();

        model.setPlayerAnswer(faern, PlayerAnswer.rename("Ordensriter"));
        model.setPlayerAnswer(vivien, PlayerAnswer.rename("Ordensriter"));

        assertEquals(Set.of(faern, vivien), model.duplicateRenameQuestionIds());
        assertTrue(model.blockingProblems(Step.PLAYERS).contains(Block.DUPLICATE_RENAME));
    }

    @Test
    @DisplayName("Summary counts and reports the guild change")
    void summary() throws Exception {
        setSampleMembers();
        ImportPlan plan = prepare("de", SEP_24, LogDirection.ATTACK, LogDirection.DEFENSE);
        ImportWizardModel model = new ImportWizardModel(plan, context.guild());
        model.setPlayerAnswer(question(plan, "Faern").id(), PlayerAnswer.of(PlayerAnswer.Kind.CREATE));
        model.setPlayerAnswer(question(plan, "Xavior").id(), PlayerAnswer.rename("Ordensriter"));

        ImportWizardModel.Summary s = model.summary();

        assertEquals(1, s.battlesNew());
        assertEquals(0, s.battlesUpdated());
        assertEquals(1, s.seasonsNew());
        assertTrue(s.linkGuild());
        assertEquals(1, s.membersNew());
        assertEquals(1, s.membersRenamed());
        assertEquals(plan.autoAssignments().size() + 3, s.assignments(), "auto + Ordensritter + Faern + Xavior");
        assertTrue(s.guildChanges());
        assertEquals(4, model.memberCountAfterImport(), "3 members + Faern");
    }

    @Test
    @DisplayName("Second direction later: the battle counts as updated, no guild change without link")
    void summaryUpdatedBattle() throws Exception {
        importWithDefaults(prepare("de", SEP_24, LogDirection.ATTACK));

        ImportWizardModel model = new ImportWizardModel(prepare("fr", SEP_24, LogDirection.DEFENSE), context.guild());

        assertEquals(1, model.summary().battlesUpdated());
        assertFalse(model.summary().linkGuild());
        assertEquals(List.of(LogDirection.DEFENSE), ImportWizardModel.directions(model.plan().battles().get(0)));
    }

    @Test
    @DisplayName("'Create all without suggestion': only questions without suggestion, then 'reset all'")
    void createAllWithoutSuggestion() throws Exception {
        setSampleMembers();
        ImportPlan plan = prepare("de", SEP_24, LogDirection.DEFENSE);
        ImportWizardModel model = new ImportWizardModel(plan, context.guild());
        PlayerQuestion ordensritter = question(plan, "Ordensritter");
        PlayerQuestion faern = question(plan, "Faern");
        assertFalse(ImportWizardModel.hasNoSuggestion(ordensritter));
        assertTrue(ImportWizardModel.hasNoSuggestion(faern));
        long withoutSuggestion = plan.playerQuestions().stream().filter(ImportWizardModel::hasNoSuggestion).count();
        model.setPlayerAnswer(faern.id(), PlayerAnswer.of(PlayerAnswer.Kind.FORMER));

        ImportWizardModel.CreateAllResult result = model.createAllWithoutSuggestion();

        assertEquals(withoutSuggestion, result.created());
        assertEquals(0, result.skippedByLimit());
        assertEquals(PlayerAnswer.Kind.CREATE, model.playerAnswer(faern.id()).kind(), "overrides other answers");
        assertEquals(PlayerAnswer.assign("Ordensriter"), model.playerAnswer(ordensritter.id()), "suggestion untouched");
        assertEquals(3 + withoutSuggestion, model.memberCountAfterImport());
        assertEquals(result, model.createAllWithoutSuggestion(), "idempotent");

        model.resetPlayerAnswers();
        assertEquals(PlayerAnswer.of(PlayerAnswer.Kind.OPEN), model.playerAnswer(faern.id()));
        assertEquals(PlayerAnswer.assign("Ordensriter"), model.playerAnswer(ordensritter.id()));
        assertEquals(3, model.memberCountAfterImport());
    }

    @Test
    @DisplayName("'Create all' respects the limit of 30 members, in question order")
    void createAllRespectsLimit() throws Exception {
        setMembers(dummyMembers(27));
        ImportPlan plan = prepare("de", SEP_24, LogDirection.DEFENSE);
        ImportWizardModel model = new ImportWizardModel(plan, context.guild());
        List<PlayerQuestion> candidates = plan.playerQuestions().stream().filter(ImportWizardModel::hasNoSuggestion)
                .filter(q -> q.allowedAnswers().contains(PlayerAnswer.Kind.CREATE)).toList();
        assertTrue(candidates.size() > 3);

        ImportWizardModel.CreateAllResult result = model.createAllWithoutSuggestion();

        assertEquals(3, result.created());
        assertEquals(candidates.size() - 3, result.skippedByLimit());
        assertEquals(30, model.memberCountAfterImport());
        assertFalse(model.isMemberLimitExceeded());
        for (int i = 0; i < candidates.size(); i++) {
            assertEquals(i < 3 ? PlayerAnswer.Kind.CREATE : PlayerAnswer.Kind.OPEN,
                    model.playerAnswer(candidates.get(i).id()).kind(), candidates.get(i).rawName());
        }
    }

    @Test
    @DisplayName("Empty guild, all 6 battles: every defender becomes a new member (27 names, limit kept)")
    void createAllForEmptyGuild() throws Exception {
        ImportPlan plan = service().prepare(BattleLogTestFiles.files("de"));
        ImportWizardModel model = new ImportWizardModel(plan, context.guild());

        ImportWizardModel.CreateAllResult result = model.createAllWithoutSuggestion();

        assertEquals(27, plan.playerQuestions().size());
        assertEquals(27, result.created());
        assertEquals(0, result.skippedByLimit());
        assertEquals(27, model.summary().membersNew());
        assertTrue(model.blockingProblems().isEmpty());
    }

    private static PlayerQuestion question(ImportPlan plan, String rawName) {
        return plan.playerQuestions().stream().filter(q -> q.rawName().equals(rawName)).findFirst()
                .orElseThrow(() -> new AssertionError("no question for " + rawName));
    }
}
