package org.c2w.gui.guild;

import org.c2w.data.model.*;
import org.c2w.data.repository.TitanRepository;
import org.c2w.gui.common.IconLoader;
import org.c2w.util.AppContext;
import org.c2w.util.LanguageService;
import org.c2w.util.TeamScoreCalculator;

import java.awt.*;
import java.util.Comparator;

/**
 * {@link GuildTeamEntryDialog} for titan teams only - the titan-side half of
 * what used to be a single combined {@code GuildEntryDialog} (see that
 * class's replacement, {@link GuildTeamEntryDialog}). Opened from its own
 * toolbar button - see {@code ToolbarPanel#onOpenGuildTitanEntry}.
 */
public final class GuildTitanEntryDialog extends GuildTeamEntryDialog<Titan> {

    private static final int MAX_TITAN_TEAMS = 2;

    private static final int ICON_SIZE = 32;

    /** Language file key (see {@code resources/language/<name>/<name>.properties}) for this dialog's window title - reused from the old combined dialog's "titan teams" section header. */
    private static final String KEY_TITLE = "guildEntry.titanTeams";

    public GuildTitanEntryDialog(Frame owner, AppContext appContext, Runnable onSaved) {
        super(owner, appContext, onSaved, KEY_TITLE, buildSpec(), MAX_TITAN_TEAMS);
    }

    private static SectionSpec<Titan> buildSpec() {
        return new SectionSpec<>(TitanRepository.findAll(), GuildTitanEntryDialog::titanLabel,
                t -> IconLoader.iconFor(t.imagePath(), ICON_SIZE), Comparator.comparing(GuildTitanEntryDialog::titanLabel),
                m -> m.titanTeams, FortificationType.TITAN, Lineup.TeamType.TITAN,
                GuildTitanEntryDialog::titanMatchesBuff, GuildTitanEntryDialog::titanScoreBreakdown);
    }

    private static String titanLabel(Titan titan) {
        return LanguageService.displayName(titan.id());
    }

    private static boolean titanMatchesBuff(Fortification fortification, Titan titan) {
        return fortification.buff() instanceof ElementBuff elementBuff && titan.element() == elementBuff.element();
    }

    /** The TITAN-side counterpart of {@code GuildHeroEntryDialog#heroScoreBreakdown} - see {@link TeamScoreCalculator#scoreFor(TitanTeam, Fortification)}. */
    private static TeamScoreCalculator.Breakdown titanScoreBreakdown(TeamDraft<Titan> teamDraft, Fortification fortification) {
        TitanTeam titanTeam = new TitanTeam(null, 0, teamDraft.members, teamDraft.totalPower);
        return TeamScoreCalculator.scoreFor(titanTeam, fortification);
    }
}
