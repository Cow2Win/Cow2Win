package org.c2w.data.journal.parse;

import com.google.gson.JsonParser;
import org.c2w.data.model.Fortification;
import org.c2w.data.model.TitanElement;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.i18n.GameNameNormalizer;
import org.c2w.i18n.LanguageService;
import org.c2w.i18n.TotemTexts;
import org.c2w.infra.JsonSupport;
import org.c2w.infra.Logger;

import java.io.IOException;
import java.util.*;

/**
 * Maps a raw name from a battle log (fortification, hero, pet, titan, totem)
 * back to its catalog id. A battle log is written in the game language of the
 * exporting account, so the lookup searches the display names of ALL language
 * files ({@link LanguageService#textIn}) - they carry the in-game names -
 * compared via {@link GameNameNormalizer}. A name that is not there (e.g. a
 * hero renamed by the game, in an old log) stays unresolved and is reported
 * by the parser.
 *
 * <p>The index is built once per kind, from the ids that exist in the
 * respective catalog only - the language files share one flat key space with
 * UI texts, so a key is never accepted just because it has a display name.
 * A name that maps to more than one id of the same kind is ambiguous and
 * resolves to nothing, like an unknown one.
 */
public final class NameResolver {

    /** What kind of name is looked up. */
    public enum NameKind {
        FORTIFICATION,
        HERO,
        PET,
        TITAN
    }

    /** Result of a lookup: the id, or {@code null} with {@link #ambiguous} telling unknown from ambiguous. */
    public record Match(String id, boolean ambiguous, Set<String> candidates) {
        static final Match UNKNOWN = new Match(null, false, Set.of());

        public boolean isFound() {
            return id != null;
        }
    }

    private static final Map<NameKind, String> CATALOG_PATHS = Map.of(
            NameKind.HERO, "/data/heroes.json",
            NameKind.PET, "/data/pets.json",
            NameKind.TITAN, "/data/titans.json");

    private final Map<NameKind, Map<String, Set<String>>> idsByNameKey;

    private NameResolver(Map<NameKind, Map<String, Set<String>>> idsByNameKey) {
        this.idsByNameKey = idsByNameKey;
    }

    /**
     * Builds the index from the shipped catalogs (fortifications, heroes, pets,
     * titans) and the display names of every available language.
     */
    public static NameResolver load() {
        Map<NameKind, Set<String>> idsByKind = new EnumMap<>(NameKind.class);
        idsByKind.put(NameKind.FORTIFICATION, new TreeSet<>(
                FortificationRepository.findAll().stream().map(Fortification::id).toList()));
        for (Map.Entry<NameKind, String> e : CATALOG_PATHS.entrySet()) {
            idsByKind.put(e.getKey(), readIds(e.getValue()));
        }
        List<String> languages = LanguageService.availableLanguages();

        Map<NameKind, Map<String, List<String>>> namesByKind = new EnumMap<>(NameKind.class);
        for (Map.Entry<NameKind, Set<String>> e : idsByKind.entrySet()) {
            Map<String, List<String>> namesById = new LinkedHashMap<>();
            for (String id : e.getValue()) {
                List<String> names = new ArrayList<>();
                for (String language : languages) {
                    String name = LanguageService.textIn(language, id);
                    if (name != null) {
                        names.add(name);
                    }
                }
                namesById.put(id, names);
            }
            namesByKind.put(e.getKey(), namesById);
        }
        return of(namesByKind);
    }

    /**
     * Builds a resolver from explicit names per kind and id (all names of all
     * languages) - for tests and for {@link #load()}.
     */
    static NameResolver of(Map<NameKind, Map<String, List<String>>> namesByKind) {
        Map<NameKind, Map<String, Set<String>>> index = new EnumMap<>(NameKind.class);
        for (NameKind kind : NameKind.values()) {
            Map<String, Set<String>> idsByKey = new HashMap<>();
            for (Map.Entry<String, List<String>> e : namesByKind.getOrDefault(kind, Map.of()).entrySet()) {
                for (String name : e.getValue()) {
                    String key = GameNameNormalizer.key(name);
                    if (!key.isEmpty()) {
                        idsByKey.computeIfAbsent(key, k -> new TreeSet<>()).add(e.getKey());
                    }
                }
            }
            idsByKey.replaceAll((k, ids) -> Collections.unmodifiableSet(ids));
            index.put(kind, Collections.unmodifiableMap(idsByKey));
        }
        return new NameResolver(index);
    }

    /** Looks up {@code rawName} among the names of {@code kind}. */
    public Match resolve(NameKind kind, String rawName) {
        Set<String> ids = idsByNameKey.get(kind).get(GameNameNormalizer.key(rawName));
        if (ids == null || ids.isEmpty()) {
            return Match.UNKNOWN;
        }
        if (ids.size() > 1) {
            return new Match(null, true, ids);
        }
        return new Match(ids.iterator().next(), false, ids);
    }

    /** The id of {@code rawName} of {@code kind}, or {@code null} if unknown or ambiguous. */
    public String id(NameKind kind, String rawName) {
        return resolve(kind, rawName).id();
    }

    /** The totem element of an in-game totem name in any language, or {@code null}. */
    public TitanElement totem(String rawName) {
        return TotemTexts.fromGameName(rawName).orElse(null);
    }

    private static Set<String> readIds(String path) {
        Set<String> ids = new TreeSet<>();
        try {
            for (var element : JsonParser.parseString(JsonSupport.readClasspathResource(NameResolver.class, path))
                    .getAsJsonArray()) {
                String id = JsonSupport.getStringOrNull(element.getAsJsonObject(), "id");
                if (id != null && !id.isBlank()) {
                    ids.add(id);
                }
            }
        } catch (IOException | RuntimeException e) {
            Logger.logException("Could not read catalog ids from " + path, e);
        }
        return ids;
    }
}
