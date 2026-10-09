package org.c2w.gui.cowscore;

import org.c2w.gui.journal.JournalTexts;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The HTML of the "Info" tab of {@link CowScoreDialog}: {@code language/<language>/cowScoreInfo.html}
 * of the configured language (falling back to English), with its {@code ${name}} placeholders replaced
 * by {@link CowScoreInfoValues} - plain text replacement, no {@code MessageFormat} (the HTML contains
 * apostrophes and could contain braces).
 */
final class CowScoreInfoHtml {

    static final String FILE_NAME = "cowScoreInfo.html";
    private static final String FALLBACK_LANGUAGE = "english";
    private static final String KEY_MISSING = "cowScore.info.missing";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{[^}]*}");

    private CowScoreInfoHtml() {
    }

    /** The filled HTML for the configured language and the currently registered percentages. */
    static String build() {
        String template = template(LanguageService.configuredLanguage());
        if (template == null) {
            template = template(FALLBACK_LANGUAGE);
        }
        if (template == null) {
            Logger.log("CowScore info: " + FILE_NAME + " found neither for "
                    + LanguageService.configuredLanguage() + " nor for " + FALLBACK_LANGUAGE);
            return "<html><body><p>" + escape(LanguageService.displayName(KEY_MISSING)) + "</p></body></html>";
        }
        return fill(template, CowScoreInfoValues.values(JournalTexts.locale()));
    }

    /** The unfilled HTML of {@code language}, or null if that language has none. */
    static String template(String language) {
        try (InputStream in = LanguageService.openLanguageResource(language, FILE_NAME)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Logger.logException("Could not read " + LanguageService.languageResourcePath(language, FILE_NAME), e);
            return null;
        }
    }

    /** Replaces every {@code ${name}} of {@code values}; placeholders left over are logged. */
    static String fill(String template, Map<String, String> values) {
        String html = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            html = html.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        Matcher unknown = PLACEHOLDER.matcher(html);
        while (unknown.find()) {
            Logger.log("CowScore info: unknown placeholder " + unknown.group() + " in " + FILE_NAME);
        }
        return html;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
