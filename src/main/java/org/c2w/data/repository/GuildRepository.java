package org.c2w.data.repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.c2w.data.model.*;
import org.c2w.infra.JsonSupport;
import org.c2w.infra.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GuildRepository {

    private GuildRepository() {
    }

    /**
     * Loads the guild stored at the given path, resolving hero/titan/pet/war
     * flag ids against {@code catalog}.
     *
     * @throws IOException if the file cannot be read, is not valid JSON, or
     *                      does not describe a valid {@link Guild} (e.g.
     *                      missing id)
     */
    public static Guild load(Path path, Catalog catalog) throws IOException {
        if (catalog == null) {
            throw new IllegalArgumentException("catalog must not be null");
        }
        String json = Files.readString(path, StandardCharsets.UTF_8);

        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("Malformed guild JSON in " + path + ": " + e.getMessage(), e);
        }

        try {
            return guildFromJson(root, catalog);
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
     * {@code org.c2w.service.GuildService#deleteGuild}) -
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

    private static Guild guildFromJson(JsonObject obj, Catalog catalog) {
        String id = JsonSupport.getString(obj, "id", "");
        String name = JsonSupport.getString(obj, "name", "");
        int season = JsonSupport.getInt(obj, "season", 0);
        LocalDate seasonStart = JsonSupport.getLocalDate(obj, "seasonStart");

        List<GuildMember> members = new ArrayList<>();
        for (JsonElement memberEl : JsonSupport.getArray(obj, "members")) {
            members.add(memberFromJson(memberEl.getAsJsonObject(), catalog));
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

    private static GuildMember memberFromJson(JsonObject obj, Catalog catalog) {
        String id = JsonSupport.getString(obj, "id", "");
        String name = JsonSupport.getString(obj, "name", "");

        List<HeroTeam> heroTeams = new ArrayList<>();
        JsonArray heroTeamsArr = JsonSupport.getArray(obj, "heroTeams");
        // Shared across this member's hero teams - see heroTeamFromJson: a pet/war flag used twice is dropped from
        // the later team instead of failing the whole load on GuildMember's "at most once per member" rule.
        Set<String> usedPetIds = new HashSet<>();
        Set<String> usedWarFlagIds = new HashSet<>();
        for (int i = 0; i < heroTeamsArr.size(); i++) {
            heroTeams.add(heroTeamFromJson(heroTeamsArr.get(i).getAsJsonObject(), id, i, usedPetIds, usedWarFlagIds,
                    catalog));
        }

        List<TitanTeam> titanTeams = new ArrayList<>();
        JsonArray titanTeamsArr = JsonSupport.getArray(obj, "titanTeams");
        for (int i = 0; i < titanTeamsArr.size(); i++) {
            titanTeams.add(titanTeamFromJson(titanTeamsArr.get(i).getAsJsonObject(), id, i, catalog));
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

    /**
     * Reads one hero team. "petId"/"warFlagId" are optional - missing (guild
     * files saved before pets/war flags existed) or null both mean "none".
     * An unknown id is logged and treated as "none", same as an unknown hero
     * id. A pet/war flag already used by an earlier hero team of the same
     * member (tracked via {@code usedPetIds}/{@code usedWarFlagIds}) is
     * logged and dropped from this team, so a hand-edited file can't make
     * the whole guild unloadable via {@link GuildMember}'s "at most once per
     * member" rule.
     */
    private static HeroTeam heroTeamFromJson(JsonObject obj, String memberId, int index,
                                             Set<String> usedPetIds, Set<String> usedWarFlagIds, Catalog catalog) {
        List<Hero> heroes = new ArrayList<>();
        for (String heroId : JsonSupport.getStringList(obj, "heroIds")) {
            catalog.heroes().findById(heroId).ifPresentOrElse(heroes::add,
                    () -> Logger.log("Unknown hero id in guild file, skipping: " + heroId));
        }

        Pet pet = null;
        String petId = JsonSupport.getStringOrNull(obj, "petId");
        if (petId != null) {
            pet = catalog.pets().findById(petId).orElse(null);
            if (pet == null) {
                Logger.log("Unknown pet id in guild file, skipping: " + petId);
            } else if (!usedPetIds.add(petId)) {
                Logger.log("Pet '" + petId + "' used in more than one hero team of member '" + memberId
                        + "' in guild file, dropping it from team " + (index + 1));
                pet = null;
            }
        }

        WarFlag warFlag = null;
        String warFlagId = JsonSupport.getStringOrNull(obj, "warFlagId");
        if (warFlagId != null) {
            warFlag = catalog.warFlags().findById(warFlagId).orElse(null);
            if (warFlag == null) {
                Logger.log("Unknown war flag id in guild file, skipping: " + warFlagId);
            } else if (!usedWarFlagIds.add(warFlagId)) {
                Logger.log("War flag '" + warFlagId + "' used in more than one hero team of member '" + memberId
                        + "' in guild file, dropping it from team " + (index + 1));
                warFlag = null;
            }
        }

        int totalPower = JsonSupport.getInt(obj, "totalPower", 0);
        LocalDate lastModified = JsonSupport.getLocalDate(obj, "lastModified");

        return new HeroTeam(memberId, index, heroes, pet, warFlag, totalPower, lastModified);
    }

    private static JsonObject heroTeamToTree(HeroTeam team) {
        JsonObject obj = new JsonObject();

        List<String> heroIds = new ArrayList<>();
        for (Hero hero : team.heroes()) {
            heroIds.add(hero.id());
        }
        obj.add("heroIds", JsonSupport.toStringArray(heroIds));
        JsonSupport.putNullable(obj, "petId", team.pet() == null ? null : team.pet().id());
        JsonSupport.putNullable(obj, "warFlagId", team.warFlag() == null ? null : team.warFlag().id());

        obj.addProperty("totalPower", team.totalPower());
        JsonSupport.putNullable(obj, "lastModified", team.lastModified() == null ? null : team.lastModified().toString());

        return obj;
    }

    /**
     * Reads one titan team. "totems" is optional - missing (guild files
     * saved before totems existed, or a team without totems) means none.
     * Never fails on it: the titans are read first, then the totems are
     * cleaned up against them - an unknown element name (e.g. the removed
     * "WIND"), a duplicate, a totem without enough titans of its element
     * (e.g. because a titan is no longer in the catalog) and every valid
     * totem beyond {@link TitanTeam#MAX_TOTEMS} are logged and dropped (see
     * {@link TitanTeam#validTotems}).
     */
    private static TitanTeam titanTeamFromJson(JsonObject obj, String memberId, int index, Catalog catalog) {
        List<Titan> titans = new ArrayList<>();
        for (String titanId : JsonSupport.getStringList(obj, "titanIds")) {
            catalog.titans().findById(titanId).ifPresentOrElse(titans::add,
                    () -> Logger.log("Unknown titan id in guild file, skipping: " + titanId));
        }

        List<TitanElement> requestedTotems = new ArrayList<>();
        for (String totemName : JsonSupport.getStringList(obj, "totems")) {
            try {
                requestedTotems.add(TitanElement.valueOf(totemName));
            } catch (IllegalArgumentException e) {
                Logger.log("Unknown totem in guild file, skipping: " + totemName);
            }
        }
        Set<TitanElement> totems = TitanTeam.validTotems(requestedTotems, titans,
                message -> Logger.log(message + " (guild file, member '" + memberId + "', titan team " + (index + 1) + ")"));

        int totalPower = JsonSupport.getInt(obj, "totalPower", 0);
        LocalDate lastModified = JsonSupport.getLocalDate(obj, "lastModified");

        return new TitanTeam(memberId, index, titans, totalPower, lastModified, totems);
    }

    private static JsonObject titanTeamToTree(TitanTeam team) {
        JsonObject obj = new JsonObject();

        List<String> titanIds = new ArrayList<>();
        for (Titan titan : team.titans()) {
            titanIds.add(titan.id());
        }
        obj.add("titanIds", JsonSupport.toStringArray(titanIds));
        // No totems = no field at all, so such teams look exactly like those of files saved before totems existed.
        if (!team.totems().isEmpty()) {
            obj.add("totems", JsonSupport.toStringArray(team.totems().stream().map(TitanElement::name).toList()));
        }

        obj.addProperty("totalPower", team.totalPower());
        JsonSupport.putNullable(obj, "lastModified", team.lastModified() == null ? null : team.lastModified().toString());

        return obj;
    }
}
