package org.c2w.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.domain.ChangePlanChecks;
import org.c2w.infra.JsonSupport;
import org.c2w.infra.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;

/**
 * Reads and writes the checks of a guild's change plan ({@link ChangePlanChecks}) in
 * {@value #FILE_NAME} in the guild folder: {@code {"target": "...", "checked": ["id", ...]}}.
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
                    new LinkedHashSet<>(JsonSupport.getStringList(root, "checked")));
        } catch (IOException | RuntimeException e) {
            Logger.logException("Could not read the change plan checks " + file, e);
            return ChangePlanChecks.EMPTY;
        }
    }

    /** Writes {@code checks} to the guild folder {@code guildDir}. */
    public static void save(Path guildDir, ChangePlanChecks checks) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("target", checks.target());
        JsonArray checked = JsonSupport.toStringArray(checks.checkedIds().stream().sorted().toList());
        root.add("checked", checked);
        JsonSupport.writeJsonFile(root, fileFor(guildDir));
    }
}
