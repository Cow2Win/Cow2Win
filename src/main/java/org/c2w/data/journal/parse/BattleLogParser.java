package org.c2w.data.journal.parse;

import org.c2w.data.journal.*;
import org.c2w.data.journal.parse.NameResolver.Match;
import org.c2w.data.journal.parse.NameResolver.NameKind;
import org.c2w.data.model.BuffEffect;
import org.c2w.data.model.HeroColor;
import org.c2w.data.model.TitanElement;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads an exported Clash of Worlds battle log (CSV) into a {@link BattleLog}.
 *
 * <p>Format (see the Weltenschlacht journal concept): UTF-8, optionally with a
 * BOM, CRLF or LF, comma-separated without quotes, varying column count per
 * row. The first row is the column header row and tells the game language.
 * Then, in file order:
 * <ul>
 *   <li>fortification rows without a position: undefended positions or "captured",</li>
 *   <li>single fight rows {@code <Fort> (Position: n),<result>,<points>,<attacker>,,,,,,<defender>,<buff>},</li>
 *   <li>optionally per fight a stats header row and the unit rows of both teams
 *       (attacker from column 3, defender from column 9) - in BOTH log directions,
 *       since a defense log may or may not contain them,</li>
 *   <li>empty rows between fights.</li>
 * </ul>
 *
 * <p>Tolerant by design: a row that cannot be understood becomes a
 * {@link ParseProblem} and is skipped, and parsing goes on; an unknown name is
 * kept with a {@code null} id plus a problem. Only an empty file or an unknown
 * header row throws {@link BattleLogFormatException}. Raw texts are always kept
 * unchanged; names are normalized for lookups only.
 */
public final class BattleLogParser {

    /**
     * Version of the parsing rules, stored with every saved log so a later
     * parser improvement can tell which logs to read again from their original
     * CSV. Increase it whenever the parse result of an existing file changes.
     */
    public static final int PARSER_VERSION = 1;

    /** Columns of a single fight row and of a unit row. */
    static final int ROW_COLUMNS = 14;

    /** Points per position captured without a fight. */
    public static final int UNDEFENDED_POINTS_PER_POSITION = 35;

    private static final int COL_FORTIFICATION = 0;
    private static final int COL_RESULT = 1;
    private static final int COL_POINTS = 2;
    private static final int COL_ATTACKER = 3;
    private static final int COL_DEFENDER = 9;
    private static final int COL_BUFF = 10;
    private static final int COL_STATS_DAMAGE_DEALT = 4;

    /** Offsets within one side of a unit row: unit, damage dealt, damage taken, healing, patronage. */
    private static final int OFFSET_DAMAGE_DEALT = 1;
    private static final int OFFSET_DAMAGE_TAKEN = 2;
    private static final int OFFSET_HEALING = 3;
    private static final int OFFSET_PATRONAGE = 4;

    private static final String UNIT_SEPARATOR = " | ";
    private static final char BOM = '﻿';

    /** {@code Brücke (Position: 6)}, FR {@code Pont (Position : 6)}. */
    private static final Pattern POSITION = Pattern.compile("^(?<fort>.*?)\\s*\\(Position\\s*:\\s*(?<position>\\d+)\\)$");
    /** {@code Name (Level-TeamPower)}; the name is kept exactly, including trailing spaces. */
    private static final Pattern PLAYER = Pattern.compile("^(?<name>.*) \\((?<level>\\d+)-(?<power>\\d+)\\)$", Pattern.DOTALL);
    /** {@code Rüstung erhöht (56%)}. */
    private static final Pattern BUFF = Pattern.compile("^(?<text>.*?)\\s*\\((?<percent>\\d+)\\s*%\\)$");
    /** {@code 6 stars} - not translated in any language. */
    private static final Pattern STARS = Pattern.compile("^(?<stars>\\d+)\\s+stars?$", Pattern.CASE_INSENSITIVE);
    /** {@code Rot}, {@code Rot +2} (non-breaking space before the plus). */
    private static final Pattern COLOR = Pattern.compile("^(?<color>.+?)(?:[\\s\\u00A0]*\\+(?<plus>\\d+))?$");
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private final List<BattleLogVocabulary> vocabularies;
    private final NameResolver names;

    public BattleLogParser(List<BattleLogVocabulary> vocabularies, NameResolver names) {
        if (vocabularies == null || names == null) {
            throw new IllegalArgumentException("BattleLogParser needs vocabularies and a name resolver");
        }
        this.vocabularies = List.copyOf(vocabularies);
        this.names = names;
    }

    /** A parser with the vocabularies of all available languages and the shipped catalogs. */
    public static BattleLogParser createDefault() {
        return new BattleLogParser(BattleLogVocabulary.loadAll(), NameResolver.load());
    }

    /** Parses {@code file}; its file name supplies the head data. */
    public BattleLogParseResult parse(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return parse(file.getFileName().toString(), in);
        }
    }

    /** Parses a battle log read from {@code in}; {@code fileName} (original export name) supplies the head data. */
    public BattleLogParseResult parse(String fileName, InputStream in) throws IOException {
        return parse(fileName, new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }

    /** Parses the content of a battle log; {@code fileName} (original export name) supplies the head data. */
    public BattleLogParseResult parse(String fileName, String content) throws BattleLogFormatException {
        String text = content == null ? "" : content;
        if (!text.isEmpty() && text.charAt(0) == BOM) {
            text = text.substring(1);
        }
        List<String> lines = text.lines().toList();
        if (lines.isEmpty()) {
            throw new BattleLogFormatException("Empty battle log: " + fileName);
        }
        String[] headerCells = lines.get(0).split(",", -1);
        BattleLogVocabulary vocabulary = vocabularies.stream()
                .filter(v -> isHeaderRow(headerCells, v))
                .findFirst()
                .orElseThrow(() -> new BattleLogFormatException(
                        "Unknown column header row in " + fileName + ": " + lines.get(0)));
        return new Run(vocabulary, fileName).parse(lines);
    }

    private static boolean isHeaderRow(String[] cells, BattleLogVocabulary v) {
        return cells.length > COL_DEFENDER
                && v.matches(BattleLogVocabulary.HEADER_FORTIFICATION, cells[COL_FORTIFICATION])
                && v.matches(BattleLogVocabulary.HEADER_RESULT, cells[COL_RESULT])
                && v.matches(BattleLogVocabulary.HEADER_POINTS, cells[COL_POINTS])
                && v.matches(BattleLogVocabulary.HEADER_ATTACKER, cells[COL_ATTACKER])
                && v.matches(BattleLogVocabulary.HEADER_DEFENDER, cells[COL_DEFENDER]);
    }

    /** The state of parsing one file. */
    private final class Run {
        private final BattleLogVocabulary vocabulary;
        private final String fileName;
        private final List<ParseProblem> problems = new ArrayList<>();
        private final List<BattleLogEntry> entries = new ArrayList<>();
        private FightBuilder fight;

        Run(BattleLogVocabulary vocabulary, String fileName) {
            this.vocabulary = vocabulary;
            this.fileName = fileName == null ? "" : fileName;
        }

        BattleLogParseResult parse(List<String> lines) {
            BattleLogHeader header = header();
            for (int i = 1; i < lines.size(); i++) {
                line(i + 1, lines.get(i));
            }
            finishFight();
            return new BattleLogParseResult(new BattleLog(header, entries), problems);
        }

        private BattleLogHeader header() {
            String plainName = fileName.replaceAll("^.*[/\\\\]", "");
            Optional<BattleLogFileName> parsed = BattleLogFileName.parse(plainName, vocabularies);
            if (parsed.isEmpty()) {
                problem(0, plainName, "file name not recognized - date, guilds, ranking points and direction unknown");
                return new BattleLogHeader(null, null, null, null, null, null, vocabulary.language(), plainName);
            }
            BattleLogFileName name = parsed.get();
            if (!name.language().equals(vocabulary.language())) {
                problem(0, plainName, "file name language '" + name.language()
                        + "' differs from the column header language '" + vocabulary.language() + "'");
            }
            name.problems().forEach(p -> problem(0, plainName, p));
            return new BattleLogHeader(name.date(), name.ownGuild(), name.opponent(), name.rankingPoints(),
                    name.result(), name.direction(), vocabulary.language(), plainName);
        }

        private void line(int lineNumber, String line) {
            if (line.isBlank()) {
                finishFight();
                return;
            }
            String[] cells = line.split(",", -1);
            if (!cells[COL_FORTIFICATION].isBlank()) {
                finishFight();
                Matcher position = POSITION.matcher(cells[COL_FORTIFICATION]);
                if (position.matches()) {
                    startFight(lineNumber, line, cells, position.group("fort"),
                            Integer.parseInt(position.group("position")));
                } else {
                    fortEvent(lineNumber, line, cells);
                }
            } else if (isStatsHeader(cells)) {
                statsHeader(lineNumber, line, cells);
            } else if (isUnitRow(cells)) {
                unitRow(lineNumber, line, cells);
            } else {
                problem(lineNumber, line, "unknown row");
            }
        }

        // --- single fight ---

        private void startFight(int lineNumber, String line, String[] cells, String fortName, int position) {
            if (cells.length <= COL_DEFENDER) {
                problem(lineNumber, line, "fight row with " + cells.length + " columns (expected " + ROW_COLUMNS + ")");
                return;
            }
            Matcher attacker = PLAYER.matcher(cells[COL_ATTACKER]);
            Matcher defender = PLAYER.matcher(cells[COL_DEFENDER]);
            if (!attacker.matches() || !defender.matches() || !blank(cells, COL_ATTACKER + 1, COL_DEFENDER)
                    || !blank(cells, ROW_COLUMNS, cells.length)) {
                problem(lineNumber, line, "attacker/defender not in columns " + COL_ATTACKER + "/" + COL_DEFENDER
                        + " as 'Name (Level-Power)' - a player name containing a comma?");
                return;
            }
            String resultText = cells[COL_RESULT];
            boolean attackerWins;
            if (vocabulary.matches(BattleLogVocabulary.FIGHT_WIN, resultText)) {
                attackerWins = true;
            } else if (vocabulary.matches(BattleLogVocabulary.FIGHT_LOSS, resultText)) {
                attackerWins = false;
            } else {
                problem(lineNumber, line, "unknown fight result '" + resultText + "'");
                return;
            }
            Integer points = parseInt(cells[COL_POINTS]);
            if (points == null) {
                problem(lineNumber, line, "points '" + cells[COL_POINTS] + "' are not a number");
                return;
            }
            Integer attackerLevel = parseInt(attacker.group("level"));
            Integer attackerPower = parseInt(attacker.group("power"));
            Integer defenderLevel = parseInt(defender.group("level"));
            Integer defenderPower = parseInt(defender.group("power"));
            if (attackerLevel == null || attackerPower == null || defenderLevel == null || defenderPower == null) {
                problem(lineNumber, line, "level or team power out of range");
                return;
            }
            DefenseBuff buff = cells.length > COL_BUFF && !cells[COL_BUFF].isBlank()
                    ? buff(lineNumber, line, cells[COL_BUFF]) : null;
            fight = new FightBuilder(fortificationId(lineNumber, line, fortName), fortName, position,
                    attackerWins, resultText, points, lineNumber,
                    attacker.group("name"), attackerLevel, attackerPower,
                    defender.group("name"), defenderLevel, defenderPower,
                    buff);
        }

        private DefenseBuff buff(int lineNumber, String line, String raw) {
            Matcher m = BUFF.matcher(raw.strip());
            String text = m.matches() ? m.group("text") : raw;
            Integer percent = m.matches() ? Integer.valueOf(m.group("percent")) : null;
            BuffEffect effect = vocabulary.buffEffect(text);
            if (effect == null) {
                problem(lineNumber, line, "unknown fortification buff '" + raw + "'");
            }
            return new DefenseBuff(effect, percent, raw);
        }

        private void finishFight() {
            if (fight != null) {
                entries.add(fight.build());
                fight = null;
            }
        }

        // --- fortification rows without a fight ---

        private void fortEvent(int lineNumber, String line, String[] cells) {
            String fortName = cells[COL_FORTIFICATION];
            String text = cells.length > COL_RESULT ? cells[COL_RESULT] : "";
            Integer points = cells.length > COL_POINTS ? parseInt(cells[COL_POINTS]) : null;
            if (points == null || !blank(cells, COL_POINTS + 1, cells.length)) {
                problem(lineNumber, line, "unknown row");
                return;
            }
            if (vocabulary.matches(BattleLogVocabulary.FORTIFICATION_CAPTURED, text)) {
                entries.add(new FortEvent(fortificationId(lineNumber, line, fortName), fortName, FortEventKind.CAPTURED,
                        null, null, text, points, lineNumber));
                return;
            }
            BattleLogVocabulary.UndefendedPositions undefended = vocabulary.matchUndefended(text);
            if (undefended == null) {
                undefended = guessUndefended(text, points);
                if (undefended == null) {
                    problem(lineNumber, line, "unknown row");
                    return;
                }
                problem(lineNumber, line, "unknown undefended text - recognized by its numbers and points");
            }
            entries.add(new FortEvent(fortificationId(lineNumber, line, fortName), fortName, FortEventKind.UNDEFENDED,
                    undefended.free(), undefended.total(), text, points, lineNumber));
        }

        /** Fallback for a new wording: exactly two numbers N <= M and points = 35 x N. */
        private BattleLogVocabulary.UndefendedPositions guessUndefended(String text, int points) {
            List<Integer> numbers = new ArrayList<>();
            Matcher m = NUMBER.matcher(text);
            while (m.find()) {
                Integer n = parseInt(m.group());
                if (n == null) {
                    return null;
                }
                numbers.add(n);
            }
            if (numbers.size() != 2) {
                return null;
            }
            int free = numbers.get(0);
            int total = numbers.get(1);
            if (free < 1 || free > total || points != free * UNDEFENDED_POINTS_PER_POSITION) {
                return null;
            }
            return new BattleLogVocabulary.UndefendedPositions(free, total);
        }

        // --- teams ---

        private boolean isStatsHeader(String[] cells) {
            return cells.length > COL_STATS_DAMAGE_DEALT && blank(cells, 0, COL_STATS_DAMAGE_DEALT)
                    && vocabulary.matches(BattleLogVocabulary.STATS_DAMAGE_DEALT, cells[COL_STATS_DAMAGE_DEALT]);
        }

        private void statsHeader(int lineNumber, String line, String[] cells) {
            if (fight == null) {
                problem(lineNumber, line, "stats header outside a single fight");
                return;
            }
            if (fight.teamKind != null) {
                problem(lineNumber, line, "repeated stats header in one single fight");
                return;
            }
            boolean patronage = false;
            for (String cell : cells) {
                patronage |= vocabulary.matches(BattleLogVocabulary.STATS_PATRONAGE, cell);
            }
            fight.teamKind = patronage ? TeamKind.HERO : TeamKind.TITAN;
        }

        private boolean isUnitRow(String[] cells) {
            return cells.length > COL_ATTACKER && blank(cells, 0, COL_ATTACKER)
                    && (cell(cells, COL_ATTACKER).contains("|") || cell(cells, COL_DEFENDER).contains("|"));
        }

        private void unitRow(int lineNumber, String line, String[] cells) {
            if (fight == null) {
                problem(lineNumber, line, "unit row outside a single fight");
                return;
            }
            if (fight.teamKind == null) {
                problem(lineNumber, line, "unit row before the stats header");
                return;
            }
            if (!blank(cells, ROW_COLUMNS, cells.length)) {
                problem(lineNumber, line, "unit row with " + cells.length + " columns (expected " + ROW_COLUMNS + ")");
                return;
            }
            if (!cell(cells, COL_ATTACKER).isBlank()) {
                FightUnit unit = unit(lineNumber, line, cells, COL_ATTACKER);
                if (unit != null) {
                    fight.attackerUnits.add(unit);
                }
            }
            if (!cell(cells, COL_DEFENDER).isBlank()) {
                FightUnit unit = unit(lineNumber, line, cells, COL_DEFENDER);
                if (unit != null) {
                    fight.defenderUnits.add(unit);
                }
            }
        }

        /** One side of a unit row starting at column {@code start}; {@code null} (plus a problem) if it can't be read. */
        private FightUnit unit(int lineNumber, String line, String[] cells, int start) {
            String unitCell = cell(cells, start);
            String[] parts = unitCell.split(Pattern.quote(UNIT_SEPARATOR), -1);
            Integer damageDealt = parseInt(cell(cells, start + OFFSET_DAMAGE_DEALT));
            Integer damageTaken = parseInt(cell(cells, start + OFFSET_DAMAGE_TAKEN));
            Integer healing = parseInt(cell(cells, start + OFFSET_HEALING));
            if (damageDealt == null || damageTaken == null || healing == null) {
                problem(lineNumber, line, "damage/healing of '" + unitCell + "' are not numbers");
                return null;
            }
            return fight.teamKind == TeamKind.HERO
                    ? heroOrPet(lineNumber, line, parts, unitCell, damageDealt, damageTaken, healing,
                    cell(cells, start + OFFSET_PATRONAGE))
                    : titanOrTotem(lineNumber, line, parts, unitCell, damageDealt, damageTaken, healing);
        }

        private FightUnit heroOrPet(int lineNumber, String line, String[] parts, String unitCell,
                                    int damageDealt, int damageTaken, int healing, String patronageCell) {
            if (parts.length != 5) {
                problem(lineNumber, line, "hero/pet '" + unitCell + "' is not 'Name | Color | n stars | Level | Power'");
                return null;
            }
            Integer stars = stars(parts[2]);
            Integer level = parseInt(parts[3]);
            Integer power = parseInt(parts[4]);
            Matcher color = COLOR.matcher(parts[1].strip());
            if (stars == null || level == null || power == null || !color.matches()) {
                problem(lineNumber, line, "hero/pet '" + unitCell + "' has an invalid color, stars, level or power");
                return null;
            }
            String name = parts[0];
            HeroColor heroColor = vocabulary.heroColor(color.group("color"));
            if (heroColor == null) {
                problem(lineNumber, line, "unknown hero color '" + parts[1] + "'");
            }
            int colorLevel = color.group("plus") == null ? 0 : Integer.parseInt(color.group("plus"));

            UnitKind kind;
            String id;
            Match pet = names.resolve(NameKind.PET, name);
            if (pet.isFound()) {
                kind = UnitKind.PET;
                id = pet.id();
            } else {
                kind = UnitKind.HERO;
                Match hero = names.resolve(NameKind.HERO, name);
                id = hero.id();
                if (!hero.isFound()) {
                    unknownName(lineNumber, line, "hero or pet", name, hero.ambiguous() ? hero : pet);
                }
            }
            Patronage patronage = patronageCell.isBlank() ? null : patronage(lineNumber, line, patronageCell);
            return new FightUnit(kind, name, id, null, heroColor, parts[1], colorLevel, stars, level, power,
                    damageDealt, damageTaken, healing, patronage);
        }

        private Patronage patronage(int lineNumber, String line, String cell) {
            String[] parts = cell.split(Pattern.quote(UNIT_SEPARATOR), -1);
            Integer power = parts.length == 2 ? parseInt(parts[1]) : null;
            if (power == null) {
                problem(lineNumber, line, "patronage '" + cell + "' is not 'Pet | Power'");
                return null;
            }
            Match pet = names.resolve(NameKind.PET, parts[0]);
            if (!pet.isFound()) {
                unknownName(lineNumber, line, "patronage pet", parts[0], pet);
            }
            return new Patronage(pet.id(), parts[0], power);
        }

        private FightUnit titanOrTotem(int lineNumber, String line, String[] parts, String unitCell,
                                       int damageDealt, int damageTaken, int healing) {
            if (parts.length != 3 && parts.length != 4) {
                problem(lineNumber, line, "titan/totem '" + unitCell
                        + "' is neither 'Name | n stars | Level | Power' nor 'Name | n stars | Level'");
                return null;
            }
            Integer stars = stars(parts[1]);
            Integer level = parseInt(parts[2]);
            Integer power = parts.length == 4 ? parseInt(parts[3]) : null;
            if (stars == null || level == null || (parts.length == 4 && power == null)) {
                problem(lineNumber, line, "titan/totem '" + unitCell + "' has invalid stars, level or power");
                return null;
            }
            String name = parts[0];
            if (parts.length == 3) {
                TitanElement element = names.totem(name);
                if (element == null) {
                    problem(lineNumber, line, "unknown totem name '" + name + "'");
                }
                return new FightUnit(UnitKind.TOTEM, name, null, element, null, null, 0, stars, level, null,
                        damageDealt, damageTaken, healing, null);
            }
            Match titan = names.resolve(NameKind.TITAN, name);
            if (!titan.isFound()) {
                unknownName(lineNumber, line, "titan", name, titan);
            }
            return new FightUnit(UnitKind.TITAN, name, titan.id(), null, null, null, 0, stars, level, power,
                    damageDealt, damageTaken, healing, null);
        }

        // --- helpers ---

        private String fortificationId(int lineNumber, String line, String fortName) {
            Match match = names.resolve(NameKind.FORTIFICATION, fortName);
            if (!match.isFound()) {
                unknownName(lineNumber, line, "fortification", fortName, match);
            }
            return match.id();
        }

        private void unknownName(int lineNumber, String line, String what, String name, Match match) {
            problem(lineNumber, line, match.ambiguous()
                    ? "ambiguous " + what + " name '" + name + "' (" + String.join(", ", match.candidates()) + ")"
                    : "unknown " + what + " name '" + name + "'");
        }

        private void problem(int lineNumber, String line, String reason) {
            problems.add(new ParseProblem(lineNumber, line, reason));
        }
    }

    /** A single fight while its unit rows are still being read. */
    private static final class FightBuilder {
        private final String fortificationId;
        private final String fortificationName;
        private final int position;
        private final boolean attackerWins;
        private final String resultText;
        private final int points;
        private final int lineNumber;
        private final String attackerName;
        private final int attackerLevel;
        private final int attackerPower;
        private final String defenderName;
        private final int defenderLevel;
        private final int defenderPower;
        private final DefenseBuff buff;
        private final List<FightUnit> attackerUnits = new ArrayList<>();
        private final List<FightUnit> defenderUnits = new ArrayList<>();
        private TeamKind teamKind;

        FightBuilder(String fortificationId, String fortificationName, int position, boolean attackerWins,
                     String resultText, int points, int lineNumber,
                     String attackerName, int attackerLevel, int attackerPower,
                     String defenderName, int defenderLevel, int defenderPower, DefenseBuff buff) {
            this.fortificationId = fortificationId;
            this.fortificationName = fortificationName;
            this.position = position;
            this.attackerWins = attackerWins;
            this.resultText = resultText;
            this.points = points;
            this.lineNumber = lineNumber;
            this.attackerName = attackerName;
            this.attackerLevel = attackerLevel;
            this.attackerPower = attackerPower;
            this.defenderName = defenderName;
            this.defenderLevel = defenderLevel;
            this.defenderPower = defenderPower;
            this.buff = buff;
        }

        Fight build() {
            boolean hasUnits = !attackerUnits.isEmpty() || !defenderUnits.isEmpty();
            return new Fight(fortificationId, fortificationName, position, hasUnits ? teamKind : null,
                    attackerWins, resultText, points,
                    new FightSide(attackerName, attackerLevel, attackerPower, null, attackerUnits),
                    new FightSide(defenderName, defenderLevel, defenderPower, buff, defenderUnits),
                    lineNumber);
        }
    }

    // --- static helpers ---

    private static String cell(String[] cells, int index) {
        return index < cells.length ? cells[index] : "";
    }

    /** True if every cell in {@code [from, to)} is blank (or missing). */
    private static boolean blank(String[] cells, int from, int to) {
        for (int i = from; i < Math.min(to, cells.length); i++) {
            if (!cells[i].isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static Integer stars(String text) {
        Matcher m = STARS.matcher(text.strip());
        return m.matches() ? parseInt(m.group("stars")) : null;
    }

    private static Integer parseInt(String text) {
        try {
            return Integer.valueOf(text.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
