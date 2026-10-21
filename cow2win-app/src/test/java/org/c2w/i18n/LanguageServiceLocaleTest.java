package org.c2w.i18n;

import org.c2w.gui.journal.JournalTexts;
import org.c2w.infra.Config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** {@link LanguageService#locale()}: the general locale helper - the core no longer asks the journal for it. */
class LanguageServiceLocaleTest {

    /** The journal's own packages - they may use {@link JournalTexts} for anything. */
    private static final List<Path> JOURNAL_PACKAGES = List.of(
            Path.of("src", "main", "java", "org", "c2w", "gui", "journal"),
            Path.of("src", "main", "java", "org", "c2w", "data", "journal"),
            Path.of("src", "main", "java", "org", "c2w", "service", "journal"));

    @Test
    @DisplayName("The locale follows the configured language; the journal's locale delegates to it")
    void localeOfConfiguredLanguage() {
        String previous = Config.getLanguage();
        try {
            Config.setLanguage("deutsch");
            assertEquals(Locale.GERMANY, LanguageService.locale());
            Config.setLanguage("francais");
            assertEquals(Locale.FRANCE, LanguageService.locale());
            Config.setLanguage("espanol");
            assertEquals(Locale.of("es", "ES"), LanguageService.locale());
            assertEquals("es-ES", LanguageService.locale().toLanguageTag());
            Config.setLanguage("english");
            assertEquals(Locale.UK, LanguageService.locale());
            assertEquals(LanguageService.locale(), JournalTexts.locale());
        } finally {
            Config.setLanguage(previous);
        }
    }

    @Test
    @DisplayName("No class outside the journal packages takes its locale from JournalTexts")
    void coreDoesNotUseJournalLocale() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
            List<Path> offenders = files
                    .filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> JOURNAL_PACKAGES.stream().noneMatch(file::startsWith))
                    .filter(file -> {
                        try {
                            return Files.readString(file).contains("JournalTexts.locale()");
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList();
            assertEquals(List.of(), offenders);
        }
    }
}
