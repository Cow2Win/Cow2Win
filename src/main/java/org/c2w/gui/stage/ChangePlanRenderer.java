package org.c2w.gui.stage;

import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.domain.LineupChangePlanService.ChangeStep;
import org.c2w.domain.LineupChangePlanService.ChangeType;
import org.c2w.domain.LineupComparisonService.TeamKey;
import org.c2w.i18n.LanguageService;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * GUI-free text of a change plan: the steps as HTML (heading, intro, summary, then the
 * sections remove / move / place as numbered lists) and the same as plain text for the
 * clipboard. Steps are named "member · team (type)" and fortifications by their display name.
 */
public final class ChangePlanRenderer {

    private static final String KEY_HEADING = "changePlan.heading";
    private static final String KEY_INTRO = "changePlan.intro";
    private static final String KEY_NO_CHANGES = "changePlan.noChanges";
    private static final String KEY_SUMMARY = "changePlan.summary";
    private static final String KEY_SECTION_REMOVE = "changePlan.sectionRemove";
    private static final String KEY_SECTION_MOVE = "changePlan.sectionMove";
    private static final String KEY_SECTION_PLACE = "changePlan.sectionPlace";
    private static final String KEY_STEP_REMOVE = "changePlan.stepRemove";
    private static final String KEY_STEP_MOVE = "changePlan.stepMove";
    private static final String KEY_STEP_PLACE = "changePlan.stepPlace";

    private ChangePlanRenderer() {
    }

    /** The plan as HTML (for an editor pane) and as plain text (for the clipboard). */
    public record RenderedPlan(String html, String plainText) {
    }

    /** Renders {@code steps}; member names come from {@code guild}. */
    public static RenderedPlan render(List<ChangeStep> steps, Guild guild) {
        Map<ChangeType, List<ChangeStep>> byType = new EnumMap<>(ChangeType.class);
        for (ChangeType type : ChangeType.values()) {
            byType.put(type, new ArrayList<>());
        }
        steps.forEach(step -> byType.get(step.type()).add(step));

        long removeCount = byType.get(ChangeType.REMOVE).size();
        long moveCount = byType.get(ChangeType.MOVE).size();
        long placeCount = byType.get(ChangeType.PLACE).size();

        StringBuilder html = new StringBuilder("<html><body style='font-family:sans-serif; margin:6px;'>");
        html.append("<h2>").append(escape(LanguageService.displayName(KEY_HEADING))).append("</h2>");
        html.append("<p>").append(escape(LanguageService.displayName(KEY_INTRO))).append("</p>");

        StringBuilder plain = new StringBuilder();
        plain.append(LanguageService.displayName(KEY_HEADING)).append("\n");

        if (steps.isEmpty()) {
            html.append("<p><b>").append(escape(LanguageService.displayName(KEY_NO_CHANGES))).append("</b></p>");
            plain.append(LanguageService.displayName(KEY_NO_CHANGES)).append("\n");
        } else {
            String summary = LanguageService.displayName(KEY_SUMMARY,
                    steps.size(), removeCount, moveCount, placeCount);
            html.append("<p><b>").append(escape(summary)).append("</b></p>");
            plain.append(summary).append("\n");

            appendSection(html, plain, KEY_SECTION_REMOVE, byType.get(ChangeType.REMOVE), guild);
            appendSection(html, plain, KEY_SECTION_MOVE, byType.get(ChangeType.MOVE), guild);
            appendSection(html, plain, KEY_SECTION_PLACE, byType.get(ChangeType.PLACE), guild);
        }
        html.append("</body></html>");
        return new RenderedPlan(html.toString(), plain.toString());
    }

    private static void appendSection(StringBuilder html, StringBuilder plain, String sectionKey,
                                      List<ChangeStep> steps, Guild guild) {
        if (steps.isEmpty()) {
            return;
        }
        String heading = LanguageService.displayName(sectionKey) + " (" + steps.size() + ")";
        html.append("<h3>").append(escape(heading)).append("</h3><ol>");
        plain.append("\n").append(heading).append("\n");
        int number = 1;
        for (ChangeStep step : steps) {
            String sentence = sentenceFor(step, guild);
            html.append("<li>").append(escape(sentence)).append("</li>");
            plain.append(number++).append(". ").append(sentence).append("\n");
        }
        html.append("</ol>");
    }

    /** One step as a sentence, e.g. "Anna · Team 1 (heroes) von Rathaus nach Bastion verschieben". */
    static String sentenceFor(ChangeStep step, Guild guild) {
        String team = teamDesignation(step.teamKey(), guild);
        return switch (step.type()) {
            case REMOVE -> LanguageService.displayName(KEY_STEP_REMOVE, team,
                    fortificationName(step.fromFortificationId()));
            case MOVE -> LanguageService.displayName(KEY_STEP_MOVE, team,
                    fortificationName(step.fromFortificationId()), fortificationName(step.toFortificationId()));
            case PLACE -> LanguageService.displayName(KEY_STEP_PLACE, team,
                    fortificationName(step.toFortificationId()));
        };
    }

    private static String teamDesignation(TeamKey teamKey, Guild guild) {
        return memberName(teamKey.teamMemberId(), guild) + " · "
                + GuildMember.teamLabel(teamKey.teamIndex()) + " ("
                + LanguageService.displayName("teamType." + teamKey.teamType().name()) + ")";
    }

    private static String fortificationName(String fortificationId) {
        return fortificationId == null
                ? LanguageService.displayName("common.none")
                : FortificationRepository.findById(fortificationId)
                        .map(f -> LanguageService.displayName(f.id()))
                        .orElse(fortificationId);
    }

    /** The member's name in {@code guild}, or the id if the guild has no such member. */
    static String memberName(String memberId, Guild guild) {
        if (guild == null || guild.members() == null) {
            return memberId;
        }
        return guild.members().stream()
                .filter(m -> m.id().equals(memberId))
                .findFirst()
                .map(GuildMember::name)
                .orElse(memberId);
    }

    /** Minimal HTML escaping for the dynamic strings (member/fortification names, translated text). */
    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
