package org.c2w.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.data.model.Lineup;
import org.c2w.infra.JsonSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


public class LineupRepository {

    public static Lineup load(Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8).trim();

        Lineup lineup;
        try {
            lineup = parseLineupObject(JsonParser.parseString(json).getAsJsonObject());
        } catch (RuntimeException e) {
            throw new IOException("Could not interpret lineup JSON in " + path + ": " + e.getMessage(), e);
        }
        if (lineup == null) {
            throw new IOException("Could not interpret lineup JSON in " + path);
        }
        return lineup;
    }

    /**
     * Writes the given lineup to the given path as pretty-printed JSON,
     * creating parent directories as needed.
     */
    public static void save(Lineup lineup, Path path) throws IOException {
        if (lineup == null) {
            throw new IllegalArgumentException("lineup must not be null");
        }
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        JsonSupport.writeJsonFile(lineupToTree(lineup), path);
    }

    /**
     * Deletes the lineup file at the given path (see
     * {@code org.c2w.service.LineupService#deleteLineup}).
     *
     * @throws IOException if the file does not exist or cannot be deleted
     */
    public static void delete(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        Files.delete(path);
    }

    /**
     * Creates an empty dummy lineup for initial use when no lineups exist yet.
     * The lineup has no entries (empty team assignments) and serves as a
     * starting point for the application.
     *
     * @return An empty Lineup with default values
     */
    public static Lineup createEmptyLineup() {
        return new Lineup(
                "default",
                "Default Guild",
                "Empty",
                LocalDateTime.now(),
                List.of()
        );
    }

    /**
     * Parses a single lineup JSON object.
     *
     * @param obj The JSON object representing a lineup
     * @return The parsed Lineup, or null if parsing failed
     */
    private static Lineup parseLineupObject(JsonObject obj) {
        String guildId = JsonSupport.getStringOrNull(obj, "guildId");
        String guildName = JsonSupport.getStringOrNull(obj, "guildName");
        String algorithmName = JsonSupport.getStringOrNull(obj, "algorithmName");
        String createdAtStr = JsonSupport.getStringOrNull(obj, "createdAt");

        if (guildId == null || guildName == null || algorithmName == null || createdAtStr == null) {
            return null;
        }

        LocalDateTime createdAt;
        try {
            createdAt = LocalDateTime.parse(createdAtStr);
        } catch (Exception e) {
            return null;
        }

        List<Lineup.Entry> entries = parseEntries(obj);

        try {
            return new Lineup(guildId, guildName, algorithmName, createdAt, entries);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parses the entries array from a lineup JSON object.
     */
    private static List<Lineup.Entry> parseEntries(JsonObject lineupObj) {
        List<Lineup.Entry> entries = new ArrayList<>();

        for (JsonElement entryEl : JsonSupport.getArray(lineupObj, "entries")) {
            Lineup.Entry entry = parseEntryObject(entryEl.getAsJsonObject());
            if (entry != null) {
                entries.add(entry);
            }
        }

        return entries;
    }

    /**
     * Parses a single entry JSON object.
     */
    private static Lineup.Entry parseEntryObject(JsonObject obj) {
        String fortificationId = JsonSupport.getStringOrNull(obj, "fortificationId");
        String teamMemberId = JsonSupport.getStringOrNull(obj, "teamMemberId");
        String teamTypeStr = JsonSupport.getStringOrNull(obj, "teamType");
        Integer teamIndex = JsonSupport.getInteger(obj, "teamIndex");

        if (fortificationId == null || teamMemberId == null || teamTypeStr == null || teamIndex == null) {
            return null;
        }

        Lineup.TeamType teamType;
        try {
            teamType = Lineup.TeamType.valueOf(teamTypeStr);
        } catch (IllegalArgumentException e) {
            return null;
        }

        return new Lineup.Entry(
                fortificationId,
                teamMemberId,
                teamType,
                teamIndex
        );
    }

    // ---- Lineup <-> JSON tree ----

    private static JsonObject lineupToTree(Lineup lineup) {
        JsonObject obj = new JsonObject();
        obj.addProperty("guildId", lineup.guildId());
        obj.addProperty("guildName", lineup.guildName());
        obj.addProperty("algorithmName", lineup.algorithmName());
        obj.addProperty("createdAt", lineup.createdAt().toString());

        JsonArray entries = new JsonArray();
        for (Lineup.Entry entry : lineup.entries()) {
            entries.add(entryToTree(entry));
        }
        obj.add("entries", entries);

        return obj;
    }

    private static JsonObject entryToTree(Lineup.Entry entry) {
        JsonObject obj = new JsonObject();
        obj.addProperty("fortificationId", entry.fortificationId());
        obj.addProperty("teamMemberId", entry.teamMemberId());
        obj.addProperty("teamType", entry.teamType().name());
        obj.addProperty("teamIndex", entry.teamIndex());
        return obj;
    }
}
