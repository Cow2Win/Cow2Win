package org.c2w.gui.guild;

import org.c2w.data.model.*;
import org.c2w.data.repository.HeroRepository;
import org.c2w.gui.common.IconLoader;
import org.c2w.util.AppContext;
import org.c2w.util.LanguageService;
import org.c2w.util.TeamScoreCalculator;

import java.awt.*;
import java.util.Comparator;

/**
 * {@link GuildTeamEntryDialog} for hero teams only - the hero-side half of
 * what used to be a single combined {@code GuildEntryDialog} (see that
 * class's replacement, {@link GuildTeamEntryDialog}). Opened from its own
 * toolbar button - see {@code ToolbarPanel#onOpenGuildHeroEntry}.
 */
public final class GuildHeroEntryDialog extends GuildTeamEntryDialog<Hero> {

    private static final int MAX_HERO_TEAMS = 3;

    private static final int ICON_SIZE = 32;

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for this dialog's window title - reused from the old combined dialog's "hero teams" section header. */
    private static final String KEY_TITLE = "guildEntry.heroTeams";

    public GuildHeroEntryDialog(Frame owner, AppContext appContext, Runnable onSaved) {
        super(owner, appContext, onSaved, KEY_TITLE, buildSpec(), MAX_HERO_TEAMS);
    }

    private static SectionSpec<Hero> buildSpec() {
        return new SectionSpec<>(HeroRepository.findAll(), GuildHeroEntryDialog::heroLabel,
                h -> IconLoader.iconFor(h.imagePath(), ICON_SIZE), Comparator.comparing(GuildHeroEntryDialog::heroLabel),
                m -> m.heroTeams, FortificationType.HERO, Lineup.TeamType.HERO,
                GuildHeroEntryDialog::heroMatchesBuff, GuildHeroEntryDialog::heroScoreBreakdown);
    }

    private static String heroLabel(Hero hero) {
        return LanguageService.displayName(hero.id());
    }

    private static boolean heroMatchesBuff(Fortification fortification, Hero hero) {
        return fortification.buff() instanceof RoleBuff roleBuff && hero.roles().contains(roleBuff.role());
    }

    /** Mirrors {@code FortificationEntryDialog#heroScoreBreakdown} - see {@link TeamScoreCalculator#scoreFor(HeroTeam, Fortification)}. */
    private static TeamScoreCalculator.Breakdown heroScoreBreakdown(TeamDraft<Hero> teamDraft, Fortification fortification) {
        HeroTeam heroTeam = new HeroTeam(null, 0, teamDraft.members, teamDraft.totalPower);
        return TeamScoreCalculator.scoreFor(heroTeam, fortification);
    }
}
