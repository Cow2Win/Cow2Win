package org.c2w.data.journal.parse;

import com.google.gson.JsonParser;
import org.c2w.data.journal.NameMappingKind;
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
 * by the parser - unless the journal has a manual mapping for it, see
 * {@link #withMappings}.
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
        TITAN;

        /** The matching kind of a manual name mapping. */
        public NameMappingKind mappingKind() {
            return NameMappingKind.valueOf(name());
        }
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
    private final Map<NameKind, Set<String>> catalogIds;
    /** Manual mappings: kind -> name key -> catalog id (TOTEM: element name). */
    private final Map<NameMappingKind, Map<String, String>> mappings;

    private NameResolver(Map<NameKind, Map<String, Set<String>>> idsByNameKey, Map<NameKind, Set<String>> catalogIds,
                         Map<NameMappingKind, Map<String, String>> mappings) {
        this.idsByNameKey = idsByNameKey;
        this.catalogIds = catalogIds;
        this.mappings = mappings;
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
     * languages) - for tests and for {@link #load()}. The ids of the map are the
     * catalog ids of that kind.
     */
    static NameResolver of(Map<NameKind, Map<String, List<String>>> namesByKind) {
        Map<NameKind, Map<String, Set<String>>> index = new EnumMap<>(NameKind.class);
        Map<NameKind, Set<String>> ids = new EnumMap<>(NameKind.class);
        for (NameKind kind : NameKind.values()) {
            Map<String, List<String>> namesById = namesByKind.getOrDefault(kind, Map.of());
            Map<String, Set<String>> idsByKey = new HashMap<>();
            for (Map.Entry<String, List<String>> e : namesById.entrySet()) {
                for (String name : e.getValue()) {
                    String key = GameNameNormalizer.key(name);
                    if (!key.isEmpty()) {
                        idsByKey.computeIfAbsent(key, k -> new TreeSet<>()).add(e.getKey());
                    }
                }
            }
            idsByKey.replaceAll((k, set) -> Collections.unmodifiableSet(set));
            index.put(kind, Collections.unmodifiableMap(idsByKey));
            ids.put(kind, Set.copyOf(namesById.keySet()));
        }
        return new NameResolver(index, ids, Map.of());
    }

    /**
     * This resolver plus manual mappings (raw name -> catalog id, or element name
     * for {@link NameMappingKind#TOTEM}), as stored in the journal. A mapping only
     * applies when the language files cannot resolve the name unambiguously - a
     * known name always wins. Mappings to ids that are not in the catalog (or to an
     * unknown element) are ignored and logged. Names are compared via
     * {@link GameNameNormalizer}.
     */
    public NameResolver withMappings(Map<NameMappingKind, Map<String, String>> rawMappings) {
        Map<NameMappingKind, Map<String, String>> result = new EnumMap<>(NameMappingKind.class);
        mappings.forEach((kind, byKey) -> result.put(kind, new HashMap<>(byKey)));
        rawMappings.forEach((kind, byName) -> byName.forEach((rawName, id) -> {
            if (isValidTarget(kind, id)) {
                result.computeIfAbsent(kind, k -> new HashMap<>()).put(GameNameNormalizer.key(rawName), id);
            } else {
                Logger.log("Ignoring name mapping " + kind + " '" + rawName + "' -> '" + id + "': not in the catalog");
            }
        }));
        result.replaceAll((kind, byKey) -> Map.copyOf(byKey));
        return new NameResolver(idsByNameKey, catalogIds, Map.copyOf(result));
    }

    /** True if {@code id} is a valid target of a mapping of {@code kind}. */
    public boolean isValidTarget(NameMappingKind kind, String id) {
        if (id == null) {
            return false;
        }
        if (kind == NameMappingKind.TOTEM) {
            return Arrays.stream(TitanElement.values()).anyMatch(e -> e.name().equals(id));
        }
        return catalogIds.get(NameKind.valueOf(kind.name())).contains(id);
    }

    /** Looks up {@code rawName} among the names of {@code kind}, then among the manual mappings. */
    public Match resolve(NameKind kind, String rawName) {
        String key = GameNameNormalizer.key(rawName);
        Match match = fromLanguageFiles(kind, key);
        if (match.isFound()) {
            return match;
        }
        String mapped = mappings.getOrDefault(kind.mappingKind(), Map.of()).get(key);
        return mapped == null ? match : new Match(mapped, false, Set.of(mapped));
    }

    /** The id of {@code rawName} of {@code kind}, or {@code null} if unknown or ambiguous. */
    public String id(NameKind kind, String rawName) {
        return resolve(kind, rawName).id();
    }

    /** The totem element of an in-game totem name in any language (or a manual mapping), or {@code null}. */
    public TitanElement totem(String rawName) {
        TitanElement element = TotemTexts.fromGameName(rawName).orElse(null);
        if (element != null) {
            return element;
        }
        String mapped = mappings.getOrDefault(NameMappingKind.TOTEM, Map.of()).get(GameNameNormalizer.key(rawName));
        return mapped == null ? null : TitanElement.valueOf(mapped);
    }

    private Match fromLanguageFiles(NameKind kind, String key) {
        Set<String> ids = idsByNameKey.get(kind).get(key);
        if (ids == null || ids.isEmpty()) {
            return Match.UNKNOWN;
        }
        if (ids.size() > 1) {
            return new Match(null, true, ids);
        }
        return new Match(ids.iterator().next(), false, ids);
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
