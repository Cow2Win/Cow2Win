package org.c2w.domain;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.domain.LineupChangePlanService.ChangeStep;
import org.c2w.domain.LineupComparisonService.TeamKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The change plan "live → target" as a checklist: the {@link ChangeStep}s become entries
 * ({@link Item}) to remove or to add, grouped by fortification type (heroes before titans) →
 * kind (remove before add) → fortification (in map order: row, then column) → teams (by member
 * name, then team number). Only fortifications with entries appear; a type without changes is
 * left out. A team that changes its fortification ({@code MOVE}) appears twice: as a removal at
 * its old and as an addition at its new fortification - the two entries know each other
 * ({@link Item#partnerId()}). GUI-free and immutable.
 */
public final class ChangePlanOutline {

    /** Whether an entry takes a team off a fortification or puts it there. */
    public enum Kind {
        REMOVE, ADD
    }

    /**
     * One entry of the checklist.
     *
     * @param id                    stable id built from kind, team and fortification, e.g. {@code "REMOVE|HERO|m1|0|citadel"}
     * @param fortificationId       the fortification the team is taken off ({@link Kind#REMOVE}) or put on ({@link Kind#ADD})
     * @param partnerId             for a fortification change: the id of the other half (the addition
     *                              of a removal and vice versa), otherwise null
     * @param otherFortificationId  for a fortification change: the new (removal) or old (addition) fortification, otherwise null
     */
    public record Item(String id, Kind kind, String fortificationId, TeamKey teamKey, String partnerId,
                       String otherFortificationId) {

        /** True if the entry is one half of a fortification change. */
        public boolean isMovePart() {
            return partnerId != null;
        }
    }

    /** The entries of one fortification within a section. */
    public record FortificationGroup(String fortificationId, List<Item> items) {
        public FortificationGroup {
            items = List.copyOf(items);
        }
    }

    /** "Remove" or "add" of one fortification type, with its fortifications. */
    public record Section(Kind kind, List<FortificationGroup> fortifications) {
        public Section {
            fortifications = List.copyOf(fortifications);
        }

        public int itemCount() {
            return fortifications.stream().mapToInt(group -> group.items().size()).sum();
        }
    }

    /** One fortification type with its sections (removals before additions; empty ones left out). */
    public record TypeGroup(FortificationType type, List<Section> sections) {
        public TypeGroup {
            sections = List.copyOf(sections);
        }
    }

    private final List<TypeGroup> types;
    private final List<Item> items;
    private final Map<String, Item> itemsById;

    private ChangePlanOutline(List<TypeGroup> types) {
        this.types = List.copyOf(types);
        List<Item> all = new ArrayList<>();
        types.forEach(type -> type.sections().forEach(section ->
                section.fortifications().forEach(group -> all.addAll(group.items()))));
        this.items = List.copyOf(all);
        Map<String, Item> byId = new LinkedHashMap<>();
        all.forEach(item -> byId.put(item.id(), item));
        this.itemsById = byId;
    }

    /** The outline of {@code steps}; member names (for the order) come from {@code guild}. */
    public static ChangePlanOutline of(List<ChangeStep> steps, Guild guild) {
        List<Item> items = new ArrayList<>();
        for (ChangeStep step : steps) {
            TeamKey key = step.teamKey();
            switch (step.type()) {
                case REMOVE -> items.add(new Item(id(Kind.REMOVE, key, step.fromFortificationId()), Kind.REMOVE,
                        step.fromFortificationId(), key, null, null));
                case PLACE -> items.add(new Item(id(Kind.ADD, key, step.toFortificationId()), Kind.ADD,
                        step.toFortificationId(), key, null, null));
                case MOVE -> {
                    String removeId = id(Kind.REMOVE, key, step.fromFortificationId());
                    String addId = id(Kind.ADD, key, step.toFortificationId());
                    items.add(new Item(removeId, Kind.REMOVE, step.fromFortificationId(), key, addId,
                            step.toFortificationId()));
                    items.add(new Item(addId, Kind.ADD, step.toFortificationId(), key, removeId,
                            step.fromFortificationId()));
                }
            }
        }

        Comparator<Item> byTeam = Comparator
                .comparing((Item item) -> memberName(item.teamKey().teamMemberId(), guild), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(item -> item.teamKey().teamMemberId())
                .thenComparingInt(item -> item.teamKey().teamIndex());
        List<TypeGroup> types = new ArrayList<>();
        for (FortificationType type : FortificationType.values()) {
            List<Section> sections = new ArrayList<>();
            for (Kind kind : Kind.values()) {
                List<String> fortificationIds = items.stream()
                        .filter(item -> typeOf(item) == type && item.kind() == kind)
                        .map(Item::fortificationId)
                        .distinct()
                        .sorted(MAP_ORDER)
                        .toList();
                List<FortificationGroup> groups = new ArrayList<>();
                for (String fortificationId : fortificationIds) {
                    List<Item> groupItems = items.stream()
                            .filter(item -> typeOf(item) == type && item.kind() == kind
                                    && item.fortificationId().equals(fortificationId))
                            .sorted(byTeam)
                            .toList();
                    groups.add(new FortificationGroup(fortificationId, groupItems));
                }
                if (!groups.isEmpty()) {
                    sections.add(new Section(kind, groups));
                }
            }
            if (!sections.isEmpty()) {
                types.add(new TypeGroup(type, sections));
            }
        }
        return new ChangePlanOutline(types);
    }

    /** The stable id of an entry, e.g. {@code "ADD|TITAN|m7|1|bastion-of-fire"}. */
    static String id(Kind kind, TeamKey key, String fortificationId) {
        return kind + "|" + key.teamType() + "|" + key.teamMemberId() + "|" + key.teamIndex() + "|" + fortificationId;
    }

    /** Fortifications in map order: row, then column; unknown ids last, by id. */
    private static final Comparator<String> MAP_ORDER = Comparator
            .comparing((String id) -> FortificationRepository.findById(id).map(Fortification::row).orElse(Integer.MAX_VALUE))
            .thenComparing(id -> FortificationRepository.findById(id).map(Fortification::column).orElse(Integer.MAX_VALUE))
            .thenComparing(Comparator.naturalOrder());

    private static FortificationType typeOf(Item item) {
        return item.teamKey().teamType() == Lineup.TeamType.TITAN ? FortificationType.TITAN : FortificationType.HERO;
    }

    private static String memberName(String memberId, Guild guild) {
        if (guild == null || guild.members() == null) {
            return memberId;
        }
        return guild.members().stream()
                .filter(member -> member.id().equals(memberId))
                .findFirst()
                .map(GuildMember::name)
                .orElse(memberId);
    }

    /** The fortification types with changes, heroes first. */
    public List<TypeGroup> types() {
        return types;
    }

    /** Every entry, in outline order. */
    public List<Item> items() {
        return items;
    }

    /** The entry with {@code id}, empty if the outline has none. */
    public Optional<Item> item(String id) {
        return Optional.ofNullable(itemsById.get(id));
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
