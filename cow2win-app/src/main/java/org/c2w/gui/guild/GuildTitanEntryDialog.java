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
 * {@link GuildTeamEntryDialog} for titan teams - table-based alternative to
 * {@link GuildTitanEntryDialog}, same spec.
 */
public final class GuildTitanEntryDialog extends GuildTeamEntryDialog<Titan> {

    private static final int MAX_TITAN_TEAMS = 2;
    private static final int ICON_SIZE = 32;
    private static final String KEY_TITLE = "guildEntry.titanTeams";

    public GuildTitanEntryDialog(Frame owner, AppContext appContext) {
        super(owner, appContext, KEY_TITLE, buildSpec(appContext.catalog()), MAX_TITAN_TEAMS);
    }

    /** The team assignment for the one member {@code memberId} - see {@link GuildTeamEntryDialog}. */
    public GuildTitanEntryDialog(Frame owner, AppContext appContext, String memberId) {
        super(owner, appContext, KEY_TITLE, buildSpec(appContext.catalog()), MAX_TITAN_TEAMS, memberId);
    }

    private static TeamTypeSpec<Titan> buildSpec(Catalog catalog) {
        return new TeamTypeSpec<>(catalog.titans().findAll(), GuildTitanEntryDialog::titanLabel,
                t -> IconLoader.iconFor(t.imagePath(), ICON_SIZE), Comparator.comparing(GuildTitanEntryDialog::titanLabel),
                m -> m.titanTeams, FortificationType.TITAN, Lineup.TeamType.TITAN,
                GuildTitanEntryDialog::titanMatchesBuff, GuildTitanEntryDialog::titanScoreBreakdown,
                catalog.titanTemplates(), Titan::id);
    }

    private static String titanLabel(Titan titan) {
        return LanguageService.displayName(titan.id());
    }

    private static boolean titanMatchesBuff(Fortification fortification, Titan titan) {
        return fortification.buff() instanceof ElementBuff elementBuff && titan.element() == elementBuff.element();
    }

    private static TeamScoreCalculator.Breakdown titanScoreBreakdown(TeamDraft<Titan> teamDraft, Fortification fortification) {
        TitanTeam titanTeam = GuildDraftConverter.toTitanTeam(null, 0, teamDraft, null);
        return TeamScoreCalculator.scoreFor(titanTeam, fortification);
    }
}
