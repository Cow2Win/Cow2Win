package org.c2w.data.journal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SeasonCalendarTest {

    private static final SeasonCalendar CALENDAR = SeasonCalendar.standard();

    private static Optional<Integer> numberOf(int year, int month, int day) {
        return CALENDAR.numberOf(LocalDate.of(year, month, day));
    }

    @Test
    @DisplayName("Seasons 16, 17 and the computed 18 with the breaks in between")
    void currentSeasons() {
        assertEquals(Optional.of(17), numberOf(2026, 10, 12));
        assertEquals(Optional.of(17), numberOf(2027, 1, 3));
        assertEquals(Optional.empty(), numberOf(2027, 1, 4), "break after season 17");
        assertEquals(Optional.of(18), numberOf(2027, 1, 11));
        assertEquals(Optional.empty(), numberOf(2026, 10, 11), "break before season 17");
        assertEquals(Optional.of(16), numberOf(2026, 10, 4));
        assertEquals(Optional.of(16), numberOf(2026, 7, 13));
        assertEquals(Optional.empty(), numberOf(2026, 7, 12));
    }

    @Test
    @DisplayName("The known exception: 2-week break between seasons 5 and 6")
    void twoWeekBreak() {
        assertEquals(Optional.of(5), numberOf(2023, 12, 31));
        for (LocalDate day = LocalDate.of(2024, 1, 1); day.isBefore(LocalDate.of(2024, 1, 15)); day = day.plusDays(1)) {
            assertEquals(Optional.empty(), CALENDAR.numberOf(day), day.toString());
        }
        assertEquals(Optional.of(6), numberOf(2024, 1, 15));
    }

    @Test
    @DisplayName("No season before the first known start (no backward computation)")
    void beforeTheFirstSeason() {
        assertEquals(Optional.empty(), numberOf(2023, 7, 9));
        assertEquals(Optional.of(4), numberOf(2023, 7, 10));
        assertEquals(Optional.empty(), numberOf(2020, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> CALENDAR.season(3));
    }

    @Test
    @DisplayName("After the last known start: 13-week cycle")
    void computedSeasons() {
        assertEquals(LocalDate.of(2027, 4, 12), CALENDAR.season(19).start());
        assertEquals(LocalDate.of(2027, 7, 12), CALENDAR.season(20).start());
        Season s17 = CALENDAR.season(17);
        assertEquals(new Season(17, LocalDate.of(2026, 10, 12), LocalDate.of(2027, 1, 4)), s17);
        assertEquals(LocalDate.of(2027, 1, 3), s17.lastDay());
        assertEquals(Optional.of(s17), CALENDAR.seasonOf(LocalDate.of(2026, 11, 1)));
        assertEquals(LocalDate.of(2024, 1, 15), CALENDAR.season(6).start(), "known starts win");
    }

    @Test
    @DisplayName("Every day maps to the season whose range contains it")
    void seasonOfIsConsistent() {
        for (LocalDate day = LocalDate.of(2023, 7, 1); day.isBefore(LocalDate.of(2028, 1, 1)); day = day.plusDays(1)) {
            LocalDate d = day;
            CALENDAR.seasonOf(day).ifPresent(s -> {
                assertTrue(s.contains(d), d.toString());
                assertEquals(s, CALENDAR.season(s.number()), d.toString());
            });
        }
    }

    @Test
    @DisplayName("Checks when loading: gapless numbers, Mondays only, at least 12 weeks apart")
    void validation() {
        LocalDate monday = LocalDate.of(2026, 10, 12);
        assertDoesNotThrow(() -> new SeasonCalendar(Map.of(1, monday, 2, monday.plusWeeks(12))));

        IllegalStateException gap = assertThrows(IllegalStateException.class,
                () -> new SeasonCalendar(Map.of(1, monday, 3, monday.plusWeeks(26))));
        assertTrue(gap.getMessage().contains("gapless"), gap.getMessage());
        IllegalStateException notMonday = assertThrows(IllegalStateException.class,
                () -> new SeasonCalendar(Map.of(1, monday.plusDays(1))));
        assertTrue(notMonday.getMessage().contains("Monday"), notMonday.getMessage());
        IllegalStateException tooClose = assertThrows(IllegalStateException.class,
                () -> new SeasonCalendar(Map.of(1, monday, 2, monday.plusWeeks(11))));
        assertTrue(tooClose.getMessage().contains("12 weeks"), tooClose.getMessage());
        assertThrows(IllegalStateException.class, () -> new SeasonCalendar(Map.of()));
    }

    @Test
    @DisplayName("Own start list: computation continues after its last start")
    void ownStartList() {
        SeasonCalendar calendar = new SeasonCalendar(Map.of(1, LocalDate.of(2026, 1, 5)));
        assertEquals(Optional.of(1), calendar.numberOf(LocalDate.of(2026, 3, 29)));
        assertEquals(Optional.empty(), calendar.numberOf(LocalDate.of(2026, 3, 30)));
        assertEquals(Optional.of(2), calendar.numberOf(LocalDate.of(2026, 4, 6)));
    }
}
