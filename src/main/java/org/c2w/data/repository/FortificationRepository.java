package org.c2w.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.data.model.*;
import org.c2w.util.JsonSupport;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class FortificationRepository {
    private static final String JSON_PATH = "/data/fortifications.json";


    private static final Path CATALOG_FILE_PATH = Paths.get("src", "main", "resources", "data", "fortifications.json");

    private static volatile Map<String, Fortification> fortificationsByid;

    /**
     * Returns the fortification with the given id, or Optional.empty() if no
     * fortification with this id exists.
     */
    public static Optional<Fortification> findById(String fortificationId) {
        ensureLoaded();
        return Optional.ofNullable(fortificationsByid.get(fortificationId));
    }

    /**
     * Returns all known fortifications as a list sorted by id.
     */
    public static List<Fortification> findAll() {
        ensureLoaded();
        return fortificationsByid.values().stream()
                .sorted(Comparator.comparing(Fortification::id))
                .collect(Collectors.toList());
    }

    /**
     * Returns the number of known fortifications.
     */
    public static int count() {
        ensureLoaded();
        return fortificationsByid.size();
    }

    /**
     * Resets the catalog (for tests). The JSON file is reloaded on the next
     * access.
     */
    public static void resetCache() {
        synchronized (FortificationRepository.class) {
            fortificationsByid = null;
        }
    }

    public static synchronized void save(List<Fortification> catalog) throws IOException {
        if (catalog == null) {
            throw new IllegalArgumentException("catalog must not be null");
        }

        JsonSupport.writeJsonFile(catalogToTree(catalog), CATALOG_FILE_PATH);

        Map<String, Fortification> updated = new LinkedHashMap<>();
        for (Fortification f : catalog) {
            updated.put(f.id(), f);
        }
        fortificationsByid = updated;
    }

    // --- private: loading ---

    private static void ensureLoaded() {
        if (fortificationsByid == null) {
            synchronized (FortificationRepository.class) {
                if (fortificationsByid == null) {
                    fortificationsByid = loadFortifications();
                }
            }
        }
    }

    private static Map<String, Fortification> loadFortifications() {
        try {
            String json = JsonSupport.readClasspathResource(FortificationRepository.class, JSON_PATH);
            return parseFortificationsJson(json);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load fortification catalog from " + JSON_PATH, e);
        }
    }

    private static Map<String, Fortification> parseFortificationsJson(String json) {
        Map<String, Fortification> result = new LinkedHashMap<>();

        JsonArray array = JsonParser.parseString(json).getAsJsonArray();
        for (var element : array) {
            Fortification fort = parseFortificationObject(element.getAsJsonObject());
            if (fort != null) {
                result.put(fort.id(), fort);
            }
        }

        return result;
    }

    private static Fortification parseFortificationObject(JsonObject obj) {
        String id = JsonSupport.getStringOrNull(obj, "id");
        String typeStr = JsonSupport.getStringOrNull(obj, "type");
        Integer capacity = JsonSupport.getInteger(obj, "capacity");
        Integer captureBonus = JsonSupport.getInteger(obj, "captureBonus");
        Integer row = JsonSupport.getInteger(obj, "row");
        Integer column = JsonSupport.getInteger(obj, "column");
        Integer importance = JsonSupport.getInteger(obj, "strategicImportance");
        List<String> prerequisites = JsonSupport.getStringList(obj, "prerequisites");
        Buff buff = parseBuff(JsonSupport.getObject(obj, "buff"));

        if (id == null || typeStr == null || capacity == null || captureBonus == null ||
            row == null || column == null || importance == null) {
            return null;
        }

        FortificationType type;
        try {
            type = FortificationType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return null;
        }

        return new Fortification(
                id,
                type,
                capacity,
                captureBonus,
                row,
                column,
                buff,
                prerequisites,
                importance
        );
    }

    private static Buff parseBuff(JsonObject buffObj) {
        if (buffObj == null) {
            return null;
        }
        try {
            String kind = JsonSupport.getStringOrNull(buffObj, "kind");
            String display = JsonSupport.getString(buffObj, "display", "");
            BuffEffect effect = BuffEffect.valueOf(JsonSupport.getStringOrNull(buffObj, "effect"));
            Double bonusPercent = JsonSupport.getDouble(buffObj, "bonusPercent");
            List<String> buffProfits = JsonSupport.getStringList(buffObj, "buffProfits");

            if ("ROLE".equals(kind)) {
                Role role = Role.valueOf(JsonSupport.getStringOrNull(buffObj, "role"));
                return new RoleBuff(role, effect, bonusPercent, buffProfits, display);
            }
            if ("ELEMENT".equals(kind)) {
                TitanElement element = TitanElement.valueOf(JsonSupport.getStringOrNull(buffObj, "element"));
                return new ElementBuff(element, effect, bonusPercent, buffProfits, display);
            }
            return null;
        } catch (RuntimeException e) {
            System.err.println("Could not parse fortification buff, skipping it: " + e.getMessage());
            return null;
        }
    }

    // --- private: writing (see #save) ---

    private static JsonArray catalogToTree(List<Fortification> catalog) {
        JsonArray tree = new JsonArray();
        for (Fortification f : catalog) {
            tree.add(fortificationToTree(f));
        }
        return tree;
    }

    private static JsonObject fortificationToTree(Fortification f) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", f.id());
        obj.addProperty("type", f.type().name());
        obj.addProperty("capacity", f.capacity());
        obj.addProperty("captureBonus", f.captureBonus());
        obj.addProperty("row", f.row());
        obj.addProperty("column", f.column());
        obj.add("buff", buffToTree(f.buff()));
        obj.add("prerequisites", JsonSupport.toStringArray(f.prerequisites()));
        obj.addProperty("strategicImportance", f.strategicImportance());
        return obj;
    }

    private static JsonElement buffToTree(Buff buff) {
        if (buff == null) {
            return JsonNull.INSTANCE;
        }
        JsonObject obj = new JsonObject();
        if (buff instanceof RoleBuff roleBuff) {
            obj.addProperty("kind", "ROLE");
            obj.addProperty("display", roleBuff.display());
            obj.addProperty("effect", roleBuff.effect().name());
            JsonSupport.addNumber(obj, "bonusPercent", roleBuff.bonusPercent());
            obj.add("buffProfits", JsonSupport.toStringArray(roleBuff.buffProfits()));
            obj.addProperty("role", roleBuff.role().name());
        } else if (buff instanceof ElementBuff elementBuff) {
            obj.addProperty("kind", "ELEMENT");
            obj.addProperty("display", elementBuff.display());
            obj.addProperty("effect", elementBuff.effect().name());
            JsonSupport.addNumber(obj, "bonusPercent", elementBuff.bonusPercent());
            obj.add("buffProfits", JsonSupport.toStringArray(elementBuff.buffProfits()));
            obj.addProperty("element", elementBuff.element().name());
        }
        return obj;
    }
}
