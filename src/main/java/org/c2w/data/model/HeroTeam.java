package org.c2w.data.model;

import org.c2w.domain.TeamScoreCalculator;

import java.time.LocalDate;
import java.util.List;

/**
 * A hero team (up to 5 heroes) fielded by a guild member. totalPower is the
 * team's total strength as shown in-game - per-hero values are not visible
 * for other players' teams and are therefore not modeled here.
 *
 * index: this team's 0-based slot/position among its member's hero teams
 * (0..{@link #MAX_TEAMS_PER_MEMBER} - 1, see {@link GuildMember}, up to 3
 * hero teams per member). Currently always equal to this team's position in
 * {@link GuildMember#heroTeams()} - added so a team can be referenced by
 * memberId + index alone (see {@link Lineup.Entry#teamIndex()}), without
 * relying on callers to separately track list position themselves.
 *
 * lastModified: date of the last change to this team VIA THE GUI - set when
 * the team is created or subsequently edited (see TeamEditorPanel/
 * MemberEditorPanel), purely informational, not a model invariant. null for
 * teams that have (still) never been created/edited via the GUI (e.g.
 * programmatically created teams like in the Main demo, or legacy data from
 * a guild file without this field).
 *
 * pet: this team's optional {@link Pet}, null if the team fields none.
 * warFlag: this team's optional {@link WarFlag}, null if the team fields
 * none. Both only exist for hero teams (titan teams have neither). Per
 * Clash of Worlds rules, a member can field each pet and each war flag in
 * at most ONE of their hero teams - enforced by {@link GuildMember}, not
 * here, since a single team cannot see its siblings. The pet's strength is
 * already part of {@link #totalPower()}, the war flag's is not - see
 * {@link TeamScoreCalculator} for how each adds a bonus to the team's
 * CowScore.
 */
public record HeroTeam(
        String memberId,
        int index,
        List<Hero> heroes,
        Pet pet,
        WarFlag warFlag,
        int totalPower,
        LocalDate lastModified
) {
    /** Base weight per hero whose role matches the RoleBuff's role. */
    public static final int ROLE_MATCH_WEIGHT = 1;

    /** Per Clash of Worlds rules, at most 3 hero teams per member (see {@link GuildMember}) - so {@link #index} must be 0, 1 or 2. */
    public static final int MAX_TEAMS_PER_MEMBER = 3;

    public HeroTeam {
        if (totalPower < 0) {
            throw new IllegalArgumentException("totalPower must not be negative");
        }
        if (index < 0 || index >= MAX_TEAMS_PER_MEMBER) {
            throw new IllegalArgumentException(
                    "index must be between 0 and " + (MAX_TEAMS_PER_MEMBER - 1) + ", was: " + index);
        }
        if(heroes != null) {
            heroes = List.copyOf(heroes);
        }
    }

    /** Convenience constructor for hero teams without pet and war flag. */
    public HeroTeam(String memberId, int index, List<Hero> heroes, int totalPower, LocalDate lastModified) {
        this(memberId, index, heroes, null, null, totalPower, lastModified);
    }

    /** Convenience constructor for hero teams without pet, war flag and lastModified. */
    public HeroTeam(String memberId, int index, List<Hero> heroes, int totalPower) {
        this(memberId, index, heroes, null, null, totalPower, null);
    }

    /** Convenience constructor for an empty hero team at slot 0, without pet, war flag and lastModified. */
    public HeroTeam() {
        this(null, 0, null, null, null, 0, null);
    }

    /**
     * Second comparison value besides totalPower: how much the given
     * {@link RoleBuff} helps this team. Every hero WITH the required role
     * contributes {@link #ROLE_MATCH_WEIGHT} points. Heroes WITHOUT the
     * required role do NOT contribute to the score (see {@link RoleBuff}:
     * "+bonusPercent% ... per hero WITH matching role").
     */
    public int buffFitScore(Buff buff) {
        if (!(buff instanceof RoleBuff roleBuff)) {
            throw new IllegalArgumentException("HeroTeam.buffFitScore expects a RoleBuff, was: " + buff);
        }
        int score = 0;
        for (Hero h : heroes) {
            if (h.roles().contains(roleBuff.role())) {
                score += ROLE_MATCH_WEIGHT;
            }
        }
        return score;
    }

    /**
     * Used instead of {@link #buffFitScore(Buff)} to pick a team for a
     * fortification that has NO buff, and as the general "how good is this
     * team" measure independent of any specific fortification: see
     * {@link TeamScoreCalculator#sortScore(HeroTeam)} (power / 100 000 x
     * (1 + fortification-independent bonus)).
     */
    public double sortScore() {
        return TeamScoreCalculator.sortScore(this);
    }
}
