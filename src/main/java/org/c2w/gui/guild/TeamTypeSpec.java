package org.c2w.gui.guild;

import org.c2w.data.model.Fortification;
import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.TeamTemplateRepository;
import org.c2w.domain.TeamScoreCalculator;

import javax.swing.*;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Everything {@link GuildTeamEntryDialog} needs to know about the ONE team
 * type (hero or titan) it edits - which catalog, team list, fortification
 * type and lineup team type, how to label/draw an entry, how to score a team.
 * Built once by {@link GuildHeroEntryDialog}/{@link GuildTitanEntryDialog}.
 * {@code templates}/{@code idOf} enable the team templates, see
 * {@link TeamEditorPanel#enableTemplates}.
 */
record TeamTypeSpec<T>(List<T> catalog, Function<T, String> label, Function<T, Icon> icon,
                       Comparator<T> catalogOrder, Function<MemberDraft, List<TeamDraft<T>>> teamsOf,
                       FortificationType fortificationType, Lineup.TeamType teamType,
                       BiFunction<Fortification, T, Boolean> matchesBuff,
                       BiFunction<TeamDraft<T>, Fortification, TeamScoreCalculator.Breakdown> scoreBreakdownOf,
                       TeamTemplateRepository templates, Function<T, String> idOf) {
}
