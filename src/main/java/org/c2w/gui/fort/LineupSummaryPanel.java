package org.c2w.gui.fort;

import org.c2w.data.model.FortificationType;
import org.c2w.data.model.Guild;
import org.c2w.data.model.Lineup;
import org.c2w.gui.common.GuiUtils;
import org.c2w.util.BuffCalculationService;
import org.c2w.util.LanguageService;

import javax.swing.*;
import java.awt.*;
import java.text.NumberFormat;
import java.util.Locale;

public class LineupSummaryPanel extends JPanel {


    private static final String KEY_HERO_POWER = "lineupSummary.heroPower";
    private static final String KEY_HERO_BUFF_COUNT = "lineupSummary.heroBuffCount";
    private static final String KEY_TITAN_POWER = "lineupSummary.titanPower";
    private static final String KEY_TITAN_BUFF_COUNT = "lineupSummary.titanBuffCount";

    private static final String KEY_HERO_POWER_TOOLTIP = "lineupSummary.heroPowerTooltip";

    private static final String KEY_TITAN_POWER_TOOLTIP = "lineupSummary.titanPowerTooltip";

    private static final String KEY_MILLIONS_SUFFIX = "lineupSummary.millionsSuffix";

    private static final int MILLIONS_THRESHOLD = 1_000_000;

    private static final NumberFormat MILLIONS_FORMAT = buildMillionsFormat();


    private static final Color BACKGROUND_COLOR = Color.DARK_GRAY;

    private static final Color TEXT_COLOR = Color.WHITE;

    private final Guild guild;

    private final JLabel heroPowerLbl = new JLabel();
    private final JLabel heroBuffCountLbl = new JLabel();
    private final JLabel titanPowerLbl = new JLabel();
    private final JLabel titanBuffCountLbl = new JLabel();

    public LineupSummaryPanel(Lineup lineup, Guild guild) {
        super(new GridLayout(4, 1, 0, 2));
        if (lineup == null) {
            throw new IllegalArgumentException("LineupSummaryPanel needs a lineup");
        }
        if (guild == null) {
            throw new IllegalArgumentException("LineupSummaryPanel needs a guild");
        }
        this.guild = guild;
        setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        //setBackground(BACKGROUND_COLOR);
        setOpaque(false);

        heroPowerLbl.setForeground(FortificationType.HERO.getColor());
        heroBuffCountLbl.setForeground(FortificationType.HERO.getColor());
        titanPowerLbl.setForeground(FortificationType.TITAN.getColor());
        titanBuffCountLbl.setForeground(FortificationType.TITAN.getColor());

        // Tooltips added 2026-09-05 alongside shortening the hero/titan power
        // labels themselves (see class Javadoc) - static text, set once here
        // rather than in refresh(Lineup), since neither ever changes with the
        // lineup. The buff-count labels got no tooltip - their own text was
        // never shortened and stays self-descriptive without one.
        heroPowerLbl.setToolTipText(LanguageService.displayName(KEY_HERO_POWER_TOOLTIP));
        titanPowerLbl.setToolTipText(LanguageService.displayName(KEY_TITAN_POWER_TOOLTIP));

        add(heroPowerLbl);
        add(heroBuffCountLbl);
        add(titanPowerLbl);
        add(titanBuffCountLbl);

        refresh(lineup);
    }

    public void refresh(Lineup lineup) {
        if (lineup == null) {
            throw new IllegalArgumentException("lineup must not be null");
        }
        heroPowerLbl.setText(LanguageService.displayName(KEY_HERO_POWER) + ": "
                + formatPower(totalPower(lineup, Lineup.TeamType.HERO)));
        heroBuffCountLbl.setText(LanguageService.displayName(KEY_HERO_BUFF_COUNT) + ": "
                + GuiUtils.NUMBER_FORMAT.format(BuffCalculationService.countHeroesIncreasingBuff(lineup, guild)));
        titanPowerLbl.setText(LanguageService.displayName(KEY_TITAN_POWER) + ": "
                + formatPower(totalPower(lineup, Lineup.TeamType.TITAN)));
        titanBuffCountLbl.setText(LanguageService.displayName(KEY_TITAN_BUFF_COUNT) + ": "
                + GuiUtils.NUMBER_FORMAT.format(BuffCalculationService.countTitansIncreasingBuff(lineup, guild)));
    }

    /** Sums {@link Lineup.Entry#totalPower()} over every entry of the given team type. */
    private static int totalPower(Lineup lineup, Lineup.TeamType teamType) {
        int total = 0;
        for (Lineup.Entry entry : lineup.entries()) {
            if (entry.teamType() == teamType) {
                total += entry.totalPower();
            }
        }
        return total;
    }

    private static String formatPower(int power) {
        if (Math.abs(power) >= MILLIONS_THRESHOLD) {
            double millions = power / (double) MILLIONS_THRESHOLD;
            return MILLIONS_FORMAT.format(millions) + " " + LanguageService.displayName(KEY_MILLIONS_SUFFIX);
        }
        return GuiUtils.NUMBER_FORMAT.format(power);
    }

  
    private static NumberFormat buildMillionsFormat() {
        NumberFormat format = NumberFormat.getInstance(Locale.GERMANY);
        format.setGroupingUsed(false);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(1);
        return format;
    }
}
