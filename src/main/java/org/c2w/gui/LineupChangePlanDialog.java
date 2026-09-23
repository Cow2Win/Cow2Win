package org.c2w.gui;

import org.c2w.data.model.Guild;
import org.c2w.data.model.GuildMember;
import org.c2w.data.model.Lineup;
import org.c2w.data.repository.FortificationRepository;
import org.c2w.data.repository.LineupRepository;
import org.c2w.eval.LineupAlgorithm;
import org.c2w.eval.LineupAlgorithms;
import org.c2w.util.AppContext;
import org.c2w.util.LanguageService;
import org.c2w.util.LineupChangePlanService;
import org.c2w.util.LineupChangePlanService.ChangeStep;
import org.c2w.util.LineupChangePlanService.ChangeType;
import org.c2w.util.LineupComparisonService;
import org.c2w.util.LineupComparisonService.LineupComparison;
import org.c2w.util.LineupComparisonService.TeamKey;
import org.c2w.util.LineupFiles;
import org.c2w.util.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Produces the step-by-step "what to change on the Hero Wars side" guide that
 * transforms the guild's fixed {@code Original} lineup (the actual in-game
 * deployment, see {@link LineupFiles}) into a chosen target lineup - either
 * another saved ".lineup" file or a candidate a {@link LineupAlgorithm}
 * produces on the fly. Purely read-only: it never saves the target or touches
 * {@link AppContext}; the diff/step computation lives in
 * {@link LineupComparisonService}/{@link LineupChangePlanService} and this
 * class is only the Swing wiring around it, following the same non-modal,
 * dispose-on-close pattern as {@link LineupComparisonDialog}.
 */
public class LineupChangePlanDialog extends JDialog {

    /** Dialog title - hardcoded, not localized (matches {@link LineupComparisonDialog}/{@link ReportViewerDialog}). */
    private static final String BASE_TITLE = "Cow2 - In-Game Change Plan";

    private static final String LINEUP_FILE_GLOB = "*.lineup";

    private static final String KEY_TARGET_SAVED = "changePlan.targetSavedLineup";
    private static final String KEY_TARGET_ALGORITHM = "changePlan.targetAlgorithm";
    private static final String KEY_ALGORITHM_LABEL = "teamsOverview.algorithm";
    private static final String KEY_GENERATE = "changePlan.generate";
    private static final String KEY_COPY = "changePlan.copy";
    private static final String KEY_HEADING = "changePlan.heading";
    private static final String KEY_INTRO = "changePlan.intro";
    private static final String KEY_NO_CHANGES = "changePlan.noChanges";
    private static final String KEY_NO_ORIGINAL = "changePlan.noOriginal";
    private static final String KEY_SELECT_TARGET = "changePlan.selectTarget";
    private static final String KEY_DIFFERENT_GUILD = "changePlan.differentGuild";
    private static final String KEY_SUMMARY = "changePlan.summary";
    private static final String KEY_SECTION_REMOVE = "changePlan.sectionRemove";
    private static final String KEY_SECTION_MOVE = "changePlan.sectionMove";
    private static final String KEY_SECTION_PLACE = "changePlan.sectionPlace";
    private static final String KEY_STEP_REMOVE = "changePlan.stepRemove";
    private static final String KEY_STEP_MOVE = "changePlan.stepMove";
    private static final String KEY_STEP_PLACE = "changePlan.stepPlace";

    private final AppContext appContext;
    private final Guild guild;

    private final JRadioButton savedLineupRadio = new JRadioButton(LanguageService.displayName(KEY_TARGET_SAVED));
    private final JRadioButton algorithmRadio = new JRadioButton(LanguageService.displayName(KEY_TARGET_ALGORITHM));
    private final JComboBox<String> targetLineupCombo = new JComboBox<>();
    private final JComboBox<LineupAlgorithm> algorithmCombo = new JComboBox<>();
    private final JPanel savedLineupPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final JPanel algorithmPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));

    private final JEditorPane outputPane = new JEditorPane();
    private final JButton copyButton = new JButton(LanguageService.displayName(KEY_COPY));

    /** Plain-text form of the currently shown plan, for {@link #copyButton} - empty until the first successful {@link #onGenerate()}. */
    private String plainTextPlan = "";

    public LineupChangePlanDialog(Frame owner, AppContext appContext) {
        super(owner, BASE_TITLE, false);
        if (appContext == null) {
            throw new IllegalArgumentException("LineupChangePlanDialog needs an appContext");
        }
        this.appContext = appContext;
        this.guild = appContext.guild();
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(savedLineupRadio);
        modeGroup.add(algorithmRadio);
        savedLineupRadio.setSelected(true);
        savedLineupRadio.addActionListener(e -> updateModeVisibility());
        algorithmRadio.addActionListener(e -> updateModeVisibility());

        populateTargetCombo();
        populateAlgorithmCombo();

        savedLineupPanel.add(targetLineupCombo);
        algorithmPanel.add(new JLabel(LanguageService.displayName(KEY_ALGORITHM_LABEL)));
        algorithmPanel.add(algorithmCombo);

        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        modePanel.add(savedLineupRadio);
        modePanel.add(algorithmRadio);

        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        modePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        savedLineupPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        algorithmPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        controlPanel.add(modePanel);
        controlPanel.add(savedLineupPanel);
        controlPanel.add(algorithmPanel);

        JButton generateButton = new JButton(LanguageService.displayName(KEY_GENERATE));
        generateButton.addActionListener(e -> onGenerate());

        JPanel topPanel = new JPanel(new BorderLayout(8, 4));
        topPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        topPanel.add(controlPanel, BorderLayout.CENTER);
        topPanel.add(generateButton, BorderLayout.EAST);

        outputPane.setEditable(false);
        outputPane.setContentType("text/html");
        outputPane.setText("");

        copyButton.setEnabled(false);
        copyButton.addActionListener(e -> onCopy());
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
        bottomPanel.add(copyButton);

        setLayout(new BorderLayout());
        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(outputPane), BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        updateModeVisibility();
        setSize(760, 620);
        setLocationRelativeTo(owner);
    }

    private void updateModeVisibility() {
        boolean savedMode = savedLineupRadio.isSelected();
        savedLineupPanel.setVisible(savedMode);
        algorithmPanel.setVisible(!savedMode);
    }

    private void populateAlgorithmCombo() {
        algorithmCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof LineupAlgorithm algorithm) {
                    setText(algorithm.displayName());
                }
                return this;
            }
        });
        for (LineupAlgorithm algorithm : LineupAlgorithms.ALL) {
            algorithmCombo.addItem(algorithm);
        }
    }

    /** Lists every ".lineup" file of the current guild EXCEPT the Original itself (which is always the "before" side here), suffix-stripped for display. */
    private void populateTargetCombo() {
        targetLineupCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof String fileName && fileName.endsWith(LineupFiles.SUFFIX)) {
                    setText(fileName.substring(0, fileName.length() - LineupFiles.SUFFIX.length()));
                }
                return this;
            }
        });
        List<String> fileNames = listTargetLineupFileNames();
        fileNames.forEach(targetLineupCombo::addItem);

        String currentFileName = appContext.lineupFilePath() == null
                ? null : appContext.lineupFilePath().getFileName().toString();
        if (currentFileName != null && fileNames.contains(currentFileName)) {
            targetLineupCombo.setSelectedItem(currentFileName);
        }
    }

    private List<String> listTargetLineupFileNames() {
        List<String> result = new ArrayList<>();
        Path guildDir = appContext.guildFilePath() == null ? null : appContext.guildFilePath().getParent();
        if (guildDir == null || !Files.isDirectory(guildDir)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(guildDir, LINEUP_FILE_GLOB)) {
            for (Path path : stream) {
                String fileName = path.getFileName().toString();
                if (!LineupFiles.isOriginalFileName(fileName)) {
                    result.add(fileName);
                }
            }
        } catch (IOException e) {
            Logger.logException("Could not list lineup files for the change plan", e);
        }
        result.sort(Comparator.naturalOrder());
        return result;
    }

    private void onGenerate() {
        Path guildDir = appContext.guildFilePath().getParent();
        Path originalPath = LineupFiles.originalPathFor(guildDir);
        if (!Files.exists(originalPath)) {
            JOptionPane.showMessageDialog(this, LanguageService.displayName(KEY_NO_ORIGINAL),
                    BASE_TITLE, JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Lineup original;
        try {
            original = LineupRepository.load(originalPath);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not load the Original lineup:\n" + e.getMessage(),
                    BASE_TITLE, JOptionPane.ERROR_MESSAGE);
            return;
        }

        Lineup target;
        if (savedLineupRadio.isSelected()) {
            String fileName = (String) targetLineupCombo.getSelectedItem();
            if (fileName == null) {
                JOptionPane.showMessageDialog(this, LanguageService.displayName(KEY_SELECT_TARGET),
                        BASE_TITLE, JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                target = LineupRepository.load(guildDir.resolve(fileName));
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Could not load the target lineup:\n" + e.getMessage(),
                        BASE_TITLE, JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!original.guildId().equals(target.guildId())) {
                JOptionPane.showMessageDialog(this, LanguageService.displayName(KEY_DIFFERENT_GUILD),
                        BASE_TITLE, JOptionPane.WARNING_MESSAGE);
                return;
            }
        } else {
            LineupAlgorithm algorithm = (LineupAlgorithm) algorithmCombo.getSelectedItem();
            if (algorithm == null) {
                JOptionPane.showMessageDialog(this, LanguageService.displayName(KEY_SELECT_TARGET),
                        BASE_TITLE, JOptionPane.WARNING_MESSAGE);
                return;
            }
            target = algorithm.run(original, guild);
        }

        LineupComparison comparison = LineupComparisonService.compare(original, target, guild);
        List<ChangeStep> steps = LineupChangePlanService.from(comparison);
        render(steps);
    }

    private void render(List<ChangeStep> steps) {
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

            appendSection(html, plain, KEY_SECTION_REMOVE, byType.get(ChangeType.REMOVE));
            appendSection(html, plain, KEY_SECTION_MOVE, byType.get(ChangeType.MOVE));
            appendSection(html, plain, KEY_SECTION_PLACE, byType.get(ChangeType.PLACE));
        }
        html.append("</body></html>");

        outputPane.setText(html.toString());
        outputPane.setCaretPosition(0);
        plainTextPlan = plain.toString();
        copyButton.setEnabled(true);
    }

    private void appendSection(StringBuilder html, StringBuilder plain, String sectionKey, List<ChangeStep> steps) {
        if (steps.isEmpty()) {
            return;
        }
        String heading = LanguageService.displayName(sectionKey) + " (" + steps.size() + ")";
        html.append("<h3>").append(escape(heading)).append("</h3><ol>");
        plain.append("\n").append(heading).append("\n");
        int number = 1;
        for (ChangeStep step : steps) {
            String sentence = sentenceFor(step);
            html.append("<li>").append(escape(sentence)).append("</li>");
            plain.append(number++).append(". ").append(sentence).append("\n");
        }
        html.append("</ol>");
    }

    private String sentenceFor(ChangeStep step) {
        String team = teamDesignation(step.teamKey());
        return switch (step.type()) {
            case REMOVE -> LanguageService.displayName(KEY_STEP_REMOVE, team,
                    fortificationName(step.fromFortificationId()));
            case MOVE -> LanguageService.displayName(KEY_STEP_MOVE, team,
                    fortificationName(step.fromFortificationId()), fortificationName(step.toFortificationId()));
            case PLACE -> LanguageService.displayName(KEY_STEP_PLACE, team,
                    fortificationName(step.toFortificationId()));
        };
    }

    private String teamDesignation(TeamKey teamKey) {
        return memberName(teamKey.teamMemberId()) + " · "
                + GuildMember.teamLabel(teamKey.teamIndex()) + " (" + teamKey.teamType() + ")";
    }

    private String fortificationName(String fortificationId) {
        return fortificationId == null
                ? LanguageService.displayName("common.none")
                : FortificationRepository.findById(fortificationId)
                        .map(f -> LanguageService.displayName(f.id()))
                        .orElse(fortificationId);
    }

    private String memberName(String memberId) {
        return guild.members().stream()
                .filter(m -> m.id().equals(memberId))
                .findFirst()
                .map(GuildMember::name)
                .orElse(memberId);
    }

    private void onCopy() {
        StringSelection selection = new StringSelection(plainTextPlan);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
    }

    /** Minimal HTML escaping for the small set of dynamic strings (member/fortification names, translated text) shown in {@link #outputPane}. */
    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
