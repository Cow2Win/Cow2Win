package org.c2w.data.journal.db;

import org.c2w.data.journal.*;
import org.c2w.data.journal.parse.BattleLogParser;
import org.c2w.data.model.BuffEffect;
import org.c2w.data.model.HeroColor;
import org.c2w.data.model.TitanElement;
import org.c2w.i18n.GameNameNormalizer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * All reads and writes of one guild's Weltenschlacht journal (see
 * {@link JournalDatabase}). Every writing method runs in exactly one
 * transaction. Pure JDBC.
 *
 * <p>A battle is identified by its day and the opponent's game guild id. It
 * has at most one attack and one defense log; saving a later export of the
 * same direction replaces the earlier one completely (exports are
 * append-only), saving the identical file again changes nothing. The original
 * CSV is kept (gzip) with its SHA-256, so logs can be parsed again after a
 * parser improvement.
 *
 * <p>Raw texts (player, fortification and unit names, buff and color texts)
 * are stored unchanged next to the resolved ids; {@link #loadLog} rebuilds the
 * exact phase-1 records.
 */
public final class JournalRepository {

    private static final String SIDE_ATTACKER = "ATTACKER";
    private static final String SIDE_DEFENDER = "DEFENDER";

    private final JournalDatabase db;

    public JournalRepository(JournalDatabase db) {
        if (db == null) {
            throw new IllegalArgumentException("JournalRepository needs a database");
        }
        this.db = db;
    }

    /** The underlying database. */
    public JournalDatabase database() {
        return db;
    }

    /** Work combining several repository calls, see {@link #inTransaction}. */
    @FunctionalInterface
    public interface RepositoryWork<T> {
        T run(JournalRepository repository) throws JournalException;
    }

    /**
     * Runs several repository calls atomically: every writing method called from
     * {@code work} joins this one transaction instead of committing on its own; on
     * any exception everything is rolled back.
     */
    public <T> T inTransaction(RepositoryWork<T> work) throws JournalException {
        return db.transaction(c -> work.run(this));
    }

    /** Game guild id of the journal's own guild, empty while nothing was saved. */
    public Optional<Long> ownGameGuildId() throws JournalException {
        return db.read(c -> {
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT game_guild_id FROM guild WHERE is_own")) {
                return rs.next() ? Optional.of(rs.getLong(1)) : Optional.<Long>empty();
            }
        });
    }

    /** SHA-256 of the stored log of one direction, empty if there is none. */
    public Optional<String> findLogSha256(int battleId, LogDirection direction) throws JournalException {
        return db.read(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT sha256 FROM battle_log WHERE battle_id = ? AND direction = ?")) {
                ps.setInt(1, battleId);
                ps.setString(2, direction.name());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(rs.getString(1)) : Optional.<String>empty();
                }
            }
        });
    }

    // =====================================================================
    // Saving
    // =====================================================================

    /**
     * Saves a parsed battle log with its original file content.
     * <ul>
     *   <li>Own guild and opponent are found or created by game guild id (name/server
     *       updated if changed). The own guild of a journal never changes.</li>
     *   <li>The battle is found or created by (day, opponent).</li>
     *   <li>An existing log of the same direction is replaced - unless it has the same
     *       SHA-256, then nothing is written ({@link SaveResult.Outcome#UNCHANGED}).</li>
     *   <li>The battle's head data (ranking points, result, status) follow the saved log;
     *       a finished battle is not set back to running by a running export (warning).</li>
     *   <li>{@code seasonId}, if given, is assigned to the battle; otherwise an existing
     *       assignment stays.</li>
     * </ul>
     *
     * @throws IllegalArgumentException  if the file name was not recognized (no head data)
     * @throws OwnGuildMismatchException if the log was exported by another guild than the journal's
     * @throws JournalException          on database errors (e.g. an unknown season id)
     */
    public SaveResult saveLog(BattleLogParseResult parsed, byte[] rawCsv, Integer seasonId) throws JournalException {
        if (parsed == null || rawCsv == null) {
            throw new IllegalArgumentException("saveLog needs a parse result and the raw CSV");
        }
        BattleLogHeader header = parsed.log().header();
        if (!header.isComplete()) {
            throw new IllegalArgumentException("Battle log without head data (file name not recognized): "
                    + header.fileName());
        }
        String sha256 = sha256(rawCsv);
        byte[] compressed = gzip(rawCsv);
        return db.transaction(c -> save(c, parsed, rawCsv.length, compressed, sha256, seasonId));
    }

    private SaveResult save(Connection c, BattleLogParseResult parsed, int rawSize, byte[] compressed, String sha256,
                            Integer seasonId) throws SQLException, JournalException {
        BattleLog log = parsed.log();
        BattleLogHeader h = log.header();
        LocalDateTime now = now();
        List<String> warnings = new ArrayList<>();

        int ownGuildId = ownGuild(c, h.ownGuild());
        int opponentGuildId = opponentGuild(c, h.opponent());

        Integer battleId = findBattleId(c, h.date(), opponentGuildId);
        boolean battleCreated = battleId == null;
        if (battleCreated) {
            battleId = insertBattle(c, h, ownGuildId, opponentGuildId, now);
        }

        Integer existingLogId = null;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, sha256 FROM battle_log WHERE battle_id = ? AND direction = ?")) {
            ps.setInt(1, battleId);
            ps.setString(2, h.direction().name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    existingLogId = rs.getInt("id");
                    if (sha256.equals(rs.getString("sha256"))) {
                        if (seasonId != null) {
                            updateSeasonOfBattle(c, battleId, seasonId, now);
                        }
                        return new SaveResult(battleId, false, SaveResult.Outcome.UNCHANGED, warnings);
                    }
                }
            }
        }
        if (existingLogId != null) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM battle_log WHERE id = ?")) {
                ps.setInt(1, existingLogId);
                ps.executeUpdate();
            }
        }

        int logId = insertLog(c, battleId, h, rawSize, compressed, sha256, log.totalPoints(), now);
        insertProblems(c, logId, parsed.problems());
        int attackerGuildId = h.direction() == LogDirection.ATTACK ? ownGuildId : opponentGuildId;
        int defenderGuildId = h.direction() == LogDirection.ATTACK ? opponentGuildId : ownGuildId;
        insertEntries(c, logId, log.entries(), attackerGuildId, defenderGuildId);

        if (!battleCreated) {
            updateBattleHead(c, battleId, h, now, warnings);
        }
        if (seasonId != null) {
            updateSeasonOfBattle(c, battleId, seasonId, now);
        }
        if (existingLogId != null) {
            cleanup(c);
        }
        return new SaveResult(battleId, battleCreated,
                existingLogId == null ? SaveResult.Outcome.CREATED : SaveResult.Outcome.REPLACED, warnings);
    }

    private int ownGuild(Connection c, GuildRef own) throws SQLException, JournalException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, game_guild_id FROM guild WHERE is_own")) {
            if (rs.next()) {
                long stored = rs.getLong("game_guild_id");
                if (stored != own.gameGuildId()) {
                    throw new OwnGuildMismatchException(stored, own.gameGuildId());
                }
                int id = rs.getInt("id");
                updateGuildName(c, id, own);
                return id;
            }
        }
        if (findGuildId(c, own.gameGuildId()) != null) {
            throw new JournalException("The exporting guild " + own.gameGuildId()
                    + " is stored as an opponent in this journal");
        }
        return insertGuild(c, own, true);
    }

    private int opponentGuild(Connection c, GuildRef opponent) throws SQLException, JournalException {
        Integer id = findGuildId(c, opponent.gameGuildId());
        if (id == null) {
            return insertGuild(c, opponent, false);
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT is_own FROM guild WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                if (rs.getBoolean(1)) {
                    throw new JournalException("The opponent " + opponent.gameGuildId() + " is this journal's own guild");
                }
            }
        }
        updateGuildName(c, id, opponent);
        return id;
    }

    private static Integer findGuildId(Connection c, long gameGuildId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM guild WHERE game_guild_id = ?")) {
            ps.setLong(1, gameGuildId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    private static int insertGuild(Connection c, GuildRef guild, boolean own) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO guild (game_guild_id, name, server, is_own) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, guild.gameGuildId());
            ps.setString(2, guild.name());
            ps.setInt(3, guild.server());
            ps.setBoolean(4, own);
            ps.executeUpdate();
            return generatedId(ps);
        }
    }

    private static void updateGuildName(Connection c, int id, GuildRef guild) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE guild SET name = ?, server = ? WHERE id = ? AND (name <> ? OR server <> ?)")) {
            ps.setString(1, guild.name());
            ps.setInt(2, guild.server());
            ps.setInt(3, id);
            ps.setString(4, guild.name());
            ps.setInt(5, guild.server());
            ps.executeUpdate();
        }
    }

    private static Integer findBattleId(Connection c, LocalDate date, int opponentGuildId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id FROM battle WHERE battle_date = ? AND opponent_guild_id = ?")) {
            ps.setObject(1, date);
            ps.setInt(2, opponentGuildId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    private static int insertBattle(Connection c, BattleLogHeader h, int ownGuildId, int opponentGuildId,
                                     LocalDateTime now) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO battle (battle_date, own_guild_id, opponent_guild_id, ranking_points, result, status,"
                        + " created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setObject(1, h.date());
            ps.setInt(2, ownGuildId);
            ps.setInt(3, opponentGuildId);
            ps.setInt(4, h.rankingPoints());
            setEnum(ps, 5, h.result());
            ps.setString(6, statusOf(h).name());
            ps.setTimestamp(7, Timestamp.valueOf(now));
            ps.setTimestamp(8, Timestamp.valueOf(now));
            ps.executeUpdate();
            return generatedId(ps);
        }
    }

    /** RUNNING for 0 ranking points (partial export), FINISHED otherwise. */
    private static BattleStatus statusOf(BattleLogHeader h) {
        return h.rankingPoints() == 0 ? BattleStatus.RUNNING : BattleStatus.FINISHED;
    }

    private static void updateBattleHead(Connection c, int battleId, BattleLogHeader h, LocalDateTime now,
                                         List<String> warnings) throws SQLException {
        BattleStatus current;
        Integer currentPoints;
        try (PreparedStatement ps = c.prepareStatement("SELECT status, ranking_points FROM battle WHERE id = ?")) {
            ps.setInt(1, battleId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                current = BattleStatus.valueOf(rs.getString("status"));
                currentPoints = rs.getObject("ranking_points", Integer.class);
            }
        }
        BattleStatus incoming = statusOf(h);
        if (current == BattleStatus.FINISHED && incoming == BattleStatus.RUNNING) {
            warnings.add("The battle is already finished (" + currentPoints + " ranking points); the running export "
                    + h.fileName() + " replaced the " + h.direction() + " log, but the result was kept");
            touch(c, battleId, now);
            return;
        }
        if (current == BattleStatus.FINISHED && !Objects.equals(currentPoints, h.rankingPoints())) {
            warnings.add("The ranking points of the finished battle changed from " + currentPoints + " to "
                    + h.rankingPoints() + " (" + h.fileName() + ")");
        }
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE battle SET ranking_points = ?, result = ?, status = ?, updated_at = ? WHERE id = ?")) {
            ps.setInt(1, h.rankingPoints());
            setEnum(ps, 2, h.result());
            ps.setString(3, incoming.name());
            ps.setTimestamp(4, Timestamp.valueOf(now));
            ps.setInt(5, battleId);
            ps.executeUpdate();
        }
    }

    private static void touch(Connection c, int battleId, LocalDateTime now) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE battle SET updated_at = ? WHERE id = ?")) {
            ps.setTimestamp(1, Timestamp.valueOf(now));
            ps.setInt(2, battleId);
            ps.executeUpdate();
        }
    }

    private static void updateSeasonOfBattle(Connection c, int battleId, Integer seasonId, LocalDateTime now)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE battle SET season_id = ?, updated_at = ? WHERE id = ?")) {
            setInt(ps, 1, seasonId);
            ps.setTimestamp(2, Timestamp.valueOf(now));
            ps.setInt(3, battleId);
            ps.executeUpdate();
        }
    }

    private static int insertLog(Connection c, int battleId, BattleLogHeader h, int rawSize, byte[] compressed,
                                  String sha256, int pointsTotal, LocalDateTime now) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO battle_log (battle_id, direction, language, file_name, own_guild_name, own_server,"
                        + " opponent_name, opponent_server, ranking_points_in_file, result_in_file, raw_csv, raw_size,"
                        + " sha256, parser_version, imported_at, points_total)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, battleId);
            ps.setString(2, h.direction().name());
            ps.setString(3, h.language());
            ps.setString(4, h.fileName());
            ps.setString(5, h.ownGuild().name());
            ps.setInt(6, h.ownGuild().server());
            ps.setString(7, h.opponent().name());
            ps.setInt(8, h.opponent().server());
            ps.setInt(9, h.rankingPoints());
            setEnum(ps, 10, h.result());
            ps.setBytes(11, compressed);
            ps.setInt(12, rawSize);
            ps.setString(13, sha256);
            ps.setInt(14, BattleLogParser.PARSER_VERSION);
            ps.setTimestamp(15, Timestamp.valueOf(now));
            ps.setInt(16, pointsTotal);
            ps.executeUpdate();
            return generatedId(ps);
        }
    }

    private static void insertProblems(Connection c, int logId, List<ParseProblem> problems) throws SQLException {
        if (problems.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO parse_problem (battle_log_id, seq, line_number, line, reason) VALUES (?, ?, ?, ?, ?)")) {
            int seq = 0;
            for (ParseProblem p : problems) {
                ps.setInt(1, logId);
                ps.setInt(2, seq++);
                ps.setInt(3, p.lineNumber());
                ps.setString(4, p.line());
                ps.setString(5, p.reason());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void insertEntries(Connection c, int logId, List<BattleLogEntry> entries,
                                      int attackerGuildId, int defenderGuildId) throws SQLException {
        Map<String, Integer> playerIds = new HashMap<>();
        try (PreparedStatement fortEvents = c.prepareStatement(
                "INSERT INTO fort_event (battle_log_id, seq, line_number, fortification_id, fortification_name, kind,"
                        + " free_positions, total_positions, text, points) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
             PreparedStatement fights = c.prepareStatement(
                     "INSERT INTO fight (battle_log_id, seq, line_number, fortification_id, fortification_name, position,"
                             + " team_kind, attacker_wins, result_text, points, attacker_player_id, attacker_level,"
                             + " attacker_team_power, defender_player_id, defender_level, defender_team_power,"
                             + " buff_effect, buff_percent, buff_text)"
                             + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS);
             PreparedStatement units = c.prepareStatement(
                     "INSERT INTO fight_unit (fight_id, side, slot, kind, name, catalog_id, totem_element, color,"
                             + " color_text, color_level, stars, level, power, damage_dealt, damage_taken, healing,"
                             + " patronage_pet_id, patronage_pet_name, patronage_power)"
                             + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            int seq = 0;
            boolean anyFortEvent = false;
            boolean anyUnit = false;
            for (BattleLogEntry entry : entries) {
                switch (entry) {
                    case FortEvent e -> {
                        fortEvents.setInt(1, logId);
                        fortEvents.setInt(2, seq);
                        fortEvents.setInt(3, e.lineNumber());
                        fortEvents.setString(4, e.fortificationId());
                        fortEvents.setString(5, e.fortificationName());
                        fortEvents.setString(6, e.kind().name());
                        setInt(fortEvents, 7, e.freePositions());
                        setInt(fortEvents, 8, e.totalPositions());
                        fortEvents.setString(9, e.text());
                        fortEvents.setInt(10, e.points());
                        fortEvents.addBatch();
                        anyFortEvent = true;
                    }
                    case Fight f -> {
                        int attackerId = playerId(c, playerIds, attackerGuildId, f.attacker().playerName());
                        int defenderId = playerId(c, playerIds, defenderGuildId, f.defender().playerName());
                        fights.setInt(1, logId);
                        fights.setInt(2, seq);
                        fights.setInt(3, f.lineNumber());
                        fights.setString(4, f.fortificationId());
                        fights.setString(5, f.fortificationName());
                        fights.setInt(6, f.position());
                        setEnum(fights, 7, f.teamKind());
                        fights.setBoolean(8, f.attackerWins());
                        fights.setString(9, f.resultText());
                        fights.setInt(10, f.points());
                        fights.setInt(11, attackerId);
                        fights.setInt(12, f.attacker().level());
                        fights.setInt(13, f.attacker().teamPower());
                        fights.setInt(14, defenderId);
                        fights.setInt(15, f.defender().level());
                        fights.setInt(16, f.defender().teamPower());
                        DefenseBuff buff = f.defender().buff();
                        setEnum(fights, 17, buff == null ? null : buff.effect());
                        setInt(fights, 18, buff == null ? null : buff.percent());
                        fights.setString(19, buff == null ? null : buff.rawText());
                        fights.executeUpdate();
                        int fightId = generatedId(fights);
                        anyUnit |= addUnits(units, fightId, SIDE_ATTACKER, f.attacker().units());
                        anyUnit |= addUnits(units, fightId, SIDE_DEFENDER, f.defender().units());
                    }
                }
                seq++;
            }
            if (anyFortEvent) {
                fortEvents.executeBatch();
            }
            if (anyUnit) {
                units.executeBatch();
            }
        }
    }

    private static boolean addUnits(PreparedStatement ps, int fightId, String side, List<FightUnit> units)
            throws SQLException {
        int slot = 0;
        for (FightUnit u : units) {
            ps.setInt(1, fightId);
            ps.setString(2, side);
            ps.setInt(3, slot++);
            ps.setString(4, u.kind().name());
            ps.setString(5, u.name());
            ps.setString(6, u.catalogId());
            setEnum(ps, 7, u.totemElement());
            setEnum(ps, 8, u.color());
            ps.setString(9, u.colorText());
            ps.setInt(10, u.colorLevel());
            ps.setInt(11, u.stars());
            ps.setInt(12, u.level());
            setInt(ps, 13, u.power());
            ps.setInt(14, u.damageDealt());
            ps.setInt(15, u.damageTaken());
            ps.setInt(16, u.healing());
            Patronage p = u.patronage();
            ps.setString(17, p == null ? null : p.petId());
            ps.setString(18, p == null ? null : p.petName());
            setInt(ps, 19, p == null ? null : p.power());
            ps.addBatch();
        }
        return !units.isEmpty();
    }

    /** Finds or creates the player {@code name} (exact raw text) of the guild. */
    private static int playerId(Connection c, Map<String, Integer> cache, int guildId, String name) throws SQLException {
        String key = guildId + "\u0000" + name;
        Integer cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        int id;
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM player WHERE guild_id = ? AND name = ?")) {
            ps.setInt(1, guildId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                id = rs.next() ? rs.getInt(1) : -1;
            }
        }
        if (id < 0) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO player (guild_id, name) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, guildId);
                ps.setString(2, name);
                ps.executeUpdate();
                id = generatedId(ps);
            }
        }
        cache.put(key, id);
        return id;
    }

    // =====================================================================
    // Reading
    // =====================================================================

    /** The battle of {@code date} against the opponent with that game guild id. */
    public Optional<Integer> findBattle(LocalDate date, long opponentGameGuildId) throws JournalException {
        return db.read(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT b.id FROM battle b JOIN guild g ON g.id = b.opponent_guild_id"
                            + " WHERE b.battle_date = ? AND g.game_guild_id = ?")) {
                ps.setObject(1, date);
                ps.setLong(2, opponentGameGuildId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(rs.getInt(1)) : Optional.<Integer>empty();
                }
            }
        });
    }

    /**
     * Rebuilds the stored log of one direction as the phase-1 records - equal to
     * the parse result that was saved (entries in file order, raw texts, problems).
     */
    public Optional<BattleLogParseResult> loadLog(int battleId, LogDirection direction) throws JournalException {
        return db.read(c -> {
            int logId;
            BattleLogHeader header;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT l.*, b.battle_date, og.game_guild_id AS own_game_id, pg.game_guild_id AS opp_game_id"
                            + " FROM battle_log l JOIN battle b ON b.id = l.battle_id"
                            + " JOIN guild og ON og.id = b.own_guild_id JOIN guild pg ON pg.id = b.opponent_guild_id"
                            + " WHERE l.battle_id = ? AND l.direction = ?")) {
                ps.setInt(1, battleId);
                ps.setString(2, direction.name());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.<BattleLogParseResult>empty();
                    }
                    logId = rs.getInt("id");
                    header = new BattleLogHeader(
                            rs.getObject("battle_date", LocalDate.class),
                            new GuildRef(rs.getString("own_guild_name"), rs.getInt("own_server"), rs.getLong("own_game_id")),
                            new GuildRef(rs.getString("opponent_name"), rs.getInt("opponent_server"), rs.getLong("opp_game_id")),
                            rs.getInt("ranking_points_in_file"),
                            enumOrNull(BattleResult.class, rs.getString("result_in_file")),
                            direction,
                            rs.getString("language"),
                            rs.getString("file_name"));
                }
            }
            List<ParseProblem> problems = loadProblems(c, logId);
            TreeMap<Integer, BattleLogEntry> entries = new TreeMap<>();
            loadFortEvents(c, logId, entries);
            loadFights(c, logId, entries);
            return Optional.of(new BattleLogParseResult(new BattleLog(header, List.copyOf(entries.values())), problems));
        });
    }

    private static List<ParseProblem> loadProblems(Connection c, int logId) throws SQLException {
        List<ParseProblem> problems = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT line_number, line, reason FROM parse_problem WHERE battle_log_id = ? ORDER BY seq")) {
            ps.setInt(1, logId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    problems.add(new ParseProblem(rs.getInt("line_number"), rs.getString("line"), rs.getString("reason")));
                }
            }
        }
        return problems;
    }

    private static void loadFortEvents(Connection c, int logId, Map<Integer, BattleLogEntry> entries)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM fort_event WHERE battle_log_id = ? ORDER BY seq")) {
            ps.setInt(1, logId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.put(rs.getInt("seq"), new FortEvent(
                            rs.getString("fortification_id"),
                            rs.getString("fortification_name"),
                            FortEventKind.valueOf(rs.getString("kind")),
                            rs.getObject("free_positions", Integer.class),
                            rs.getObject("total_positions", Integer.class),
                            rs.getString("text"),
                            rs.getInt("points"),
                            rs.getInt("line_number")));
                }
            }
        }
    }

    private static void loadFights(Connection c, int logId, Map<Integer, BattleLogEntry> entries) throws SQLException {
        Map<Integer, List<FightUnit>> attackerUnits = new HashMap<>();
        Map<Integer, List<FightUnit>> defenderUnits = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT u.* FROM fight_unit u JOIN fight f ON f.id = u.fight_id WHERE f.battle_log_id = ?"
                        + " ORDER BY u.fight_id, u.side, u.slot")) {
            ps.setInt(1, logId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer patronagePower = rs.getObject("patronage_power", Integer.class);
                    Patronage patronage = patronagePower == null ? null
                            : new Patronage(rs.getString("patronage_pet_id"), rs.getString("patronage_pet_name"),
                            patronagePower);
                    FightUnit unit = new FightUnit(
                            UnitKind.valueOf(rs.getString("kind")),
                            rs.getString("name"),
                            rs.getString("catalog_id"),
                            enumOrNull(TitanElement.class, rs.getString("totem_element")),
                            enumOrNull(HeroColor.class, rs.getString("color")),
                            rs.getString("color_text"),
                            rs.getInt("color_level"),
                            rs.getInt("stars"),
                            rs.getInt("level"),
                            rs.getObject("power", Integer.class),
                            rs.getInt("damage_dealt"),
                            rs.getInt("damage_taken"),
                            rs.getInt("healing"),
                            patronage);
                    Map<Integer, List<FightUnit>> target =
                            SIDE_ATTACKER.equals(rs.getString("side")) ? attackerUnits : defenderUnits;
                    target.computeIfAbsent(rs.getInt("fight_id"), k -> new ArrayList<>()).add(unit);
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT f.*, pa.name AS attacker_name, pd.name AS defender_name FROM fight f"
                        + " JOIN player pa ON pa.id = f.attacker_player_id JOIN player pd ON pd.id = f.defender_player_id"
                        + " WHERE f.battle_log_id = ? ORDER BY f.seq")) {
            ps.setInt(1, logId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int fightId = rs.getInt("id");
                    String buffText = rs.getString("buff_text");
                    DefenseBuff buff = buffText == null ? null : new DefenseBuff(
                            enumOrNull(BuffEffect.class, rs.getString("buff_effect")),
                            rs.getObject("buff_percent", Integer.class),
                            buffText);
                    entries.put(rs.getInt("seq"), new Fight(
                            rs.getString("fortification_id"),
                            rs.getString("fortification_name"),
                            rs.getInt("position"),
                            enumOrNull(TeamKind.class, rs.getString("team_kind")),
                            rs.getBoolean("attacker_wins"),
                            rs.getString("result_text"),
                            rs.getInt("points"),
                            new FightSide(rs.getString("attacker_name"), rs.getInt("attacker_level"),
                                    rs.getInt("attacker_team_power"), null,
                                    attackerUnits.getOrDefault(fightId, List.of())),
                            new FightSide(rs.getString("defender_name"), rs.getInt("defender_level"),
                                    rs.getInt("defender_team_power"), buff,
                                    defenderUnits.getOrDefault(fightId, List.of())),
                            rs.getInt("line_number")));
                }
            }
        }
    }

    /** The original file content of the stored log (unpacked). */
    public Optional<byte[]> loadRawCsv(int battleId, LogDirection direction) throws JournalException {
        return db.read(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT raw_csv FROM battle_log WHERE battle_id = ? AND direction = ?")) {
                ps.setInt(1, battleId);
                ps.setString(2, direction.name());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(gunzip(rs.getBytes(1))) : Optional.<byte[]>empty();
                }
            }
        });
    }

    /** All battles, or those of one season, newest first. */
    public List<BattleSummary> listBattles(Integer seasonIdOrNull) throws JournalException {
        return db.read(c -> {
            String sql = "SELECT b.id, b.battle_date, b.result, b.status, b.ranking_points, b.season_id,"
                    + " s.season_number, g.name, g.server, g.game_guild_id,"
                    + " (SELECT points_total FROM battle_log l WHERE l.battle_id = b.id AND l.direction = 'ATTACK') AS own_points,"
                    + " (SELECT points_total FROM battle_log l WHERE l.battle_id = b.id AND l.direction = 'DEFENSE') AS opp_points"
                    + " FROM battle b JOIN guild g ON g.id = b.opponent_guild_id LEFT JOIN season s ON s.id = b.season_id"
                    + (seasonIdOrNull == null ? "" : " WHERE b.season_id = ?")
                    + " ORDER BY b.battle_date DESC, b.id DESC";
            List<BattleSummary> result = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                if (seasonIdOrNull != null) {
                    ps.setInt(1, seasonIdOrNull);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Integer own = rs.getObject("own_points", Integer.class);
                        Integer opp = rs.getObject("opp_points", Integer.class);
                        Set<LogDirection> directions = EnumSet.noneOf(LogDirection.class);
                        if (own != null) {
                            directions.add(LogDirection.ATTACK);
                        }
                        if (opp != null) {
                            directions.add(LogDirection.DEFENSE);
                        }
                        result.add(new BattleSummary(
                                rs.getInt("id"),
                                rs.getObject("battle_date", LocalDate.class),
                                new GuildRef(rs.getString("name"), rs.getInt("server"), rs.getLong("game_guild_id")),
                                enumOrNull(BattleResult.class, rs.getString("result")),
                                BattleStatus.valueOf(rs.getString("status")),
                                rs.getObject("ranking_points", Integer.class),
                                own, opp, directions,
                                rs.getObject("season_id", Integer.class),
                                rs.getObject("season_number", Integer.class)));
                    }
                }
            }
            return result;
        });
    }

    // =====================================================================
    // Seasons
    // =====================================================================

    /** Creates a season of {@link Season#DEFAULT_LENGTH} starting on {@code start}. */
    public Season createSeason(int number, LocalDate start) throws JournalException {
        return createSeason(number, start, start.plus(Season.DEFAULT_LENGTH));
    }

    /**
     * Creates a season covering {@code start <= day < end}.
     *
     * @throws JournalException if the number is taken or the season overlaps another one
     */
    public Season createSeason(int number, LocalDate start, LocalDate end) throws JournalException {
        Season candidate = new Season(0, number, start, end, null);
        return db.transaction(c -> {
            checkSeason(c, candidate);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO season (season_number, start_date, end_date) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, number);
                ps.setObject(2, start);
                ps.setObject(3, end);
                ps.executeUpdate();
                return new Season(generatedId(ps), number, start, end, null);
            }
        });
    }

    /** Changes number, dates and note of an existing season (same checks as {@link #createSeason}). */
    public Season updateSeason(Season season) throws JournalException {
        return db.transaction(c -> {
            checkSeason(c, season);
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE season SET season_number = ?, start_date = ?, end_date = ?, note = ? WHERE id = ?")) {
                ps.setInt(1, season.number());
                ps.setObject(2, season.start());
                ps.setObject(3, season.end());
                ps.setString(4, season.note());
                ps.setInt(5, season.id());
                if (ps.executeUpdate() != 1) {
                    throw new JournalException("Unknown season id " + season.id());
                }
            }
            return season;
        });
    }

    private static void checkSeason(Connection c, Season season) throws SQLException, JournalException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT season_number, start_date, end_date FROM season WHERE id <> ?"
                        + " AND (season_number = ? OR (start_date < ? AND ? < end_date))")) {
            ps.setInt(1, season.id());
            ps.setInt(2, season.number());
            ps.setObject(3, season.end());
            ps.setObject(4, season.start());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int number = rs.getInt(1);
                    throw new JournalException(number == season.number()
                            ? "Season number " + number + " already exists"
                            : "Season " + season.number() + " (" + season.start() + " - " + season.lastDay()
                            + ") overlaps season " + number + " (" + rs.getObject(2, LocalDate.class) + " - "
                            + rs.getObject(3, LocalDate.class).minusDays(1) + ")");
                }
            }
        }
    }

    /** All seasons, oldest first. */
    public List<Season> listSeasons() throws JournalException {
        return db.read(c -> {
            List<Season> seasons = new ArrayList<>();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM season ORDER BY start_date")) {
                while (rs.next()) {
                    seasons.add(season(rs));
                }
            }
            return seasons;
        });
    }

    /** The season containing {@code date}. */
    public Optional<Season> findSeasonFor(LocalDate date) throws JournalException {
        return db.read(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM season WHERE start_date <= ? AND ? < end_date")) {
                ps.setObject(1, date);
                ps.setObject(2, date);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(season(rs)) : Optional.<Season>empty();
                }
            }
        });
    }

    /** Assigns a battle to a season ({@code null} removes the assignment). */
    public void assignSeason(int battleId, Integer seasonId) throws JournalException {
        db.transaction(c -> {
            updateSeasonOfBattle(c, battleId, seasonId, now());
            return null;
        });
    }

    private static Season season(ResultSet rs) throws SQLException {
        return new Season(rs.getInt("id"), rs.getInt("season_number"), rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class), rs.getString("note"));
    }

    // =====================================================================
    // Players, assignments, name mappings
    // =====================================================================

    /** The own-guild player with exactly this raw name. */
    public Optional<JournalPlayer> findOwnPlayer(String name) throws JournalException {
        return db.read(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT p.id, p.name, g.game_guild_id, g.is_own FROM player p"
                    + " JOIN guild g ON g.id = p.guild_id WHERE g.is_own AND p.name = ?")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(player(rs)) : Optional.<JournalPlayer>empty();
                }
            }
        });
    }

    /** The assignments of all own-guild players that have one, by exact raw player name. */
    public Map<String, PlayerAssignment> ownAssignmentsByName() throws JournalException {
        return db.read(c -> {
            Map<String, PlayerAssignment> result = new HashMap<>();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT p.name, a.* FROM player_assignment a"
                         + " JOIN player p ON p.id = a.player_id JOIN guild g ON g.id = p.guild_id WHERE g.is_own")) {
                while (rs.next()) {
                    result.put(rs.getString("name"), new PlayerAssignment(rs.getInt("player_id"),
                            rs.getString("member_id"), AssignmentStatus.valueOf(rs.getString("status")),
                            rs.getObject("confirmed_at", LocalDateTime.class)));
                }
            }
            return result;
        });
    }

    /** All manual name mappings as raw name -> catalog id per kind (input for {@code NameResolver#withMappings}). */
    public Map<NameMappingKind, Map<String, String>> nameMappingsByKind() throws JournalException {
        Map<NameMappingKind, Map<String, String>> result = new EnumMap<>(NameMappingKind.class);
        for (NameMapping m : listNameMappings()) {
            result.computeIfAbsent(m.kind(), k -> new HashMap<>()).put(m.rawName(), m.catalogId());
        }
        return result;
    }

    /** All players (or only those of the own guild), own guild first, then by name. */
    public List<JournalPlayer> listPlayers(boolean ownGuildOnly) throws JournalException {
        return db.read(c -> {
            List<JournalPlayer> players = new ArrayList<>();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT p.id, p.name, g.game_guild_id, g.is_own FROM player p"
                         + " JOIN guild g ON g.id = p.guild_id" + (ownGuildOnly ? " WHERE g.is_own" : "")
                         + " ORDER BY g.is_own DESC, g.game_guild_id, p.name")) {
                while (rs.next()) {
                    players.add(player(rs));
                }
            }
            return players;
        });
    }

    /**
     * Sets the assignment of an own-guild player to a Cow2Win member.
     *
     * @param memberId Cow2Win member id ({@code GuildMember#id}); required for {@link AssignmentStatus#ASSIGNED}
     * @throws JournalException if the player is unknown or not in the own guild
     */
    public PlayerAssignment setAssignment(int playerId, String memberId, AssignmentStatus status)
            throws JournalException {
        if (status == null) {
            throw new IllegalArgumentException("setAssignment needs a status");
        }
        if (status == AssignmentStatus.ASSIGNED && (memberId == null || memberId.isBlank())) {
            throw new IllegalArgumentException("ASSIGNED needs a member id");
        }
        LocalDateTime now = now();
        return db.transaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT g.is_own FROM player p JOIN guild g ON g.id = p.guild_id WHERE p.id = ?")) {
                ps.setInt(1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new JournalException("Unknown player id " + playerId);
                    }
                    if (!rs.getBoolean(1)) {
                        throw new JournalException("Only players of the own guild can be assigned (player " + playerId + ")");
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "MERGE INTO player_assignment (player_id, member_id, status, confirmed_at) KEY (player_id)"
                            + " VALUES (?, ?, ?, ?)")) {
                ps.setInt(1, playerId);
                ps.setString(2, memberId);
                ps.setString(3, status.name());
                ps.setTimestamp(4, Timestamp.valueOf(now));
                ps.executeUpdate();
            }
            return new PlayerAssignment(playerId, memberId, status, now);
        });
    }

    /** The assignment of a player, empty if none was set. */
    public Optional<PlayerAssignment> findAssignment(int playerId) throws JournalException {
        return db.read(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM player_assignment WHERE player_id = ?")) {
                ps.setInt(1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next()
                            ? Optional.of(new PlayerAssignment(playerId, rs.getString("member_id"),
                            AssignmentStatus.valueOf(rs.getString("status")),
                            rs.getObject("confirmed_at", LocalDateTime.class)))
                            : Optional.<PlayerAssignment>empty();
                }
            }
        });
    }

    /** All players linked to a Cow2Win member - several after renames in the game. */
    public List<JournalPlayer> findPlayersByMember(String memberId) throws JournalException {
        return db.read(c -> {
            List<JournalPlayer> players = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT p.id, p.name, g.game_guild_id, g.is_own FROM player_assignment a"
                            + " JOIN player p ON p.id = a.player_id JOIN guild g ON g.id = p.guild_id"
                            + " WHERE a.member_id = ? ORDER BY p.name")) {
                ps.setString(1, memberId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        players.add(player(rs));
                    }
                }
            }
            return players;
        });
    }

    private static JournalPlayer player(ResultSet rs) throws SQLException {
        return new JournalPlayer(rs.getInt("id"), rs.getString("name"), rs.getLong("game_guild_id"),
                rs.getBoolean("is_own"));
    }

    /** Stores (or replaces) a manual mapping of {@code rawName}; looked up via {@code GameNameNormalizer}. */
    public void putNameMapping(NameMappingKind kind, String rawName, String catalogId) throws JournalException {
        if (kind == null || GameNameNormalizer.key(rawName).isEmpty() || catalogId == null || catalogId.isBlank()) {
            throw new IllegalArgumentException("putNameMapping needs a kind, a name and a catalog id");
        }
        db.transaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "MERGE INTO name_mapping (kind, name_key, raw_name, catalog_id, created_at) KEY (kind, name_key)"
                            + " VALUES (?, ?, ?, ?, ?)")) {
                ps.setString(1, kind.name());
                ps.setString(2, GameNameNormalizer.key(rawName));
                ps.setString(3, rawName);
                ps.setString(4, catalogId);
                ps.setTimestamp(5, Timestamp.valueOf(now()));
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** The manual mapping of {@code rawName} (compared via {@code GameNameNormalizer}). */
    public Optional<NameMapping> findNameMapping(NameMappingKind kind, String rawName) throws JournalException {
        return db.read(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT raw_name, catalog_id FROM name_mapping WHERE kind = ? AND name_key = ?")) {
                ps.setString(1, kind.name());
                ps.setString(2, GameNameNormalizer.key(rawName));
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(new NameMapping(kind, rs.getString(1), rs.getString(2)))
                            : Optional.<NameMapping>empty();
                }
            }
        });
    }

    /** All manual name mappings, by kind and name. */
    public List<NameMapping> listNameMappings() throws JournalException {
        return db.read(c -> {
            List<NameMapping> mappings = new ArrayList<>();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT kind, raw_name, catalog_id FROM name_mapping ORDER BY kind, name_key")) {
                while (rs.next()) {
                    mappings.add(new NameMapping(NameMappingKind.valueOf(rs.getString(1)), rs.getString(2),
                            rs.getString(3)));
                }
            }
            return mappings;
        });
    }

    // =====================================================================
    // Deleting
    // =====================================================================

    /** Deletes a battle with both logs and everything below, then cleans up. False if it did not exist. */
    public boolean deleteBattle(int battleId) throws JournalException {
        return db.transaction(c -> {
            int deleted;
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM battle WHERE id = ?")) {
                ps.setInt(1, battleId);
                deleted = ps.executeUpdate();
            }
            cleanup(c);
            return deleted == 1;
        });
    }

    /**
     * Deletes a season. With {@code includeBattles} all its battles are deleted too,
     * otherwise they stay without a season. Cleans up afterwards. False if it did not exist.
     */
    public boolean deleteSeason(int seasonId, boolean includeBattles) throws JournalException {
        return db.transaction(c -> {
            if (includeBattles) {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM battle WHERE season_id = ?")) {
                    ps.setInt(1, seasonId);
                    ps.executeUpdate();
                }
            }
            int deleted;
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM season WHERE id = ?")) {
                ps.setInt(1, seasonId);
                deleted = ps.executeUpdate();
            }
            cleanup(c);
            return deleted == 1;
        });
    }

    /**
     * Removes players no fight refers to - except own-guild players with an assignment
     * other than OPEN, so confirmed assignments survive - and opponent guilds without
     * a battle. The own guild always stays.
     */
    private static void cleanup(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM player p"
                    + " WHERE NOT EXISTS (SELECT 1 FROM fight f WHERE f.attacker_player_id = p.id)"
                    + " AND NOT EXISTS (SELECT 1 FROM fight f WHERE f.defender_player_id = p.id)"
                    + " AND NOT EXISTS (SELECT 1 FROM player_assignment a WHERE a.player_id = p.id AND a.status <> 'OPEN')");
            st.executeUpdate("DELETE FROM guild g WHERE NOT g.is_own"
                    + " AND NOT EXISTS (SELECT 1 FROM battle b WHERE b.opponent_guild_id = g.id)"
                    + " AND NOT EXISTS (SELECT 1 FROM player p WHERE p.guild_id = g.id)");
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /** The current time at the precision H2 stores (microseconds), so returned values equal stored ones. */
    private static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static int generatedId(Statement st) throws SQLException {
        try (ResultSet keys = st.getGeneratedKeys()) {
            if (!keys.next()) {
                throw new SQLException("No generated key returned");
            }
            return keys.getInt(1);
        }
    }

    private static void setInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static void setEnum(PreparedStatement ps, int index, Enum<?> value) throws SQLException {
        ps.setString(index, value == null ? null : value.name());
    }

    private static <E extends Enum<E>> E enumOrNull(Class<E> type, String name) {
        return name == null ? null : Enum.valueOf(type, name);
    }

    /** Hex SHA-256 of {@code data}, as stored with every log (identifies an identical re-export). */
    public static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static byte[] gzip(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, data.length / 4));
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(data);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static byte[] gunzip(byte[] data) {
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return gz.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Stored battle log is not valid gzip", e);
        }
    }
}
