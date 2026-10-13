package org.c2w.domain;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.HeroTeam;
import org.c2w.data.model.Lineup;
import org.c2w.data.model.TitanTeam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GUI-free helpers for assigning teams to one fortification by hand (see
 * {@code org.c2w.gui.fort.FortificationAssignmentDialog}): the CowScore of a team for a
 * fortification and the teams that can be assigned to it.
 */
public final class FortificationAssignment {

    private FortificationAssignment() {
    }

    /**
     * A team that can be assigned to a fortification.
     *
     * @param currentFortificationId the fortification the team is assigned to now, null if it is free
     * @param cowScore               the team's CowScore for the fortification it is offered for
     */
    public record Candidate(String memberId, Lineup.TeamType teamType, int teamIndex, String memberName,
                            int power, double cowScore, String currentFortificationId) {

        /** True if the team has no assignment yet. */
        public boolean isFree() {
            return currentFortificationId == null;
        }

        /** The entry this team would get in {@code fortificationId}. */
        public Lineup.Entry entryIn(String fortificationId) {
            return new Lineup.Entry(fortificationId, memberId, teamType, teamIndex);
        }
    }

    /** The team type that defends a fortification of {@code type}. */
    public static Lineup.TeamType teamTypeOf(FortificationType type) {
        return type == FortificationType.HERO ? Lineup.TeamType.HERO : Lineup.TeamType.TITAN;
    }

    /**
     * The CowScore ({@link TeamScoreCalculator#scoreFor}) of the team {@code entry} points at,
     * for {@code fortification} - 0 if the team no longer exists in {@code guild}.
     */
    public static double cowScoreOf(Lineup.Entry entry, Guild guild, Fortification fortification) {
        if (entry.teamType() == Lineup.TeamType.HERO) {
            return BuffCalculationService.resolveHeroTeam(entry, guild)
                    .map(team -> TeamScoreCalculator.scoreFor(team, fortification).total()).orElse(0.0);
        }
        return BuffCalculationService.resolveTitanTeam(entry, guild)
                .map(team -> TeamScoreCalculator.scoreFor(team, fortification).total()).orElse(0.0);
    }

    /**
     * The teams of {@code fortification}'s type that can be assigned to it: first the free
     * ones (no entry in {@code lineup}), then those assigned to another fortification - each
     * part sorted by CowScore for {@code fortification}, highest first. Teams already in
     * {@code fortification} are left out.
     */
    public static List<Candidate> candidatesFor(Fortification fortification, Lineup lineup, Guild guild) {
        Lineup.TeamType teamType = teamTypeOf(fortification.type());
        Map<String, String> fortificationByTeam = new HashMap<>();
        for (Lineup.Entry entry : lineup.entries()) {
            if (entry.teamType() == teamType) {
                fortificationByTeam.put(teamKey(entry.teamMemberId(), entry.teamIndex()), entry.fortificationId());
            }
        }
        List<Candidate> free = new ArrayList<>();
        List<Candidate> assigned = new ArrayList<>();
        for (GuildMember member : guild.members()) {
            int teamCount = teamType == Lineup.TeamType.HERO ? member.heroTeams().size() : member.titanTeams().size();
            for (int index = 0; index < teamCount; index++) {
                String current = fortificationByTeam.get(teamKey(member.id(), index));
                if (fortification.id().equals(current)) {
                    continue;
                }
                Candidate candidate = candidate(member, teamType, index, fortification, current);
                (current == null ? free : assigned).add(candidate);
            }
        }
        Comparator<Candidate> byCowScore = Comparator.comparingDouble(Candidate::cowScore).reversed()
                .thenComparing(Candidate::memberName, String.CASE_INSENSITIVE_ORDER);
        free.sort(byCowScore);
        assigned.sort(byCowScore);
        List<Candidate> result = new ArrayList<>(free);
        result.addAll(assigned);
        return result;
    }

    private static Candidate candidate(GuildMember member, Lineup.TeamType teamType, int index,
                                       Fortification fortification, String currentFortificationId) {
        int power;
        double cowScore;
        if (teamType == Lineup.TeamType.HERO) {
            HeroTeam team = member.heroTeams().get(index);
            power = team.totalPower();
            cowScore = TeamScoreCalculator.scoreFor(team, fortification).total();
        } else {
            TitanTeam team = member.titanTeams().get(index);
            power = team.totalPower();
            cowScore = TeamScoreCalculator.scoreFor(team, fortification).total();
        }
        return new Candidate(member.id(), teamType, index, member.displayName(), power, cowScore,
                currentFortificationId);
    }

    private static String teamKey(String memberId, int teamIndex) {
        return memberId + "#" + teamIndex;
    }
}
