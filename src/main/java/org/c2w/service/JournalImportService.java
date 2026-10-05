package org.c2w.service;

import org.c2w.data.journal.*;
import org.c2w.data.journal.db.*;
import org.c2w.data.journal.parse.BattleLogCheck;
import org.c2w.data.journal.parse.BattleLogFormatException;
import org.c2w.data.journal.parse.BattleLogParser;
import org.c2w.data.journal.parse.NameResolver;
import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.Hero;
import org.c2w.data.model.HeroTeam;
import org.c2w.data.model.Titan;
import org.c2w.data.model.TitanTeam;
import org.c2w.i18n.GameNameNormalizer;
import org.c2w.infra.Logger;
import org.c2w.service.journal.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;

/**
 * Imports exported Clash of Worlds battle logs into the Weltenschlacht journal
 * of the open guild - the logic behind the import dialog, without any GUI.
 * Two steps:
 * <ol>
 *   <li>{@link #prepare} reads and checks the files and collects the questions
 *       (guild link, seasons, players, unknown names) - it writes nothing;</li>
 *   <li>{@link #execute} validates the answers, changes the guild in memory
 *       (left unsaved, like any edit) and writes the journal in one transaction.</li>
 * </ol>
 *
 * <p><b>Cow2Win is about defense.</b> Everything that concerns the members of
 * the own guild - player questions, rename suggestions, new members - comes
 * from the DEFENSE log only (our players are the defenders there). The attack
 * log is stored completely (display, research, the opponent's defense teams),
 * but never changes the guild. New members are created without teams.
 */
public final class JournalImportService {

    /** A defender team power within this share of a stored team power suggests a rename. */
    public static final double RENAME_POWER_TOLERANCE = 0.03;

    /** Maximum Levenshtein distance of normalized names for a name suggestion. */
    public static final int MAX_NAME_DISTANCE = 2;

    private static final long SEASON_DAYS = ChronoUnit.DAYS.between(LocalDate.EPOCH, LocalDate.EPOCH.plus(Season.DEFAULT_LENGTH));

    private final AppContext context;
    private final GuildService guildService;
    private final JournalStore journal;
    private final Supplier<BattleLogParser> parserFactory;
    private BattleLogParser baseParser;

    /** A service for the open guild of {@code context}, using its journal and the shipped catalogs. */
    public JournalImportService(AppContext context, GuildService guildService) {
        this(context, guildService, context.journal(), BattleLogParser::createDefault);
    }

    public JournalImportService(AppContext context, GuildService guildService, JournalStore journal,
                                Supplier<BattleLogParser> parserFactory) {
        this.context = Objects.requireNonNull(context);
        this.guildService = Objects.requireNonNull(guildService);
        this.journal = Objects.requireNonNull(journal);
        this.parserFactory = Objects.requireNonNull(parserFactory);
    }

    // =====================================================================
    // prepare
    // =====================================================================

    /**
     * Reads {@code files}, checks them against the open guild and its journal and
     * collects the questions. Writes nothing (no journal file is created).
     *
     * @throws JournalException if the existing journal cannot be opened (e.g. locked)
     */
    public ImportPlan prepare(List<Path> files) throws JournalException {
        Guild guild = context.guild();
        Path guildFile = context.guildFilePath();
        if (guild == null || guildFile == null) {
            return errorPlan(List.of(), List.of(new PlanError(PlanError.Kind.NO_GUILD_OPEN, null, null)), null, 0, null);
        }
        Optional<JournalRepository> repo = journal.repository(false);
        BattleLogParser parser = parser(repo.isPresent() ? repo.get().nameMappingsByKind() : Map.of());

        // 3.1 read and group
        List<FileDraft> drafts = new ArrayList<>();
        for (Path path : files) {
            drafts.add(read(parser, path));
        }
        Map<BattleKey, Map<LogDirection, FileDraft>> byBattle = group(drafts);

        List<PlanError> errors = new ArrayList<>();
        Set<Long> ownIds = new TreeSet<>();
        GuildRef ownGuild = null;
        for (FileDraft d : drafts) {
            if (d.status == PlannedFile.Status.READY) {
                ownIds.add(d.header().ownGuild().gameGuildId());
                ownGuild = d.header().ownGuild();
            }
        }
        if (ownIds.isEmpty()) {
            errors.add(new PlanError(PlanError.Kind.NO_IMPORTABLE_FILE, null, null));
        } else if (ownIds.size() > 1) {
            errors.add(new PlanError(PlanError.Kind.MIXED_OWN_GUILDS, null, null));
        }
        Long ownId = ownIds.size() == 1 ? ownIds.iterator().next() : null;

        // 3.2 guild
        GuildLinkQuestion guildLink = null;
        if (errors.isEmpty()) {
            Optional<String> otherGuild = guildService.findOtherGuildByGameGuildId(ownId,
                    guildService.currentGuildFolderName());
            Optional<Long> journalOwn = repo.isPresent() ? repo.get().ownGameGuildId() : Optional.empty();
            boolean journalOfOther = journalOwn.isPresent() && !journalOwn.get().equals(ownId);
            if (ownId.equals(guild.gameGuildId())) {
                if (journalOfOther) {
                    errors.add(new PlanError(PlanError.Kind.JOURNAL_OF_OTHER_GUILD, null, journalOwn.get()));
                }
            } else if (otherGuild.isPresent()) {
                errors.add(new PlanError(PlanError.Kind.LOGS_OF_OTHER_GUILD, otherGuild.get(), ownId));
            } else if (guild.gameGuildId() != null) {
                errors.add(new PlanError(PlanError.Kind.NO_GUILD_WITH_THIS_ID, null, ownId));
            } else if (journalOfOther) {
                errors.add(new PlanError(PlanError.Kind.JOURNAL_OF_OTHER_GUILD, null, journalOwn.get()));
            } else {
                guildLink = new GuildLinkQuestion(GuildLinkQuestion.ID, ownGuild);
            }
        }
        if (!errors.isEmpty()) {
            return errorPlan(plannedFiles(drafts, Map.of()), errors, ownId, guild.members().size(), guildFile);
        }

        // 3.3 battles, 3.4 seasons
        List<BattleKey> keys = new ArrayList<>(byBattle.keySet());
        keys.sort(Comparator.comparing(BattleKey::date).thenComparingLong(BattleKey::opponentId));
        Map<BattleKey, Integer> battleIndex = new HashMap<>();
        Map<Integer, BattleSummary> summaries = new HashMap<>();
        List<Season> seasons = new ArrayList<>();
        if (repo.isPresent()) {
            repo.get().listBattles(null).forEach(s -> summaries.put(s.battleId(), s));
            seasons.addAll(repo.get().listSeasons());
        }
        List<BattleDraft> battleDrafts = new ArrayList<>();
        for (BattleKey key : keys) {
            battleIndex.put(key, battleDrafts.size());
            battleDrafts.add(battle(repo, key, byBattle.get(key), summaries, seasons));
        }
        List<SeasonQuestion> seasonQuestions = seasonQuestions(battleDrafts, seasons);
        List<PlannedBattle> battles = battleDrafts.stream().map(BattleDraft::toPlanned).toList();

        // 3.5 players (defense logs only), 3.6 unknown names (both logs)
        List<PlayerAutoAssignment> autoAssignments = new ArrayList<>();
        List<PlayerQuestion> playerQuestions = new ArrayList<>();
        players(repo, guild, drafts, autoAssignments, playerQuestions);
        List<UnknownNameQuestion> unknownNames = unknownNames(parser.names(), drafts);

        return new ImportPlan(plannedFiles(drafts, battleIndex), battles, List.of(), ownId, guildLink, seasonQuestions,
                autoAssignments, playerQuestions, unknownNames, guild.members().size(), guildFile);
    }

    private ImportPlan errorPlan(List<PlannedFile> files, List<PlanError> errors, Long ownId, int members,
                                 Path guildFile) {
        return new ImportPlan(files, List.of(), errors, ownId, null, List.of(), List.of(), List.of(), List.of(),
                members, guildFile);
    }

    private FileDraft read(BattleLogParser parser, Path path) {
        FileDraft d = new FileDraft(path);
        try {
            d.raw = Files.readAllBytes(path);
            d.parsed = parser.parse(path.getFileName().toString(), new ByteArrayInputStream(d.raw));
            if (!d.parsed.log().header().isComplete()) {
                d.fail(PlannedFile.Error.NO_HEAD_DATA, null);
            }
        } catch (BattleLogFormatException e) {
            d.fail(PlannedFile.Error.NOT_A_BATTLE_LOG, e.getMessage());
        } catch (IOException e) {
            d.fail(PlannedFile.Error.UNREADABLE, e.getMessage());
        }
        return d;
    }

    /** Groups the readable files by battle; of two files with the same direction the longer one wins (append-only). */
    private static Map<BattleKey, Map<LogDirection, FileDraft>> group(List<FileDraft> drafts) {
        Map<BattleKey, Map<LogDirection, FileDraft>> byBattle = new LinkedHashMap<>();
        for (FileDraft d : drafts) {
            if (d.status != PlannedFile.Status.READY) {
                continue;
            }
            BattleLogHeader h = d.header();
            Map<LogDirection, FileDraft> directions = byBattle.computeIfAbsent(
                    new BattleKey(h.date(), h.opponent().gameGuildId()), k -> new EnumMap<>(LogDirection.class));
            FileDraft existing = directions.get(h.direction());
            if (existing == null) {
                directions.put(h.direction(), d);
            } else if (d.parsed.log().entries().size() > existing.parsed.log().entries().size()) {
                existing.skip();
                directions.put(h.direction(), d);
            } else {
                d.skip();
            }
        }
        return byBattle;
    }

    private BattleDraft battle(Optional<JournalRepository> repo, BattleKey key, Map<LogDirection, FileDraft> files,
                               Map<Integer, BattleSummary> summaries, List<Season> seasons) throws JournalException {
        FileDraft any = files.containsKey(LogDirection.ATTACK) ? files.get(LogDirection.ATTACK) : files.get(LogDirection.DEFENSE);
        BattleLogHeader header = any.header();
        Integer battleId = repo.isPresent() ? repo.get().findBattle(key.date(), key.opponentId()).orElse(null) : null;

        Map<LogDirection, BattleLog> logs = new EnumMap<>(LogDirection.class);
        Map<LogDirection, PlannedBattle.LogAction> actions = new EnumMap<>(LogDirection.class);
        for (LogDirection direction : LogDirection.values()) {
            FileDraft file = files.get(direction);
            if (file != null) {
                logs.put(direction, file.parsed.log());
                PlannedBattle.LogAction action = PlannedBattle.LogAction.NEW;
                if (battleId != null) {
                    Optional<String> sha = repo.get().findLogSha256(battleId, direction);
                    if (sha.isPresent()) {
                        action = sha.get().equals(JournalRepository.sha256(file.raw))
                                ? PlannedBattle.LogAction.UNCHANGED : PlannedBattle.LogAction.REPLACE;
                    }
                }
                actions.put(direction, action);
            } else if (battleId != null) {
                repo.get().loadLog(battleId, direction).ifPresent(stored -> logs.put(direction, stored.log()));
            }
        }
        BattleLogCheck.Result check = BattleLogCheck.check(logs.get(LogDirection.ATTACK), logs.get(LogDirection.DEFENSE));

        List<PlannedBattle.Warning> warnings = new ArrayList<>();
        BattleSummary summary = battleId == null ? null : summaries.get(battleId);
        BattleStatus status = header.rankingPoints() == 0 ? BattleStatus.RUNNING : BattleStatus.FINISHED;
        if (summary != null && summary.status() == BattleStatus.FINISHED && status == BattleStatus.RUNNING) {
            status = BattleStatus.FINISHED;
            warnings.add(PlannedBattle.Warning.FINISHED_BATTLE_RUNNING_EXPORT);
        }
        if (check.verdict() == BattleLogCheck.Verdict.MISMATCH) {
            warnings.add(PlannedBattle.Warning.RANKING_POINTS_MISMATCH);
        }
        if (files.values().stream().anyMatch(f -> !f.parsed.problems().isEmpty())) {
            warnings.add(PlannedBattle.Warning.PARSE_PROBLEMS);
        }
        Integer seasonId = summary != null && summary.seasonId() != null ? summary.seasonId()
                : seasons.stream().filter(s -> s.contains(key.date())).map(Season::id).findFirst().orElse(null);
        return new BattleDraft(key, header, battleId, status, check, actions, seasonId, warnings);
    }

    /** One question per suggested season for the battles outside every known season (12-week raster). */
    private static List<SeasonQuestion> seasonQuestions(List<BattleDraft> battles, List<Season> seasons) {
        List<BattleDraft> open = battles.stream().filter(b -> b.seasonId == null).toList();
        if (open.isEmpty()) {
            return List.of();
        }
        Map<LocalDate, SeasonSuggestion> byStart = new TreeMap<>();
        LocalDate anchor = open.stream().map(b -> b.key.date()).min(Comparator.naturalOrder()).orElseThrow();
        for (BattleDraft b : open) {
            SeasonSuggestion s = suggest(b.key.date(), seasons, anchor);
            byStart.computeIfAbsent(s.start, k -> s).dates.add(b.key.date());
            b.seasonQuestionId = SeasonQuestion.idFor(s.start);
        }
        return byStart.values().stream()
                .map(s -> new SeasonQuestion(SeasonQuestion.idFor(s.start), s.kind, s.start, s.number,
                        s.dates.stream().distinct().sorted().toList()))
                .toList();
    }

    private static SeasonSuggestion suggest(LocalDate date, List<Season> seasons, LocalDate anchor) {
        if (seasons.isEmpty()) {
            long k = ChronoUnit.DAYS.between(anchor, date) / SEASON_DAYS;
            return new SeasonSuggestion(k == 0 ? SeasonQuestion.Kind.FIRST : SeasonQuestion.Kind.NEW,
                    anchor.plusDays(k * SEASON_DAYS), (int) k + 1);
        }
        Season first = seasons.stream().min(Comparator.comparing(Season::start)).orElseThrow();
        if (date.isBefore(first.start())) {
            long k = Math.ceilDiv(ChronoUnit.DAYS.between(date, first.start()), SEASON_DAYS);
            return new SeasonSuggestion(SeasonQuestion.Kind.EARLIER, first.start().minusDays(k * SEASON_DAYS),
                    first.number() - (int) k);
        }
        Season before = seasons.stream().filter(s -> !s.end().isAfter(date))
                .max(Comparator.comparing(Season::end)).orElse(first);
        long k = ChronoUnit.DAYS.between(before.end(), date) / SEASON_DAYS;
        return new SeasonSuggestion(SeasonQuestion.Kind.NEW, before.end().plusDays(k * SEASON_DAYS),
                before.number() + (int) k + 1);
    }

    /** Player questions and automatic assignments - from the defenders of the DEFENSE logs only. */
    private static void players(Optional<JournalRepository> repo, Guild guild, List<FileDraft> drafts,
                                List<PlayerAutoAssignment> autoAssignments, List<PlayerQuestion> questions)
            throws JournalException {
        Map<String, List<DefenderTeam>> defenders = new LinkedHashMap<>();
        for (FileDraft d : drafts) {
            if (d.status != PlannedFile.Status.READY || d.header().direction() != LogDirection.DEFENSE) {
                continue;
            }
            for (Fight fight : d.parsed.log().fights()) {
                defenders.computeIfAbsent(fight.defender().playerName(), k -> new ArrayList<>())
                        .add(DefenderTeam.of(fight));
            }
        }
        if (defenders.isEmpty()) {
            return;
        }
        Map<String, PlayerAssignment> assignments = repo.isPresent() ? repo.get().ownAssignmentsByName() : Map.of();
        Map<String, GuildMember> memberById = new LinkedHashMap<>();
        guild.members().forEach(m -> memberById.put(m.id(), m));

        Set<String> coveredMembers = new HashSet<>();
        List<String> undecided = new ArrayList<>();
        for (String name : defenders.keySet()) {
            PlayerAssignment a = assignments.get(name);
            if (a != null && a.status() == AssignmentStatus.ASSIGNED && memberById.containsKey(a.memberId())) {
                coveredMembers.add(a.memberId());
                continue;
            }
            if (a != null && (a.status() == AssignmentStatus.NOT_IN_COW2WIN || a.status() == AssignmentStatus.FORMER)) {
                continue;
            }
            Optional<GuildMember> exact = memberById.values().stream()
                    .filter(m -> name.equals(m.name()) || name.equals(m.id())).findFirst();
            Optional<GuildMember> normalized = exact.isPresent() ? Optional.empty() : memberById.values().stream()
                    .filter(m -> GameNameNormalizer.sameName(name, m.name()) || GameNameNormalizer.sameName(name, m.id()))
                    .findFirst();
            if (exact.isPresent() || normalized.isPresent()) {
                GuildMember member = exact.orElseGet(normalized::get);
                autoAssignments.add(new PlayerAutoAssignment(name, member.id(), exact.isEmpty()));
                coveredMembers.add(member.id());
            } else {
                undecided.add(name);
            }
        }

        Set<PlayerAnswer.Kind> allowed = EnumSet.allOf(PlayerAnswer.Kind.class);
        if (guild.members().size() >= Guild.MAX_MEMBERS) {
            allowed.remove(PlayerAnswer.Kind.CREATE);
        }
        List<GuildMember> renameCandidates = memberById.values().stream()
                .filter(m -> !coveredMembers.contains(m.id())).toList();
        for (String name : undecided) {
            questions.add(new PlayerQuestion(PlayerQuestion.idFor(name), name,
                    nameSuggestions(name, memberById.values()),
                    renameSuggestions(defenders.get(name), renameCandidates), allowed));
        }
    }

    private static List<PlayerQuestion.NameSuggestion> nameSuggestions(String name, Collection<GuildMember> members) {
        String key = GameNameNormalizer.key(name);
        List<PlayerQuestion.NameSuggestion> result = new ArrayList<>();
        for (GuildMember m : members) {
            int distance = Math.min(levenshtein(key, GameNameNormalizer.key(m.name())),
                    levenshtein(key, GameNameNormalizer.key(m.id())));
            if (distance <= MAX_NAME_DISTANCE) {
                result.add(new PlayerQuestion.NameSuggestion(m.id(), m.name(), distance));
            }
        }
        result.sort(Comparator.comparingInt(PlayerQuestion.NameSuggestion::distance)
                .thenComparing(PlayerQuestion.NameSuggestion::memberName));
        return result;
    }

    /**
     * Members (not occurring in this import's defense log) whose defense teams match a
     * defender team of the unknown player: the same heroes/titans (only if the log has
     * units) or a team power within {@link #RENAME_POWER_TOLERANCE}. No suggestion without evidence.
     */
    private static List<PlayerQuestion.RenameSuggestion> renameSuggestions(List<DefenderTeam> teams,
                                                                         List<GuildMember> candidates) {
        List<PlayerQuestion.RenameSuggestion> result = new ArrayList<>();
        for (GuildMember member : candidates) {
            Map<String, PlayerQuestion.RenameEvidence> evidence = new LinkedHashMap<>();
            for (DefenderTeam team : teams) {
                if (team.kind != TeamKind.TITAN) {
                    for (HeroTeam heroTeam : member.heroTeams()) {
                        Set<String> heroes = new HashSet<>(heroTeam.heroes().stream().map(Hero::id).toList());
                        addEvidence(evidence, team, TeamKind.HERO, heroTeam.index(), heroTeam.totalPower(),
                                !team.heroIds.isEmpty() && team.heroIds.equals(heroes));
                    }
                }
                if (team.kind != TeamKind.HERO) {
                    for (TitanTeam titanTeam : member.titanTeams()) {
                        Set<String> titans = new HashSet<>(titanTeam.titans().stream().map(Titan::id).toList());
                        addEvidence(evidence, team, TeamKind.TITAN, titanTeam.index(), titanTeam.totalPower(),
                                !team.titanIds.isEmpty() && team.titanIds.equals(titans));
                    }
                }
            }
            if (!evidence.isEmpty()) {
                List<PlayerQuestion.RenameEvidence> list = new ArrayList<>(evidence.values());
                list.sort(Comparator.comparing(PlayerQuestion.RenameEvidence::kind));
                result.add(new PlayerQuestion.RenameSuggestion(member.id(), member.name(), list));
            }
        }
        result.sort(Comparator.comparing((PlayerQuestion.RenameSuggestion s) -> !s.hasUnitMatch())
                .thenComparing(s -> -s.evidence().size())
                .thenComparing(PlayerQuestion.RenameSuggestion::memberName));
        return result;
    }

    private static void addEvidence(Map<String, PlayerQuestion.RenameEvidence> evidence, DefenderTeam team,
                                    TeamKind teamKind, int teamIndex, int memberPower, boolean sameUnits) {
        if (sameUnits) {
            evidence.putIfAbsent("U" + teamKind + teamIndex, new PlayerQuestion.RenameEvidence(
                    PlayerQuestion.RenameEvidence.Kind.SAME_UNITS, teamKind, teamIndex, team.power, memberPower));
        }
        if (memberPower > 0 && Math.abs(team.power - memberPower) <= memberPower * RENAME_POWER_TOLERANCE) {
            evidence.putIfAbsent("P" + teamKind + teamIndex, new PlayerQuestion.RenameEvidence(
                    PlayerQuestion.RenameEvidence.Kind.POWER, teamKind, teamIndex, team.power, memberPower));
        }
    }

    /** One question per (kind, name) without catalog id, from all importable files. */
    private static List<UnknownNameQuestion> unknownNames(NameResolver names, List<FileDraft> drafts) {
        Map<String, UnknownDraft> byId = new LinkedHashMap<>();
        for (FileDraft d : drafts) {
            if (d.status != PlannedFile.Status.READY) {
                continue;
            }
            for (BattleLogEntry entry : d.parsed.log().entries()) {
                if (entry.fortificationId() == null) {
                    addUnknown(byId, names, NameMappingKind.FORTIFICATION, entry.fortificationName());
                }
                if (entry instanceof Fight fight) {
                    for (FightSide side : List.of(fight.attacker(), fight.defender())) {
                        for (FightUnit unit : side.units()) {
                            if (!unit.isResolved()) {
                                addUnknown(byId, names, switch (unit.kind()) {
                                    case HERO -> NameMappingKind.HERO;
                                    case PET -> NameMappingKind.PET;
                                    case TITAN -> NameMappingKind.TITAN;
                                    case TOTEM -> NameMappingKind.TOTEM;
                                }, unit.name());
                            }
                            if (unit.patronage() != null && unit.patronage().petId() == null) {
                                addUnknown(byId, names, NameMappingKind.PET, unit.patronage().petName());
                            }
                        }
                    }
                }
            }
        }
        return byId.values().stream()
                .map(u -> new UnknownNameQuestion(u.id, u.kind, u.rawName, u.candidates, u.count)).toList();
    }

    private static void addUnknown(Map<String, UnknownDraft> byId, NameResolver names, NameMappingKind kind,
                                   String rawName) {
        String key = GameNameNormalizer.key(rawName);
        if (key.isEmpty()) {
            return;
        }
        String id = UnknownNameQuestion.idFor(kind, key);
        UnknownDraft draft = byId.computeIfAbsent(id, k -> {
            Set<String> candidates = Set.of();
            if (kind != NameMappingKind.TOTEM) {
                NameResolver.Match match = names.resolve(NameResolver.NameKind.valueOf(kind.name()), rawName);
                candidates = match.ambiguous() ? match.candidates() : Set.of();
            }
            return new UnknownDraft(id, kind, rawName, candidates);
        });
        draft.count++;
    }

    private static List<PlannedFile> plannedFiles(List<FileDraft> drafts, Map<BattleKey, Integer> battleIndex) {
        List<PlannedFile> result = new ArrayList<>();
        for (FileDraft d : drafts) {
            int battle = -1;
            if (d.status == PlannedFile.Status.READY) {
                BattleLogHeader h = d.header();
                battle = battleIndex.getOrDefault(new BattleKey(h.date(), h.opponent().gameGuildId()), -1);
            }
            result.add(new PlannedFile(d.path, d.status, d.error, d.detail, d.parsed, d.raw, battle));
        }
        return result;
    }

    // =====================================================================
    // execute
    // =====================================================================

    /**
     * Applies the answers: validates them (nothing changes on an error), changes the
     * guild in memory (game guild id, renamed and new members - left unsaved) and
     * writes the journal in one transaction. If writing fails, the guild changes are
     * taken back and the dirty state is restored.
     */
    public ImportResult execute(ImportPlan plan, ImportAnswers answers) {
        if (!plan.isImportable()) {
            return ImportResult.failed(List.of(new ImportResult.ImportError(
                    ImportResult.ImportError.Kind.PLAN_NOT_IMPORTABLE, null, null)));
        }
        Guild guild = context.guild();
        if (guild == null || !Objects.equals(context.guildFilePath(), plan.guildFile())) {
            return ImportResult.failed(List.of(new ImportResult.ImportError(
                    ImportResult.ImportError.Kind.GUILD_CHANGED, null, null)));
        }
        ImportAnswers a = answers == null ? ImportAnswers.defaults() : answers;
        List<ImportResult.ImportError> errors;
        try {
            errors = validate(plan, a, guild);
        } catch (JournalException e) {
            return writeFailed(e);
        }
        if (!errors.isEmpty()) {
            return ImportResult.failed(errors);
        }

        // Guild in memory: link, renames, new members (no teams), assignments to write.
        List<GuildMember> members = new ArrayList<>(guild.members());
        Map<String, PlannedAssignment> assignments = new LinkedHashMap<>();
        List<String> created = new ArrayList<>();
        List<ImportResult.RenamedMember> renamed = new ArrayList<>();
        for (PlayerAutoAssignment auto : plan.autoAssignments()) {
            assignments.put(auto.rawName(), new PlannedAssignment(auto.memberId(), AssignmentStatus.ASSIGNED));
        }
        for (PlayerQuestion q : plan.playerQuestions()) {
            PlayerAnswer answer = a.players().getOrDefault(q.id(), PlayerAnswer.of(PlayerAnswer.Kind.OPEN));
            switch (answer.kind()) {
                case ASSIGN -> assignments.put(q.rawName(), new PlannedAssignment(answer.memberId(), AssignmentStatus.ASSIGNED));
                case RENAME -> {
                    int index = indexOf(members, answer.memberId());
                    GuildMember old = members.get(index);
                    String newName = q.rawName().strip();
                    members.set(index, new GuildMember(old.id(), newName, old.heroTeams(), old.titanTeams()));
                    renamed.add(new ImportResult.RenamedMember(old.id(), old.name(), newName));
                    assignments.put(q.rawName(), new PlannedAssignment(old.id(), AssignmentStatus.ASSIGNED));
                }
                case CREATE -> {
                    String id = uniqueMemberId(q.rawName().strip(), members);
                    members.add(new GuildMember(id, id, List.of(), List.of()));
                    created.add(id);
                    assignments.put(q.rawName(), new PlannedAssignment(id, AssignmentStatus.ASSIGNED));
                }
                case NOT_IN_COW2WIN -> assignments.put(q.rawName(), new PlannedAssignment(null, AssignmentStatus.NOT_IN_COW2WIN));
                case FORMER -> assignments.put(q.rawName(), new PlannedAssignment(null, AssignmentStatus.FORMER));
                case OPEN -> assignments.put(q.rawName(), new PlannedAssignment(null, AssignmentStatus.OPEN));
            }
        }
        boolean link = plan.guildLink() != null && !Boolean.FALSE.equals(a.linkGuild());
        Guild updated = members.equals(guild.members()) ? guild : guild.withMembers(members);
        if (link) {
            updated = updated.withGameGuildId(plan.ownGameGuildId());
        }
        boolean guildChanged = !updated.equals(guild);
        boolean dirtyBefore = context.isGuildDirty();
        if (guildChanged) {
            guildService.updateGuild(updated);
        }

        try {
            JournalRepository repo = journal.repository(true)
                    .orElseThrow(() -> new JournalException("No guild open for the journal"));
            WriteSummary written = repo.inTransaction(r -> write(r, plan, a, assignments));
            Logger.log("Journal import: " + written.files.stream().filter(f -> f.outcome() != null).count()
                    + " file(s), " + written.battles.size() + " battle(s), " + written.fights + " fights, "
                    + created.size() + " new member(s), " + renamed.size() + " renamed, "
                    + written.assignments + " assignment(s)");
            GuildLog.event(GuildLog.dirOf(plan.guildFile()), "guildLog.journalImported", written.battles.size());
            return new ImportResult(List.of(), written.files, written.battles, written.fights, created, renamed,
                    written.assignments, written.nameMappings, link, written.warnings);
        } catch (JournalException | RuntimeException e) {
            if (guildChanged) {
                context.setGuild(guild);
                context.setGuildDirty(dirtyBefore);
            }
            return writeFailed(e);
        }
    }

    private static ImportResult writeFailed(Exception e) {
        Logger.logException("Journal import failed - nothing was imported", e);
        ImportResult.ImportError.Kind kind = e instanceof JournalLockedException
                ? ImportResult.ImportError.Kind.JOURNAL_LOCKED : ImportResult.ImportError.Kind.WRITE_FAILED;
        return ImportResult.failed(List.of(new ImportResult.ImportError(kind, null, e.getMessage())));
    }

    private List<ImportResult.ImportError> validate(ImportPlan plan, ImportAnswers a, Guild guild)
            throws JournalException {
        List<ImportResult.ImportError> errors = new ArrayList<>();
        Map<String, PlayerQuestion> questions = new HashMap<>();
        plan.playerQuestions().forEach(q -> questions.put(q.id(), q));
        Set<String> memberIds = new HashSet<>(guild.members().stream().map(GuildMember::id).toList());
        Set<String> renameTargets = new HashSet<>();
        int creates = 0;
        for (Map.Entry<String, PlayerAnswer> e : a.players().entrySet()) {
            PlayerQuestion q = questions.get(e.getKey());
            PlayerAnswer answer = e.getValue();
            if (q == null || !q.allowedAnswers().contains(answer.kind())) {
                errors.add(error(ImportResult.ImportError.Kind.ANSWER_NOT_ALLOWED, e.getKey()));
                continue;
            }
            switch (answer.kind()) {
                case ASSIGN, RENAME -> {
                    if (!memberIds.contains(answer.memberId())) {
                        errors.add(error(ImportResult.ImportError.Kind.UNKNOWN_MEMBER, e.getKey()));
                    } else if (answer.kind() == PlayerAnswer.Kind.RENAME && !renameTargets.add(answer.memberId())) {
                        errors.add(error(ImportResult.ImportError.Kind.DUPLICATE_RENAME, e.getKey()));
                    }
                }
                case CREATE -> creates++;
                default -> {
                }
            }
        }
        if (creates > 0 && guild.members().size() + creates > Guild.MAX_MEMBERS) {
            errors.add(error(ImportResult.ImportError.Kind.MEMBER_LIMIT, null));
        }

        Map<String, SeasonQuestion> seasonQuestions = new HashMap<>();
        plan.seasonQuestions().forEach(q -> seasonQuestions.put(q.id(), q));
        for (String id : a.seasons().keySet()) {
            if (!seasonQuestions.containsKey(id)) {
                errors.add(error(ImportResult.ImportError.Kind.ANSWER_NOT_ALLOWED, id));
            }
        }
        List<Season> existing = new ArrayList<>();
        Optional<JournalRepository> repo = journal.repository(false);
        if (repo.isPresent()) {
            existing.addAll(repo.get().listSeasons());
        }
        List<Map.Entry<String, LocalDate>> starts = new ArrayList<>();
        for (SeasonQuestion q : plan.seasonQuestions()) {
            Optional<LocalDate> start = a.seasons().getOrDefault(q.id(), Optional.of(q.suggestedStart()));
            if (start.isEmpty()) {
                continue;
            }
            LocalDate s = start.get();
            LocalDate end = s.plus(Season.DEFAULT_LENGTH);
            boolean overlaps = existing.stream().anyMatch(x -> s.isBefore(x.end()) && x.start().isBefore(end))
                    || starts.stream().anyMatch(x -> s.isBefore(x.getValue().plus(Season.DEFAULT_LENGTH))
                    && x.getValue().isBefore(end));
            if (overlaps) {
                errors.add(error(ImportResult.ImportError.Kind.SEASON_OVERLAP, q.id()));
            }
            starts.add(Map.entry(q.id(), s));
        }

        Map<String, UnknownNameQuestion> nameQuestions = new HashMap<>();
        plan.unknownNames().forEach(q -> nameQuestions.put(q.id(), q));
        NameResolver names = baseParser().names();
        for (Map.Entry<String, String> e : a.names().entrySet()) {
            UnknownNameQuestion q = nameQuestions.get(e.getKey());
            if (q == null) {
                errors.add(error(ImportResult.ImportError.Kind.ANSWER_NOT_ALLOWED, e.getKey()));
            } else if (!names.isValidTarget(q.kind(), e.getValue())) {
                errors.add(error(ImportResult.ImportError.Kind.INVALID_CATALOG_ID, e.getKey()));
            }
        }
        return errors;
    }

    private static ImportResult.ImportError error(ImportResult.ImportError.Kind kind, String questionId) {
        return new ImportResult.ImportError(kind, questionId, null);
    }

    /** Everything written to the journal - runs inside one transaction. */
    private WriteSummary write(JournalRepository repo, ImportPlan plan, ImportAnswers a,
                               Map<String, PlannedAssignment> assignments) throws JournalException {
        WriteSummary summary = new WriteSummary();
        for (UnknownNameQuestion q : plan.unknownNames()) {
            String catalogId = a.names().get(q.id());
            if (catalogId != null) {
                repo.putNameMapping(q.kind(), q.rawName(), catalogId);
                summary.nameMappings++;
            }
        }
        BattleLogParser parser = parser(repo.nameMappingsByKind());

        Map<String, Integer> seasonIds = new HashMap<>();
        Set<Integer> usedNumbers = new HashSet<>(repo.listSeasons().stream().map(Season::number).toList());
        for (SeasonQuestion q : plan.seasonQuestions()) {
            Optional<LocalDate> start = a.seasons().getOrDefault(q.id(), Optional.of(q.suggestedStart()));
            if (start.isPresent()) {
                int number = usedNumbers.contains(q.suggestedNumber())
                        ? usedNumbers.stream().max(Integer::compare).orElse(0) + 1 : q.suggestedNumber();
                usedNumbers.add(number);
                seasonIds.put(q.id(), repo.createSeason(number, start.get()).id());
            }
        }

        Map<Integer, Integer> battleIds = new LinkedHashMap<>();
        for (PlannedFile file : plan.files()) {
            if (file.status() != PlannedFile.Status.READY) {
                summary.files.add(new ImportResult.FileResult(file.path(), null, file.status()));
                continue;
            }
            PlannedBattle battle = plan.battles().get(file.battleKey());
            Integer seasonId = battle.seasonId() != null ? battle.seasonId()
                    : battle.seasonQuestionId() == null ? null : seasonIds.get(battle.seasonQuestionId());
            BattleLogParseResult parsed;
            try {
                parsed = parser.parse(file.path().getFileName().toString(), new ByteArrayInputStream(file.rawCsv()));
            } catch (IOException e) {
                throw new JournalException("Could not parse " + file.path() + " again: " + e.getMessage(), e);
            }
            SaveResult saved = repo.saveLog(parsed, file.rawCsv(), seasonId);
            summary.files.add(new ImportResult.FileResult(file.path(), saved.outcome(), file.status()));
            summary.warnings.addAll(saved.warnings());
            if (saved.outcome() != SaveResult.Outcome.UNCHANGED) {
                summary.fights += parsed.log().fights().size();
            }
            battleIds.put(file.battleKey(), saved.battleId());
        }

        for (Map.Entry<String, PlannedAssignment> e : assignments.entrySet()) {
            Optional<JournalPlayer> player = repo.findOwnPlayer(e.getKey());
            if (player.isPresent()) {
                repo.setAssignment(player.get().id(), e.getValue().memberId(), e.getValue().status());
                summary.assignments++;
            }
        }

        Map<Integer, BattleSummary> stored = new HashMap<>();
        repo.listBattles(null).forEach(s -> stored.put(s.battleId(), s));
        for (Map.Entry<Integer, Integer> e : battleIds.entrySet()) {
            PlannedBattle battle = plan.battles().get(e.getKey());
            BattleSummary s = stored.get(e.getValue());
            summary.battles.add(new ImportResult.BattleOutcome(e.getValue(), battle.date(), battle.opponent(),
                    s == null ? null : s.seasonId(), s == null ? battle.status() : s.status(), battle.check().verdict()));
        }
        return summary;
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private synchronized BattleLogParser baseParser() {
        if (baseParser == null) {
            baseParser = parserFactory.get();
        }
        return baseParser;
    }

    private BattleLogParser parser(Map<NameMappingKind, Map<String, String>> mappings) {
        return mappings.isEmpty() ? baseParser() : baseParser().withNameMappings(mappings);
    }

    private static int indexOf(List<GuildMember> members, String memberId) {
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).id().equals(memberId)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unknown member " + memberId);
    }

    /** {@code base}, or {@code base (2)}, {@code base (3)} ... if that id is taken. */
    static String uniqueMemberId(String base, List<GuildMember> members) {
        String name = base.isBlank() ? "Player" : base;
        Set<String> ids = new HashSet<>(members.stream().map(GuildMember::id).toList());
        String id = name;
        for (int n = 2; ids.contains(id); n++) {
            id = name + " (" + n + ")";
        }
        return id;
    }

    /** Levenshtein distance (insert, delete, replace = 1). */
    static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    private record BattleKey(LocalDate date, long opponentId) {
    }

    private record PlannedAssignment(String memberId, AssignmentStatus status) {
    }

    /** A defender team from the defense log: kind (if known), power and - if the log has units - its heroes/titans. */
    private record DefenderTeam(TeamKind kind, int power, Set<String> heroIds, Set<String> titanIds) {
        static DefenderTeam of(Fight fight) {
            Set<String> heroes = new HashSet<>();
            Set<String> titans = new HashSet<>();
            for (FightUnit unit : fight.defender().units()) {
                if (unit.catalogId() == null) {
                    continue;
                }
                if (unit.kind() == UnitKind.HERO) {
                    heroes.add(unit.catalogId());
                } else if (unit.kind() == UnitKind.TITAN) {
                    titans.add(unit.catalogId());
                }
            }
            return new DefenderTeam(fight.teamKind(), fight.defender().teamPower(), heroes, titans);
        }
    }

    private static final class FileDraft {
        final Path path;
        PlannedFile.Status status = PlannedFile.Status.READY;
        PlannedFile.Error error;
        String detail;
        BattleLogParseResult parsed;
        byte[] raw;

        FileDraft(Path path) {
            this.path = path;
        }

        BattleLogHeader header() {
            return parsed.log().header();
        }

        void fail(PlannedFile.Error error, String detail) {
            this.status = PlannedFile.Status.ERROR;
            this.error = error;
            this.detail = detail;
        }

        void skip() {
            this.status = PlannedFile.Status.SKIPPED;
            this.error = PlannedFile.Error.DUPLICATE_DIRECTION;
        }
    }

    private static final class BattleDraft {
        final BattleKey key;
        final BattleLogHeader header;
        final Integer battleId;
        final BattleStatus status;
        final BattleLogCheck.Result check;
        final Map<LogDirection, PlannedBattle.LogAction> actions;
        final Integer seasonId;
        final List<PlannedBattle.Warning> warnings;
        String seasonQuestionId;

        BattleDraft(BattleKey key, BattleLogHeader header, Integer battleId, BattleStatus status,
                    BattleLogCheck.Result check, Map<LogDirection, PlannedBattle.LogAction> actions, Integer seasonId,
                    List<PlannedBattle.Warning> warnings) {
            this.key = key;
            this.header = header;
            this.battleId = battleId;
            this.status = status;
            this.check = check;
            this.actions = actions;
            this.seasonId = seasonId;
            this.warnings = warnings;
        }

        PlannedBattle toPlanned() {
            return new PlannedBattle(key.date(), header.opponent(), battleId, header.result(), status,
                    header.rankingPoints(), check, actions, seasonId, seasonQuestionId, warnings);
        }
    }

    private static final class SeasonSuggestion {
        final SeasonQuestion.Kind kind;
        final LocalDate start;
        final int number;
        final List<LocalDate> dates = new ArrayList<>();

        SeasonSuggestion(SeasonQuestion.Kind kind, LocalDate start, int number) {
            this.kind = kind;
            this.start = start;
            this.number = number;
        }
    }

    private static final class UnknownDraft {
        final String id;
        final NameMappingKind kind;
        final String rawName;
        final Set<String> candidates;
        int count;

        UnknownDraft(String id, NameMappingKind kind, String rawName, Set<String> candidates) {
            this.id = id;
            this.kind = kind;
            this.rawName = rawName;
            this.candidates = candidates;
        }
    }

    private static final class WriteSummary {
        final List<ImportResult.FileResult> files = new ArrayList<>();
        final List<ImportResult.BattleOutcome> battles = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        int fights;
        int assignments;
        int nameMappings;
    }
}
