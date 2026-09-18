package org.c2w.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.data.model.*;
import org.c2w.util.JsonSupport;
import org.c2w.util.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class GuildRepository {

    private GuildRepository() {
    }

    /**
     * Loads the guild stored at the given path.
     *
     * @throws IOException if the file cannot be read, is not valid JSON, or
     *                      does not describe a valid {@link Guild} (e.g.
     *                      missing id)
     */
    public static Guild load(Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);

        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("Malformed guild JSON in " + path + ": " + e.getMessage(), e);
        }

        try {
            return guildFromJson(root);
        } catch (RuntimeException e) {
            throw new IOException("Could not interpret guild JSON in " + path + ": " + e.getMessage(), e);
        }
    }

    /**
     * Writes the given guild to the given path as pretty-printed JSON,
     * creating parent directories as needed.
     */
    public static void save(Guild guild, Path path) throws IOException {
        if (guild == null) {
            throw new IllegalArgumentException("guild must not be null");
        }
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        JsonSupport.writeJsonFile(guildToTree(guild), path);
    }

    /**
     * Deletes the given guild folder and everything in it (see
     * {@code org.c2w.gui.ToolbarPanel#onRemoveGuild}, added 2026-09-18) -
     * recursive, unlike {@link LineupRepository#delete}, since a guild
     * folder holds more than just the guild file itself (its
     * {@code *.lineup} files, generated reports, etc.).
     *
     * @throws IOException if the given path is not a directory, or any
     *                      file/subfolder in it cannot be deleted
     */
    public static void delete(Path guildDir) throws IOException {
        if (guildDir == null) {
            throw new IllegalArgumentException("guildDir must not be null");
        }
        if (!Files.isDirectory(guildDir)) {
            throw new IOException("Not a directory: " + guildDir);
        }
        try (var paths = Files.walk(guildDir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    // ---- Guild <-> JSON tree ----

    private static Guild guildFromJson(JsonObject obj) {
        String id = JsonSupport.getString(obj, "id", "");
        String name = JsonSupport.getString(obj, "name", "");
        int season = JsonSupport.getInt(obj, "season", 0);
        LocalDate seasonStart = JsonSupport.getLocalDate(obj, "seasonStart");

        List<GuildMember> members = new ArrayList<>();
        for (JsonElement memberEl : JsonSupport.getArray(obj, "members")) {
            members.add(memberFromJson(memberEl.getAsJsonObject()));
        }

        return new Guild(id, name, members, season, seasonStart);
    }

    private static JsonObject guildToTree(Guild guild) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", guild.id());
        obj.addProperty("name", guild.name());
        obj.addProperty("season", guild.season());
        JsonSupport.putNullable(obj, "seasonStart", guild.seasonStart() == null ? null : guild.seasonStart().toString());

        JsonArray members = new JsonArray();
        for (GuildMember member : guild.members()) {
            members.add(memberToTree(member));
        }
        obj.add("members", members);

        return obj;
    }

    private static GuildMember memberFromJson(JsonObject obj) {
        String id = JsonSupport.getString(obj, "id", "");
        String name = JsonSupport.getString(obj, "name", "");

        List<HeroTeam> heroTeams = new ArrayList<>();
        for (JsonElement teamEl : JsonSupport.getArray(obj, "heroTeams")) {
            heroTeams.add(heroTeamFromJson(teamEl.getAsJsonObject(), id));
        }

        List<TitanTeam> titanTeams = new ArrayList<>();
        for (JsonElement teamEl : JsonSupport.getArray(obj, "titanTeams")) {
            titanTeams.add(titanTeamFromJson(teamEl.getAsJsonObject(), id));
        }

        return new GuildMember(id, name, heroTeams, titanTeams);
    }

    private static JsonObject memberToTree(GuildMember member) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", member.id());
        obj.addProperty("name", member.name());

        JsonArray heroTeams = new JsonArray();
        for (HeroTeam team : member.heroTeams()) {
            heroTeams.add(heroTeamToTree(team));
        }
        obj.add("heroTeams", heroTeams);

        JsonArray titanTeams = new JsonArray();
        for (TitanTeam team : member.titanTeams()) {
            titanTeams.add(titanTeamToTree(team));
        }
        obj.add("titanTeams", titanTeams);

        return obj;
    }

    private static HeroTeam heroTeamFromJson(JsonObject obj, String memberId) {
        List<Hero> heroes = new ArrayList<>();
        for (String heroId : JsonSupport.getStringList(obj, "heroIds")) {
            HeroRepository.findById(heroId).ifPresentOrElse(heroes::add,
                    () -> Logger.log("Unknown hero id in guild file, skipping: " + heroId));
        }

        int totalPower = JsonSupport.getInt(obj, "totalPower", 0);
        LocalDate lastModified = JsonSupport.getLocalDate(obj, "lastModified");

        return new HeroTeam(memberId, heroes, totalPower, lastModified);
    }

    private static JsonObject heroTeamToTree(HeroTeam team) {
        JsonObject obj = new JsonObject();

        List<String> heroIds = new ArrayList<>();
        for (Hero hero : team.heroes()) {
            heroIds.add(hero.id());
        }
        obj.add("heroIds", JsonSupport.toStringArray(heroIds));

        obj.addProperty("totalPower", team.totalPower());
        JsonSupport.putNullable(obj, "lastModified", team.lastModified() == null ? null : team.lastModified().toString());

        return obj;
    }

    private static TitanTeam titanTeamFromJson(JsonObject obj, String memberId) {
        List<Titan> titans = new ArrayList<>();
        for (String titanId : JsonSupport.getStringList(obj, "titanIds")) {
            TitanRepository.findById(titanId).ifPresentOrElse(titans::add,
                    () -> Logger.log("Unknown titan id in guild file, skipping: " + titanId));
        }

        int totalPower = JsonSupport.getInt(obj, "totalPower", 0);
        LocalDate lastModified = JsonSupport.getLocalDate(obj, "lastModified");

        return new TitanTeam(memberId, titans, totalPower, lastModified);
    }

    private static JsonObject titanTeamToTree(TitanTeam team) {
        JsonObject obj = new JsonObject();

        List<String> titanIds = new ArrayList<>();
        for (Titan titan : team.titans()) {
            titanIds.add(titan.id());
        }
        obj.add("titanIds", JsonSupport.toStringArray(titanIds));

        obj.addProperty("totalPower", team.totalPower());
        JsonSupport.putNullable(obj, "lastModified", team.lastModified() == null ? null : team.lastModified().toString());

        return obj;
    }
}
