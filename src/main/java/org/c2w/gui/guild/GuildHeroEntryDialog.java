package org.c2w.gui.guild;

import org.c2w.data.model.*;
import org.c2w.data.repository.Catalog;
import org.c2w.domain.TeamScoreCalculator;
import org.c2w.gui.common.IconLoader;
import org.c2w.i18n.LanguageService;
import org.c2w.service.AppContext;

import java.awt.*;
import java.util.Comparator;

/**
 * {@link GuildTeamEntryDialog} for hero teams - table-based alternative to
 * {@link GuildHeroEntryDialog}, same spec.
 */
public final class GuildHeroEntryDialog extends GuildTeamEntryDialog<Hero> {

    private static final int MAX_HERO_TEAMS = 3;
    private static final int ICON_SIZE = 32;
    private static final String KEY_TITLE = "guildEntry.heroTeams";

    public GuildHeroEntryDialog(Frame owner, AppContext appContext) {
        super(owner, appContext, KEY_TITLE, buildSpec(appContext.catalog()), MAX_HERO_TEAMS);
    }

    private static TeamTypeSpec<Hero> buildSpec(Catalog catalog) {
        return new TeamTypeSpec<>(catalog.heroes().findAll(), GuildHeroEntryDialog::heroLabel,
                h -> IconLoader.iconFor(h.imagePath(), ICON_SIZE), Comparator.comparing(GuildHeroEntryDialog::heroLabel),
                m -> m.heroTeams, FortificationType.HERO, Lineup.TeamType.HERO,
                GuildHeroEntryDialog::heroMatchesBuff, GuildHeroEntryDialog::heroScoreBreakdown,
                catalog.heroTemplates(), Hero::id);
    }

    private static String heroLabel(Hero hero) {
        return LanguageService.displayName(hero.id());
    }

    private static boolean heroMatchesBuff(Fortification fortification, Hero hero) {
        return fortification.buff() instanceof RoleBuff roleBuff && hero.roles().contains(roleBuff.role());
    }

    private static TeamScoreCalculator.Breakdown heroScoreBreakdown(TeamDraft<Hero> teamDraft, Fortification fortification) {
        HeroTeam heroTeam = new HeroTeam(null, 0, teamDraft.members, teamDraft.pet, teamDraft.warFlag,
                teamDraft.totalPower, null);
        return TeamScoreCalculator.scoreFor(heroTeam, fortification);
    }
}
