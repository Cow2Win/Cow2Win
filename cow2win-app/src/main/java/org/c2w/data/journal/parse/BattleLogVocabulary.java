package org.c2w.data.journal.parse;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.data.model.BuffEffect;
import org.c2w.data.model.HeroColor;
import org.c2w.i18n.GameNameNormalizer;
import org.c2w.i18n.LanguageService;
import org.c2w.infra.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The language-dependent texts of the Clash of Worlds battle logs of ONE game
 * language: file name words, column header row, fight results, "captured" and
 * "undefended" texts, the stats header, buff and color texts. Read from
 * {@code language/<language>/battleLogVocabulary.json} - like the
 * {@code .properties} file, everything language-dependent lives in the folder
 * of its language, one language per file.
 *
 * <p>The JSON is a tree of objects whose leaves are lists of texts (several
 * variants are possible); it is flattened into dotted keys like
 * {@code fileName.server} or {@code buff.ARMOR_INCREASE}. Texts that never
 * appeared in a log are not invented: their list stays empty (currently
 * {@code buff.SKILL_COOLDOWN_DECREASE}). Texts are compared via
 * {@link GameNameNormalizer}.
 */
public final class BattleLogVocabulary {

    /** File name of the vocabulary inside each language folder. */
    public static final String FILE_NAME = "battleLogVocabulary.json";

    public static final String FILE_NAME_SERVER = "fileName.server";
    public static final String FILE_NAME_WIN = "fileName.win";
    public static final String FILE_NAME_LOSS = "fileName.loss";
    public static final String FILE_NAME_DRAW = "fileName.draw";
    public static final String FILE_NAME_RANKING_POINTS = "fileName.rankingPoints";
    public static final String FILE_NAME_ATTACK_LOG = "fileName.attackLog";
    public static final String FILE_NAME_DEFENSE_LOG = "fileName.defenseLog";

    public static final String HEADER_FORTIFICATION = "headerRow.fortification";
    public static final String HEADER_RESULT = "headerRow.result";
    public static final String HEADER_POINTS = "headerRow.points";
    public static final String HEADER_ATTACKER = "headerRow.attacker";
    public static final String HEADER_DEFENDER = "headerRow.defender";

    public static final String FIGHT_WIN = "fightResult.win";
    public static final String FIGHT_LOSS = "fightResult.loss";

    public static final String FORTIFICATION_CAPTURED = "fortificationCaptured";

    public static final String UNDEFENDED_SINGULAR = "undefended.singular";
    public static final String UNDEFENDED_PLURAL = "undefended.plural";

    public static final String STATS_DAMAGE_DEALT = "statsHeader.damageDealt";
    public static final String STATS_DAMAGE_TAKEN = "statsHeader.damageTaken";
    public static final String STATS_HEALING = "statsHeader.healing";
    public static final String STATS_PATRONAGE = "statsHeader.patronage";

    /** Key prefix of the hero colors, followed by the {@link HeroColor} name. */
    public static final String HERO_COLOR_PREFIX = "heroColor.";

    /** Key prefix of the fortification buffs, followed by the {@link BuffEffect} name. */
    public static final String BUFF_PREFIX = "buff.";

    /** Placeholder for the number of undefended positions in the undefended texts. */
    public static final String PLACEHOLDER_FREE = "{n}";

    /** Placeholder for the total number of positions in the undefended texts. */
    public static final String PLACEHOLDER_TOTAL = "{m}";

    /** Buff effects no battle log has shown so far - their text lists stay empty. */
    static final Set<BuffEffect> UNSEEN_BUFFS = EnumSet.of(BuffEffect.SKILL_COOLDOWN_DECREASE);

    private final String language;
    private final Map<String, List<String>> texts;
    private final List<Pattern> undefendedPatterns;

    private BattleLogVocabulary(String language, Map<String, List<String>> texts) {
        this.language = language;
        this.texts = texts;
        List<Pattern> patterns = new ArrayList<>();
        for (String key : List.of(UNDEFENDED_SINGULAR, UNDEFENDED_PLURAL)) {
            for (String template : texts(key)) {
                Pattern pattern = undefendedPattern(template);
                if (pattern == null) {
                    Logger.log("battle log vocabulary '" + language + "': " + key
                            + " needs the placeholders " + PLACEHOLDER_FREE + " and " + PLACEHOLDER_TOTAL + ": " + template);
                } else {
                    patterns.add(pattern);
                }
            }
        }
        this.undefendedPatterns = List.copyOf(patterns);
    }

    /**
     * The vocabularies of every language from {@link LanguageService#availableLanguages()}.
     * A language folder without a vocabulary file (or with an unreadable one) is logged
     * and left out - it is simply not offered as a log language.
     */
    public static List<BattleLogVocabulary> loadAll() {
        List<BattleLogVocabulary> result = new ArrayList<>();
        for (String language : LanguageService.availableLanguages()) {
            load(language).ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    /** The vocabulary of {@code language}, empty (and logged) if the file is missing or unreadable. */
    public static Optional<BattleLogVocabulary> load(String language) {
        String path = LanguageService.languageResourcePath(language, FILE_NAME);
        try (InputStream in = LanguageService.openLanguageResource(language, FILE_NAME)) {
            if (in == null) {
                Logger.log("No battle log vocabulary for language '" + language + "' (" + path
                        + ") - battle logs in this language cannot be read");
                return Optional.empty();
            }
            return Optional.of(parse(language, new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (IOException | RuntimeException e) {
            Logger.logException("Could not read battle log vocabulary " + path, e);
            return Optional.empty();
        }
    }

    /** Parses a vocabulary JSON (see class Javadoc) for {@code language}. */
    public static BattleLogVocabulary parse(String language, String json) {
        Map<String, List<String>> texts = new TreeMap<>();
        flatten("", JsonParser.parseString(json), texts, language);
        return new BattleLogVocabulary(language, Collections.unmodifiableMap(texts));
    }

    /**
     * Keys that must have at least one text in every language: the file name words,
     * the column header row, fight results, "captured", both undefended texts, the
     * stats header, every {@link HeroColor} and every buff seen in a log so far.
     */
    public static Set<String> requiredKeys() {
        Set<String> keys = new TreeSet<>(List.of(
                FILE_NAME_SERVER, FILE_NAME_WIN, FILE_NAME_LOSS, FILE_NAME_DRAW, FILE_NAME_RANKING_POINTS,
                FILE_NAME_ATTACK_LOG, FILE_NAME_DEFENSE_LOG,
                HEADER_FORTIFICATION, HEADER_RESULT, HEADER_POINTS, HEADER_ATTACKER, HEADER_DEFENDER,
                FIGHT_WIN, FIGHT_LOSS, FORTIFICATION_CAPTURED, UNDEFENDED_SINGULAR, UNDEFENDED_PLURAL,
                STATS_DAMAGE_DEALT, STATS_DAMAGE_TAKEN, STATS_HEALING, STATS_PATRONAGE));
        for (HeroColor color : HeroColor.values()) {
            keys.add(HERO_COLOR_PREFIX + color.name());
        }
        for (BuffEffect effect : BuffEffect.values()) {
            if (!UNSEEN_BUFFS.contains(effect)) {
                keys.add(BUFF_PREFIX + effect.name());
            }
        }
        return keys;
    }

    /** The language name, as {@link LanguageService} names it (e.g. {@code deutsch}). */
    public String language() {
        return language;
    }

    /** All entries, flattened to dotted keys, sorted by key. */
    public Map<String, List<String>> entries() {
        return texts;
    }

    /** The texts of {@code key}, empty if the key is missing or has none. */
    public List<String> texts(String key) {
        return texts.getOrDefault(key, List.of());
    }

    /** True if {@code text} equals one of the texts of {@code key} (compared via {@link GameNameNormalizer}). */
    public boolean matches(String key, String text) {
        String wanted = GameNameNormalizer.key(text);
        if (wanted.isEmpty()) {
            return false;
        }
        return texts(key).stream().anyMatch(t -> GameNameNormalizer.key(t).equals(wanted));
    }

    /** The {@link BuffEffect} whose text is {@code text}, or {@code null}. */
    public BuffEffect buffEffect(String text) {
        return lookup(BuffEffect.class, BUFF_PREFIX, text);
    }

    /** The {@link HeroColor} whose text is {@code text}, or {@code null}. */
    public HeroColor heroColor(String text) {
        return lookup(HeroColor.class, HERO_COLOR_PREFIX, text);
    }

    /** Numbers of an undefended text: positions captured without a fight, out of all positions. */
    public record UndefendedPositions(int free, int total) {
    }

    /** Matches {@code text} against the undefended texts; {@code null} if none of this language matches. */
    public UndefendedPositions matchUndefended(String text) {
        String cleaned = GameNameNormalizer.clean(text);
        for (Pattern pattern : undefendedPatterns) {
            Matcher m = pattern.matcher(cleaned);
            if (m.matches()) {
                return new UndefendedPositions(Integer.parseInt(m.group("free")), Integer.parseInt(m.group("total")));
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "BattleLogVocabulary[" + language + "]";
    }

    // --- private ---

    private <E extends Enum<E>> E lookup(Class<E> type, String prefix, String text) {
        for (E constant : type.getEnumConstants()) {
            if (matches(prefix + constant.name(), text)) {
                return constant;
            }
        }
        return null;
    }

    /** Regex for an undefended template: literal text, {n}/{m} as number groups; {@code null} without both placeholders. */
    private static Pattern undefendedPattern(String template) {
        String cleaned = GameNameNormalizer.clean(template);
        if (!cleaned.contains(PLACEHOLDER_FREE) || !cleaned.contains(PLACEHOLDER_TOTAL)) {
            return null;
        }
        StringBuilder regex = new StringBuilder();
        int pos = 0;
        while (pos < cleaned.length()) {
            int nextFree = cleaned.indexOf(PLACEHOLDER_FREE, pos);
            int nextTotal = cleaned.indexOf(PLACEHOLDER_TOTAL, pos);
            int next = nextFree < 0 ? nextTotal : nextTotal < 0 ? nextFree : Math.min(nextFree, nextTotal);
            if (next < 0) {
                regex.append(Pattern.quote(cleaned.substring(pos)));
                break;
            }
            if (next > pos) {
                regex.append(Pattern.quote(cleaned.substring(pos, next)));
            }
            regex.append(next == nextFree ? "(?<free>\\d+)" : "(?<total>\\d+)");
            pos = next + PLACEHOLDER_FREE.length();
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static void flatten(String prefix, JsonElement element, Map<String, List<String>> out, String language) {
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                flatten(prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey(), e.getValue(), out, language);
            }
        } else if (element.isJsonArray()) {
            List<String> values = new ArrayList<>();
            for (JsonElement value : element.getAsJsonArray()) {
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    values.add(value.getAsString());
                } else {
                    Logger.log("battle log vocabulary '" + language + "': ignoring non-text value in " + prefix);
                }
            }
            out.put(prefix, List.copyOf(values));
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            out.put(prefix, List.of(element.getAsString()));
        } else {
            Logger.log("battle log vocabulary '" + language + "': ignoring unexpected value at " + prefix);
        }
    }
}
