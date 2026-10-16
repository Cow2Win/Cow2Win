package org.c2w.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.data.model.FortificationType;
import org.c2w.domain.ChangePlanChecks;
import org.c2w.infra.JsonSupport;
import org.c2w.infra.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Reads and writes the checks of a guild's change plan ({@link ChangePlanChecks}) in
 * {@value #FILE_NAME} in the guild folder: {@code {"target": "...", "checked": ["id", ...], "appliedTypes": ["HERO", ...]}}.
 * A file without {@code "appliedTypes"} (written before that field existed) reads as none applied;
 * unknown type names are ignored.
 */
public final class ChangePlanCheckRepository {

    public static final String FILE_NAME = "changeplan-checks.json";

    private ChangePlanCheckRepository() {
    }

    /** The checks file of the guild in {@code guildDir}. */
    public static Path fileFor(Path guildDir) {
        return guildDir.resolve(FILE_NAME);
    }

    /** The saved checks; {@link ChangePlanChecks#EMPTY} without a file or if it cannot be read (logged). */
    public static ChangePlanChecks load(Path guildDir) {
        Path file = fileFor(guildDir);
        if (!Files.isRegularFile(file)) {
            return ChangePlanChecks.EMPTY;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            return new ChangePlanChecks(JsonSupport.getString(root, "target", ""),
                    new LinkedHashSet<>(JsonSupport.getStringList(root, "checked")), appliedTypes(root));
        } catch (IOException | RuntimeException e) {
            Logger.logException("Could not read the change plan checks " + file, e);
            return ChangePlanChecks.EMPTY;
        }
    }

    private static Set<FortificationType> appliedTypes(JsonObject root) {
        Set<FortificationType> types = EnumSet.noneOf(FortificationType.class);
        for (String name : JsonSupport.getStringList(root, "appliedTypes")) {
            try {
                types.add(FortificationType.valueOf(name));
            } catch (IllegalArgumentException e) {
                Logger.log("Unknown fortification type in the change plan checks: " + name);
            }
        }
        return types;
    }

    /** Writes {@code checks} to the guild folder {@code guildDir}. */
    public static void save(Path guildDir, ChangePlanChecks checks) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("target", checks.target());
        JsonArray checked = JsonSupport.toStringArray(checks.checkedIds().stream().sorted().toList());
        root.add("checked", checked);
        root.add("appliedTypes", JsonSupport.toStringArray(checks.appliedTypes().stream().map(Enum::name).sorted().toList()));
        JsonSupport.writeJsonFile(root, fileFor(guildDir));
    }
}
