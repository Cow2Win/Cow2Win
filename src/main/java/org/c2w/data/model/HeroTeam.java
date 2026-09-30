package org.c2w.data.model;

import org.c2w.util.TeamScoreCalculator;

import java.time.LocalDate;
import java.util.ArrayList;
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
 * here, since a single team cannot see its siblings. Both are already
 * included in {@link #totalPower()} as shown in-game. Their CowScore adds
 * to the team's score on top of the heroes', at the lower {@link
 * CowScoreTier#petWarFlagValue()} - see {@link #petWarFlagScores(Fortification)}.
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
     * fortification that has NO buff - there is no role to score against
     * there, so this falls back to a general "how good is this team"
     * measure: the sum of every hero's {@link Hero#generalScore()} (some
     * heroes are simply better than others, independent of any specific
     * buff/role) plus {@link #totalPower()} scaled down via
     * {@link TeamScoreCalculator#POWER_DIVISOR} - per the user's own formula (added
     * 2026-09-11, see cow2win-verbesserungsvorschlaege.md): generalScore +
     * power / 100 000.
     */
    public double sortScore() {
        double generalScoreSum = heroes.stream().mapToDouble(h -> h.generalScore().value()).sum();
        double petWarFlagSum = petWarFlagScores(null).stream().mapToDouble(Double::doubleValue).sum();
        return generalScoreSum + petWarFlagSum + totalPower() / TeamScoreCalculator.POWER_DIVISOR;
    }

    /**
     * The war flag's and the pet's contribution to this team's score at
     * {@code fortification}, in row order (war flag, then pet) - an absent
     * war flag/pet is simply left out, i.e. counts 0 (per the user's
     * decision, 2026-09-29). Each is its {@link CowScoreTier#petWarFlagValue()}
     * of: its {@link WarFlag#buffFitScore(String)}/{@link Pet#buffFitScore(String)}
     * if {@code fortification} has a buff, otherwise (also for a null
     * {@code fortification}, see {@link #sortScore()}) its generalScore.
     * Used on top of the heroes' own scores by {@link #sortScore()} and
     * {@code TeamScoreCalculator#scoreFor(HeroTeam, Fortification)}; never
     * part of {@link #buffFitScore(Buff)}, which only counts heroes whose
     * role matches.
     */
    public List<Double> petWarFlagScores(Fortification fortification) {
        boolean buffed = fortification != null && fortification.buff() != null;
        List<Double> scores = new ArrayList<>(2);
        if (warFlag != null) {
            CowScoreTier tier = buffed ? warFlag.buffFitScore(fortification.id()) : warFlag.generalScore();
            scores.add(tier.petWarFlagValue());
        }
        if (pet != null) {
            CowScoreTier tier = buffed ? pet.buffFitScore(fortification.id()) : pet.generalScore();
            scores.add(tier.petWarFlagValue());
        }
        return scores;
    }
}
