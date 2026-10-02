package org.c2w.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.infra.JsonSupport;
import org.c2w.infra.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Alternative in-game names ({@code "aliases"}) of heroes, titans and pets,
 * read from the optional {@code aliases} array of the entries in
 * {@code heroes.json}, {@code titans.json} and {@code pets.json} - the basis
 * for mapping the names in a Clash of Worlds battle log back to a catalog id
 * (Weltenschlacht journal).
 *
 * <p>Only for variants that are NOT already a display name in one of the
 * language files (those are searched anyway): short/long names, older
 * spellings. Pure classpath data without a workspace copy, like
 * {@link FortificationRepository}; deliberately kept out of the
 * {@code Hero}/{@code Titan}/{@code Pet} records, which don't need it.
 *
 * <p>Heroes, titans and pets share one id namespace (the language keys), so
 * the result is a single map keyed by id.
 */
public final class CatalogAliases {

    static final List<String> CATALOG_PATHS = List.of("/data/heroes.json", "/data/titans.json", "/data/pets.json");

    private final Map<String, List<String>> aliasesById;

    private CatalogAliases(Map<String, List<String>> aliasesById) {
        this.aliasesById = aliasesById;
    }

    /** Reads the aliases of the shipped hero, titan and pet catalogs. */
    public static CatalogAliases load() {
        List<String> jsons = new ArrayList<>();
        for (String path : CATALOG_PATHS) {
            try {
                jsons.add(JsonSupport.readClasspathResource(CatalogAliases.class, path));
            } catch (IOException e) {
                throw new RuntimeException("Failed to load catalog aliases from " + path, e);
            }
        }
        return parse(jsons);
    }

    /**
     * Parses catalog JSON arrays ({@code [{ "id": "...", "aliases": [...] }, ...]}).
     * Blank aliases and duplicates within one entry are dropped; entries without
     * aliases are simply absent from the result.
     */
    static CatalogAliases parse(List<String> catalogJsons) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String json : catalogJsons) {
            JsonArray array = JsonParser.parseString(json).getAsJsonArray();
            for (var element : array) {
                JsonObject obj = element.getAsJsonObject();
                String id = JsonSupport.getStringOrNull(obj, "id");
                if (id == null || id.isBlank()) {
                    continue;
                }
                Set<String> aliases = new LinkedHashSet<>(result.getOrDefault(id, List.of()));
                if (result.containsKey(id)) {
                    Logger.log("catalog aliases: id '" + id + "' appears in more than one catalog, merging aliases");
                }
                for (String alias : JsonSupport.getStringList(obj, "aliases")) {
                    if (alias != null && !alias.isBlank()) {
                        aliases.add(alias.trim());
                    }
                }
                if (!aliases.isEmpty()) {
                    result.put(id, List.copyOf(aliases));
                }
            }
        }
        return new CatalogAliases(Map.copyOf(result));
    }

    /** The aliases of {@code id}, empty if it has none (or is unknown). */
    public List<String> aliases(String id) {
        return aliasesById.getOrDefault(id, List.of());
    }

    /** All ids that have aliases, with their aliases. */
    public Map<String, List<String>> all() {
        return aliasesById;
    }
}
