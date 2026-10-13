package org.c2w.gui.fort;

import org.c2w.data.model.Guild;
import org.c2w.data.model.Hero;
import org.c2w.data.model.HeroTeam;
import org.c2w.data.model.Lineup;
import org.c2w.data.model.Titan;
import org.c2w.data.model.TitanTeam;
import org.c2w.domain.BuffCalculationService;
import org.c2w.gui.common.IconLoader;
import org.c2w.i18n.LanguageService;

import javax.swing.*;
import java.awt.*;
import java.util.Optional;

/**
 * The avatars of one team in a row, like in the team overviews: the heroes plus pet and war
 * flag (if the team fields them), or the titans. Each avatar's tooltip is its name. Used by
 * the "selected fortification" section of the concept stage view and by
 * {@link FortificationAssignmentDialog}.
 */
public final class TeamAvatars {

    private TeamAvatars() {
    }

    /**
     * A transparent row with the avatars ({@code size} pixels) of the team {@code entry} points
     * at - empty if the team no longer exists in {@code guild}.
     */
    public static JPanel of(Lineup.Entry entry, Guild guild, int size) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        row.setOpaque(false);
        if (entry.teamType() == Lineup.TeamType.HERO) {
            Optional<HeroTeam> team = BuffCalculationService.resolveHeroTeam(entry, guild);
            team.ifPresent(heroTeam -> {
                if (heroTeam.heroes() != null) {
                    for (Hero hero : heroTeam.heroes()) {
                        row.add(avatar(hero.imagePath(), hero.id(), size));
                    }
                }
                if (heroTeam.pet() != null) {
                    row.add(avatar(heroTeam.pet().imagePath(), heroTeam.pet().id(), size));
                }
                if (heroTeam.warFlag() != null) {
                    row.add(avatar(heroTeam.warFlag().imagePath(), heroTeam.warFlag().id(), size));
                }
            });
        } else {
            Optional<TitanTeam> team = BuffCalculationService.resolveTitanTeam(entry, guild);
            team.ifPresent(titanTeam -> {
                for (Titan titan : titanTeam.titans()) {
                    row.add(avatar(titan.imagePath(), titan.id(), size));
                }
            });
        }
        return row;
    }

    /** The avatar at {@code imagePath} with the localized name of {@code id} as tooltip - the name itself if there is no image. */
    private static JLabel avatar(String imagePath, String id, int size) {
        String name = LanguageService.displayName(id);
        Icon icon = IconLoader.iconFor(imagePath, size);
        JLabel label = icon != null ? new JLabel(icon) : new JLabel(name);
        label.setToolTipText(name);
        return label;
    }
}
