package org.c2w.data.journal.parse;

import org.c2w.data.journal.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The parser is tolerant: broken rows become problems, parsing goes on. Small artificial CSVs. */
class BattleLogParserRobustnessTest {

    private static final String FILE_NAME =
            "01-01-2027 Deutscher Bund Server 133 (193861) - Gegner Server 1 (42) Sieg +800 Rangpunkte Angriffs-Log.csv";
    private static final String HEADER = "Befestigung,Ergebnis,Punkte,Angreifer,,,,,,Verteidiger,,,";
    private static final String TITAN_FIGHT = String.join("\r\n",
            "Brücke (Position: 1),Sieg,+35,Alice (130-1000),,,,,,Bob (129-900),Rüstung erhöht (56%),,,",
            ",,,,Zugefügter Schaden,Erlittener Schaden,Heilung,,,,Zugefügter Schaden,Erlittener Schaden,Heilung,",
            ",,,Araji | 6 stars | 130 | 209561,9531359,825825,0,,,Sigurd | 6 stars | 130 | 207442,3777708,13154377,0,",
            ",,,Feuergeisttotem | 4 stars | 130,32060697,0,1248000,,,,,,,");
    private static final String SECOND_FIGHT =
            "Kaserne (Position: 2),Niederlage,+5,Alice (130-1000),,,,,,Carol (130-800),,,,";

    @Test
    void validArtificialLogHasNoProblems() throws IOException {
        BattleLogParseResult result = parse(HEADER, TITAN_FIGHT, "", SECOND_FIGHT);

        assertEquals(List.of(), result.problems());
        assertEquals(2, result.log().fights().size());
        assertEquals(3, result.log().fights().get(0).attacker().units().size()
                + result.log().fights().get(0).defender().units().size());
        assertEquals(40, result.log().totalPoints());
    }

    @Test
    void unknownRowIsReportedAndParsingGoesOn() throws IOException {
        BattleLogParseResult result = parse(HEADER, "Brücke,Irgendwas Neues,+10", TITAN_FIGHT, "", SECOND_FIGHT);

        assertEquals(1, result.problems().size(), result.problems().toString());
        assertEquals(2, result.problems().get(0).lineNumber());
        assertEquals("Brücke,Irgendwas Neues,+10", result.problems().get(0).line());
        assertEquals(2, result.log().fights().size());
    }

    @Test
    void unknownHeroNameKeepsTheUnitWithoutId() throws IOException {
        BattleLogParseResult result = parse(HEADER,
                "Heldenbrücke (Position: 1),Sieg,+35,Alice (130-1000),,,,,,Bob (130-900),,,,",
                ",,,,Zugefügter Schaden,Erlittener Schaden,Heilung,Patronat,,,Zugefügter Schaden,Erlittener Schaden,Heilung,Patronat",
                ",,,Neuheld | Rot +2 | 6 stars | 130 | 197577,1,2,3,Fenris | 7958,,Dante | Rot | 6 stars | 130 | 1,0,0,0,");

        assertEquals(1, result.problems().size(), result.problems().toString());
        assertTrue(result.problems().get(0).reason().contains("Neuheld"));
        FightUnit unknown = result.log().fights().get(0).attacker().units().get(0);
        assertEquals("Neuheld", unknown.name());
        assertNull(unknown.catalogId());
        assertEquals(UnitKind.HERO, unknown.kind());
        assertEquals("fenris", unknown.patronage().petId());
        assertEquals("dante", result.log().fights().get(0).defender().units().get(0).catalogId());
    }

    @Test
    void unknownUndefendedSentenceIsRecognizedByItsNumbers() throws IOException {
        BattleLogParseResult result = parse(HEADER,
                "Kaserne,2 Plätze von 4 blieben leer.,+70",
                "Brücke,Nur eine Zahl 3,+35",
                SECOND_FIGHT);

        assertEquals(2, result.problems().size(), result.problems().toString());
        assertTrue(result.problems().get(0).reason().startsWith("unknown undefended text"));
        assertEquals("unknown row", result.problems().get(1).reason());
        FortEvent event = result.log().fortEvents().get(0);
        assertEquals(FortEventKind.UNDEFENDED, event.kind());
        assertEquals(2, event.freePositions());
        assertEquals(4, event.totalPositions());
        assertEquals("barracks", event.fortificationId());
    }

    @Test
    void withBomAndWithLfOnlyTheResultIsTheSame() throws IOException {
        Path sample = BattleLogTestFiles.file("de", "24-09-2026", LogDirection.ATTACK);
        String content = Files.readString(sample, StandardCharsets.UTF_8);
        assertTrue(content.contains("\r\n") && content.charAt(0) != '﻿', "sample: CRLF, no BOM");
        BattleLog original = BattleLogTestFiles.parse(sample).log();
        String fileName = sample.getFileName().toString();

        BattleLogParseResult withBom = BattleLogTestFiles.parser().parse(fileName, "﻿" + content);
        BattleLogParseResult lfOnly = BattleLogTestFiles.parser().parse(fileName, content.replace("\r\n", "\n"));
        BattleLogParseResult viaStream = BattleLogTestFiles.parser().parse(fileName,
                new ByteArrayInputStream(("﻿" + content).getBytes(StandardCharsets.UTF_8)));

        for (BattleLogParseResult result : List.of(withBom, lfOnly, viaStream)) {
            assertEquals(List.of(), result.problems());
            assertEquals(original, result.log());
        }
    }

    @Test
    void renamedFileIsParsedWithoutHeadData() throws IOException {
        BattleLogParseResult result = BattleLogTestFiles.parser().parse("kampf.csv",
                String.join("\r\n", HEADER, TITAN_FIGHT));

        assertEquals(1, result.problems().size());
        assertEquals(0, result.problems().get(0).lineNumber());
        BattleLogHeader header = result.log().header();
        assertFalse(header.isComplete());
        assertNull(header.date());
        assertNull(header.direction());
        assertNull(header.rankingPoints());
        assertEquals("deutsch", header.language());
        assertEquals("kampf.csv", header.fileName());
        assertEquals(1, result.log().fights().size());

        BattleLogCheck.Result check = BattleLogCheck.check(result.log(), null);
        assertEquals(BattleLogCheck.Verdict.NOT_CHECKABLE, check.verdict());
    }

    @Test
    void playerNameWithCommaIsReported() throws IOException {
        BattleLogParseResult result = parse(HEADER,
                "Brücke (Position: 1),Sieg,+35,Ali, Baba (130-1000),,,,,,Bob (130-900),,,,",
                "Brücke (Position: 2),Sieg,+35,Alice (130-1000),,,,,,Bo, b (130-900),,,,",
                SECOND_FIGHT);

        assertEquals(2, result.problems().size(), result.problems().toString());
        assertTrue(result.problems().get(0).reason().contains("comma"));
        assertEquals(1, result.log().fights().size(), "only the valid fight");
    }

    @Test
    void unknownBuffAndColorAreKeptAsRawText() throws IOException {
        BattleLogParseResult result = parse(HEADER,
                "Heldenbrücke (Position: 1),Sieg,+35,Alice (130-1000),,,,,,Bob (130-900),Abklingzeit verringert (7%),,,",
                ",,,,Zugefügter Schaden,Erlittener Schaden,Heilung,Patronat,,,Zugefügter Schaden,Erlittener Schaden,Heilung,Patronat",
                ",,,Dante | Gold +1 | 6 stars | 130 | 1,0,0,0,,,,,,,");

        assertEquals(2, result.problems().size(), result.problems().toString());
        Fight fight = result.log().fights().get(0);
        assertNull(fight.defender().buff().effect());
        assertEquals(7, fight.defender().buff().percent());
        assertEquals("Abklingzeit verringert (7%)", fight.defender().buff().rawText());
        FightUnit dante = fight.attacker().units().get(0);
        assertNull(dante.color());
        assertEquals("Gold +1", dante.colorText());
        assertEquals(1, dante.colorLevel());
    }

    @Test
    void brokenNumbersAndOrphanRowsAreReported() throws IOException {
        BattleLogParseResult result = parse(HEADER,
                ",,,Araji | 6 stars | 130 | 209561,1,2,3,,,,,,,",
                "Brücke (Position: 1),Sieg,viel,Alice (130-1000),,,,,,Bob (129-900),,,,",
                "Brücke (Position: 2),Sieg,+35,Alice (130-1000),,,,,,Bob (129-900),,,,",
                ",,,Araji | 6 stars | 130 | 209561,1,2,3,,,,,,,",
                ",,,,Zugefügter Schaden,Erlittener Schaden,Heilung,,,,Zugefügter Schaden,Erlittener Schaden,Heilung,",
                ",,,Araji | 6 stars | x | 209561,1,2,3,,,,,,,",
                ",,,Araji | 6 stars | 130 | 209561,1,zwei,3,,,,,,,");

        assertEquals(5, result.problems().size(), result.problems().toString());
        assertEquals(List.of(2, 3, 5, 7, 8), result.problems().stream().map(ParseProblem::lineNumber).toList());
        assertEquals(1, result.log().fights().size());
        assertEquals(List.of(), result.log().fights().get(0).attacker().units());
        assertNull(result.log().fights().get(0).teamKind(), "no units read");
    }

    /** Values are stored as int (largest seen so far: about 50 million) - anything beyond is a problem, not a crash. */
    @Test
    void valuesBeyondTheIntRangeAreReported() throws IOException {
        BattleLogParseResult result = parse(HEADER,
                "Brücke (Position: 1),Sieg,+35,Alice (130-3000000000),,,,,,Bob (129-900),,,,",
                "Brücke (Position: 2),Sieg,+35,Alice (130-1000),,,,,,Bob (129-900),,,,",
                ",,,,Zugefügter Schaden,Erlittener Schaden,Heilung,,,,Zugefügter Schaden,Erlittener Schaden,Heilung,",
                ",,,Araji | 6 stars | 130 | 209561,3000000000,0,0,,,Sigurd | 6 stars | 130 | 207442,1,2,3,");

        assertEquals(2, result.problems().size(), result.problems().toString());
        assertEquals("level or team power out of range", result.problems().get(0).reason());
        assertEquals(List.of(2, 5), result.problems().stream().map(ParseProblem::lineNumber).toList());
        Fight fight = result.log().fights().get(0);
        assertEquals(List.of(), fight.attacker().units(), "unit with too large damage skipped");
        assertEquals(1, fight.defender().units().size());
    }

    @Test
    void unknownHeaderRowOrEmptyFileIsAnError() {
        assertThrows(BattleLogFormatException.class,
                () -> BattleLogTestFiles.parser().parse(FILE_NAME, "Fort,Ergebnis,Punkte\r\n"));
        assertThrows(BattleLogFormatException.class, () -> BattleLogTestFiles.parser().parse(FILE_NAME, ""));
        assertThrows(BattleLogFormatException.class, () -> BattleLogTestFiles.parser().parse(FILE_NAME, "﻿"));
    }

    @Test
    void languageWithoutVocabularyIsNotOffered() throws IOException {
        BattleLogParser englishOnly = new BattleLogParser(
                List.of(BattleLogVocabulary.load("english").orElseThrow()), NameResolver.load());

        assertThrows(BattleLogFormatException.class, () -> englishOnly.parse(FILE_NAME, HEADER + "\r\n" + SECOND_FIGHT));
        assertTrue(BattleLogVocabulary.load("klingon").isEmpty());
    }

    @Test
    void fileNameInAnotherLanguageThanTheContentIsReported() throws IOException {
        String englishName = "01-01-2027 A Server 1 (2) - B Server 3 (4) Victory +800 ranking pts. Attack Log.csv";

        BattleLogParseResult result = BattleLogTestFiles.parser().parse(englishName, HEADER + "\r\n" + SECOND_FIGHT);

        assertEquals(1, result.problems().size(), result.problems().toString());
        assertEquals("deutsch", result.log().header().language(), "the content decides");
        assertEquals(LogDirection.ATTACK, result.log().header().direction());
    }

    private static BattleLogParseResult parse(String... lines) throws IOException {
        return BattleLogTestFiles.parser().parse(FILE_NAME, String.join("\r\n", lines) + "\r\n");
    }
}
