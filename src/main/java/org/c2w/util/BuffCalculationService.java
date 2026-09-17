package org.c2w.util;

import org.c2w.data.model.*;
import org.c2w.data.repository.FortificationRepository;

public class BuffCalculationService {

    private BuffCalculationService() {
        // Utility class, no instantiation
    }

    /**
     * Calculates the actual buff value of a fortification based on the lineup.
     *
     * @param fortificationId The ID of the fortification
     * @param lineup The lineup with team assignments
     * @param guild The guild with the team data (Heroes/Titans and their roles/elements)
     * @param fortification The fortification with its buffs
     * @return The summed buff percentage value (e.g., 24 for 24%)
     */
    public static int calculateBuffForFortification(
            String fortificationId,
            Lineup lineup,
            Guild guild,
            Fortification fortification) {

        Buff buff = fortification.buff();
        if (buff == null) {
            return 0;
        }

        int buffCountForThisFortification = countMatchingMembersForFortification(fortificationId, lineup, guild, buff);

        // Add buff: number of matching Heroes/Titans × bonusPercent
        return (int) (buffCountForThisFortification * buff.bonusPercent());
    }

    public static int countMatchingMembersForFortification(String fortificationId, Lineup lineup, Guild guild, Buff buff) {
        if (buff == null) {
            return 0;
        }

        int count = 0;

        // For each entry in the lineup that belongs to this fortification
        for (Lineup.Entry entry : lineup.entries()) {
            if (entry.fortificationId().equals(fortificationId)) {
                // Find the guild member
                GuildMember member = findMemberById(guild, entry.teamMemberId());
                if (member == null) {
                    continue;
                }

                // Find the specific team (HERO or TITAN)
                if (entry.teamType() == Lineup.TeamType.HERO) {
                    if (entry.teamIndex() < member.heroTeams().size()) {
                        HeroTeam team = member.heroTeams().get(entry.teamIndex());
                        count += countHeroesWithRole(team, buff);
                    }
                } else if (entry.teamType() == Lineup.TeamType.TITAN) {
                    if (entry.teamIndex() < member.titanTeams().size()) {
                        TitanTeam team = member.titanTeams().get(entry.teamIndex());
                        count += countTitansWithElement(team, buff);
                    }
                }
            }
        }

        return count;
    }

    private static int countHeroesWithRole(HeroTeam team, Buff buff) {
        if (!(buff instanceof RoleBuff roleBuff)) {
            return 0;
        }

        int count = 0;
        for (Hero hero : team.heroes()) {
            if (hero.roles().contains(roleBuff.role())) {
                count++;
            }
        }
        return count;
    }

    private static int countTitansWithElement(TitanTeam team, Buff buff) {
        if (!(buff instanceof ElementBuff elementBuff)) {
            return 0;
        }

        int count = 0;
        for (Titan titan : team.titans()) {
            if (titan.element() == elementBuff.element()) {
                count++;
            }
        }
        return count;
    }

    public static int countHeroesIncreasingBuff(Lineup lineup, Guild guild) {
        int count = 0;
        for (Lineup.Entry entry : lineup.entries()) {
            if (entry.teamType() != Lineup.TeamType.HERO) {
                continue;
            }
            Fortification fortification = FortificationRepository.findById(entry.fortificationId()).orElse(null);
            if (fortification == null) {
                continue;
            }
            GuildMember member = findMemberById(guild, entry.teamMemberId());
            if (member == null || entry.teamIndex() >= member.heroTeams().size()) {
                continue;
            }
            HeroTeam team = member.heroTeams().get(entry.teamIndex());
            count += countHeroesWithRole(team, fortification.buff());
        }
        return count;
    }

    /**
     * Counts, across the WHOLE lineup, how many titans currently sit in a
     * titan team that is assigned to a fortification whose buff they help
     * increase (see {@link #countTitansWithElement}) - the titan-side
     * counterpart of {@link #countHeroesIncreasingBuff}, same matching
     * {@link #calculateBuffForFortification} does per fortification, just
     * summed over every TITAN-type {@link Lineup.Entry} in the lineup
     * instead of filtered down to one fortification. Used by
     * {@code org.tdi.cow2.gui.LineupSummaryPanel} (added 2026-09-03).
     *
     * @param lineup The lineup with team assignments
     * @param guild The guild with the team data (Titans and their elements)
     * @return The number of titans currently increasing some fortification's buff
     */
    public static int countTitansIncreasingBuff(Lineup lineup, Guild guild) {
        int count = 0;
        for (Lineup.Entry entry : lineup.entries()) {
            if (entry.teamType() != Lineup.TeamType.TITAN) {
                continue;
            }
            Fortification fortification = FortificationRepository.findById(entry.fortificationId()).orElse(null);
            if (fortification == null) {
                continue;
            }
            GuildMember member = findMemberById(guild, entry.teamMemberId());
            if (member == null || entry.teamIndex() >= member.titanTeams().size()) {
                continue;
            }
            TitanTeam team = member.titanTeams().get(entry.teamIndex());
            count += countTitansWithElement(team, fortification.buff());
        }
        return count;
    }

    /**
     * Sums, across the WHOLE lineup, the CowScore strength of every hero
     * team currently placed on a fortification - the CowScore-side
     * counterpart of {@link #countHeroesIncreasingBuff} (which counts
     * role/element MATCHES, not CowScore). Per team this is {@link
     * TeamScoreCalculator}'s per-member score sum (buff-fit-score-based for
     * a buffed fortification, general-score-based otherwise) -
     * deliberately WITHOUT {@link TeamScoreCalculator}'s totalPower term,
     * since {@code LineupSummaryPanel} already shows the hero power total
     * separately. Used by {@code org.c2w.gui.fort.LineupSummaryPanel}
     * (replacing its former use of {@link #countHeroesIncreasingBuff},
     * 2026-09-15).
     *
     * @param lineup The lineup with team assignments
     * @param guild The guild with the team data (Heroes and their CowScores)
     * @return The summed CowScore strength of every hero team in the lineup
     */
    public static double sumHeroCowScore(Lineup lineup, Guild guild) {
        double total = 0;
        for (Lineup.Entry entry : lineup.entries()) {
            if (entry.teamType() != Lineup.TeamType.HERO) {
                continue;
            }
            Fortification fortification = FortificationRepository.findById(entry.fortificationId()).orElse(null);
            if (fortification == null) {
                continue;
            }
            GuildMember member = findMemberById(guild, entry.teamMemberId());
            if (member == null || entry.teamIndex() >= member.heroTeams().size()) {
                continue;
            }
            HeroTeam team = member.heroTeams().get(entry.teamIndex());
            total += TeamScoreCalculator.scoreFor(team, fortification).memberScores().stream()
                    .mapToDouble(Double::doubleValue).sum();
        }
        return total;
    }

    /**
     * The TITAN-side counterpart of {@link #sumHeroCowScore} - identical
     * reasoning, {@link TitanTeam}/{@link
     * TeamScoreCalculator#scoreFor(TitanTeam, Fortification)} instead of
     * the hero side.
     *
     * @param lineup The lineup with team assignments
     * @param guild The guild with the team data (Titans and their CowScores)
     * @return The summed CowScore strength of every titan team in the lineup
     */
    public static double sumTitanCowScore(Lineup lineup, Guild guild) {
        double total = 0;
        for (Lineup.Entry entry : lineup.entries()) {
            if (entry.teamType() != Lineup.TeamType.TITAN) {
                continue;
            }
            Fortification fortification = FortificationRepository.findById(entry.fortificationId()).orElse(null);
            if (fortification == null) {
                continue;
            }
            GuildMember member = findMemberById(guild, entry.teamMemberId());
            if (member == null || entry.teamIndex() >= member.titanTeams().size()) {
                continue;
            }
            TitanTeam team = member.titanTeams().get(entry.teamIndex());
            total += TeamScoreCalculator.scoreFor(team, fortification).memberScores().stream()
                    .mapToDouble(Double::doubleValue).sum();
        }
        return total;
    }

    /**
     * Sums the CowScore strength of every hero/titan team currently assigned
     * to ONE specific fortification (as opposed to {@link #sumHeroCowScore}/
     * {@link #sumTitanCowScore}, which sum over the whole lineup). Used by
     * {@code org.c2w.util.ReportGenerator}'s "Fortifications" table
     * (2026-09-15).
     *
     * @param fortificationId The ID of the fortification
     * @param lineup The lineup with team assignments
     * @param guild The guild with the team data (Heroes/Titans and their CowScores)
     * @param fortification The fortification the CowScore should be resolved against
     * @return The summed CowScore strength of every team assigned to this fortification
     */
    public static double sumCowScoreForFortification(
            String fortificationId,
            Lineup lineup,
            Guild guild,
            Fortification fortification) {
        double total = 0;
        for (Lineup.Entry entry : lineup.entries()) {
            if (!entry.fortificationId().equals(fortificationId)) {
                continue;
            }
            GuildMember member = findMemberById(guild, entry.teamMemberId());
            if (member == null) {
                continue;
            }
            if (entry.teamType() == Lineup.TeamType.HERO) {
                if (entry.teamIndex() >= member.heroTeams().size()) {
                    continue;
                }
                HeroTeam team = member.heroTeams().get(entry.teamIndex());
                total += TeamScoreCalculator.scoreFor(team, fortification).memberScores().stream()
                        .mapToDouble(Double::doubleValue).sum();
            } else {
                if (entry.teamIndex() >= member.titanTeams().size()) {
                    continue;
                }
                TitanTeam team = member.titanTeams().get(entry.teamIndex());
                total += TeamScoreCalculator.scoreFor(team, fortification).memberScores().stream()
                        .mapToDouble(Double::doubleValue).sum();
            }
        }
        return total;
    }

    /**
     * Finds a guild member by their ID.
     */
    private static GuildMember findMemberById(Guild guild, String memberId) {
        return guild.members().stream()
                .filter(m -> m.id().equals(memberId))
                .findFirst()
                .orElse(null);
    }
}
