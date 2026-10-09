package org.c2w.service;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.data.repository.LineupFiles;
import org.c2w.data.repository.LineupRepository;
import org.c2w.domain.ChangePlanOutline;
import org.c2w.domain.ChangePlanOutline.Item;
import org.c2w.domain.ChangePlanOutline.Kind;
import org.c2w.domain.LineupComparisonService.TeamKey;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * "Apply to live": takes the checked entries of the change plan ({@link ChangePlanOutline}) into
 * the guild's live lineup (the file {@code Original.lineup}, shown as "Live" in the UI). The
 * only way besides the team-entry dialogs to change it.
 *
 * <p>{@link #apply} computes the new live lineup (GUI-free, writes nothing): the checked
 * entries, plus - for a checked addition that is part of a fortification change - its removal
 * at the old fortification, even if that is not checked. If a fortification would then hold more
 * teams than its capacity, nothing is applied. {@link #archive} and {@link #saveLive} write it:
 * first the old live file is copied to {@value #HISTORY_DIR}/ (a subfolder, so it appears in no
 * lineup list), then the new one is written to a temporary file that replaces the live file - an
 * error leaves the live file as it was. The guild itself is never changed.
 */
public final class LiveApplyService {

    /** Subfolder of the guild folder that keeps the archived live lineups. */
    public static final String HISTORY_DIR = "live-history";

    private static final DateTimeFormatter ARCHIVE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm");

    private LiveApplyService() {
    }

    /** What {@link #apply} came to. */
    public enum Outcome {
        /** No entry is checked - the live lineup stays as it is. */
        NOTHING_CHECKED,
        /** Some fortifications would hold too many teams - nothing is applied. */
        OVER_CAPACITY,
        /** The new live lineup was computed. */
        APPLIED
    }

    /**
     * The result of {@link #apply}.
     *
     * @param live                     for {@link Outcome#APPLIED}: the new live lineup, otherwise null
     * @param applied                  the entries applied (checked ones plus the removals added automatically)
     * @param autoRemoved              how many of them are removals added automatically (fortification change)
     * @param overCapacityFortifications for {@link Outcome#OVER_CAPACITY}: the fortifications that would be too full, in map order
     */
    public record Result(Outcome outcome, Lineup live, List<Item> applied, int autoRemoved,
                         List<String> overCapacityFortifications) {
        public Result {
            applied = List.copyOf(applied);
            overCapacityFortifications = List.copyOf(overCapacityFortifications);
        }

        /** The ids of the applied entries. */
        public Set<String> appliedIds() {
            Set<String> ids = new LinkedHashSet<>();
            applied.forEach(item -> ids.add(item.id()));
            return ids;
        }
    }

    /**
     * Computes the new live lineup from {@code live} and the entries of {@code outline} checked in
     * {@code checkedIds}; its {@code createdAt} is {@code now}. Writes nothing.
     */
    public static Result apply(Lineup live, ChangePlanOutline outline, Set<String> checkedIds, LocalDateTime now) {
        Objects.requireNonNull(live, "live");
        Map<String, Item> toApply = new LinkedHashMap<>();
        int autoRemoved = 0;
        for (Item item : outline.items()) {
            if (checkedIds.contains(item.id())) {
                toApply.put(item.id(), item);
            }
        }
        if (toApply.isEmpty()) {
            return new Result(Outcome.NOTHING_CHECKED, null, List.of(), 0, List.of());
        }
        for (Item item : List.copyOf(toApply.values())) {
            if (item.kind() == Kind.ADD && item.isMovePart() && !toApply.containsKey(item.partnerId())) {
                Item removal = outline.item(item.partnerId()).orElse(null);
                if (removal != null) {
                    toApply.put(removal.id(), removal);
                    autoRemoved++;
                }
            }
        }

        List<Lineup.Entry> entries = new ArrayList<>(live.entries());
        List<Item> applied = new ArrayList<>(toApply.values());
        for (Item item : applied) {
            if (item.kind() == Kind.REMOVE) {
                entries.removeIf(entry -> matches(entry, item.teamKey()) && entry.fortificationId().equals(item.fortificationId()));
            }
        }
        Set<String> filled = new LinkedHashSet<>();
        for (Item item : applied) {
            if (item.kind() == Kind.ADD) {
                // A team can only sit on one fortification - never add it twice.
                entries.removeIf(entry -> matches(entry, item.teamKey()));
                TeamKey key = item.teamKey();
                entries.add(new Lineup.Entry(item.fortificationId(), key.teamMemberId(), key.teamType(), key.teamIndex()));
                filled.add(item.fortificationId());
            }
        }

        List<String> overCapacity = filled.stream()
                .filter(fortificationId -> {
                    int capacity = FortificationRepository.findById(fortificationId).map(Fortification::capacity)
                            .orElse(Integer.MAX_VALUE);
                    return entries.stream().filter(entry -> entry.fortificationId().equals(fortificationId)).count() > capacity;
                })
                .sorted(Comparator
                        .comparing((String id) -> FortificationRepository.findById(id).map(Fortification::row).orElse(Integer.MAX_VALUE))
                        .thenComparing(id -> FortificationRepository.findById(id).map(Fortification::column).orElse(Integer.MAX_VALUE)))
                .toList();
        if (!overCapacity.isEmpty()) {
            return new Result(Outcome.OVER_CAPACITY, null, applied, autoRemoved, overCapacity);
        }
        Lineup newLive = new Lineup(live.guildId(), live.guildName(), live.algorithmName(), now, entries);
        return new Result(Outcome.APPLIED, newLive, applied, autoRemoved, List.of());
    }

    private static boolean matches(Lineup.Entry entry, TeamKey key) {
        return entry.teamMemberId().equals(key.teamMemberId()) && entry.teamType() == key.teamType()
                && entry.teamIndex() == key.teamIndex();
    }

    /**
     * Copies the live file of the guild in {@code guildDir} to
     * {@code live-history/Live_{yyyy-MM-dd_HHmm}.lineup} ({@code _2}, {@code _3} … if that exists).
     *
     * @return the archive file
     */
    public static Path archive(Path guildDir, LocalDateTime now) throws IOException {
        Path liveFile = LineupFiles.originalPathFor(guildDir);
        Path historyDir = guildDir.resolve(HISTORY_DIR);
        Files.createDirectories(historyDir);
        String base = "Live_" + ARCHIVE_STAMP.format(now);
        Path target = historyDir.resolve(base + LineupFiles.SUFFIX);
        for (int suffix = 2; Files.exists(target); suffix++) {
            target = historyDir.resolve(base + "_" + suffix + LineupFiles.SUFFIX);
        }
        Files.copy(liveFile, target);
        return target;
    }

    /**
     * Writes {@code newLive} as the live file of the guild in {@code guildDir}: into a temporary
     * file first, which then replaces the live file - on an error the live file is unchanged.
     */
    public static void saveLive(Path guildDir, Lineup newLive) throws IOException {
        Path liveFile = LineupFiles.originalPathFor(guildDir);
        Path temporary = liveFile.resolveSibling(liveFile.getFileName() + ".tmp");
        try {
            LineupRepository.save(newLive, temporary);
            try {
                Files.move(temporary, liveFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, liveFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            if (Files.isRegularFile(temporary)) {
                try {
                    Files.delete(temporary);
                } catch (IOException cleanup) {
                    e.addSuppressed(cleanup);
                }
            }
            throw e;
        }
    }
}
