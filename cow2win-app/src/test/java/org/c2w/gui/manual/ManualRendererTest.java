package org.c2w.gui.manual;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** {@link ManualRenderer} with the real {@code manual.md} and with small examples - no Swing. */
class ManualRendererTest {

    private static final ManualRenderer.Style STYLE = new ManualRenderer.Style("SansSerif", 12, "#ffffff", "#50c2aa",
            "#64b5f6", "#333333", "#909aa4");

    @Test
    @DisplayName("The real manual renders without errors and starts with the version line")
    void realManualRenders() throws IOException {
        ManualRenderer manual = ManualRenderer.load();
        String html = manual.html(STYLE);
        assertTrue(html.startsWith("<html><head><style"));
        assertTrue(html.contains("This manual describes Cow2Win "), "version line");
        assertNotNull(manual.base());
    }

    @Test
    @DisplayName("All ten chapter ids are <a name> jump targets of the HTML")
    void chapterAnchorsPresent() throws IOException {
        String html = ManualRenderer.load().html(STYLE);
        for (String chapter : ManualChapters.ALL) {
            assertTrue(html.contains("<a name=\"" + chapter + "\"></a>"), chapter);
        }
        // The anchor lines of the Markdown are replaced, not doubled.
        assertEquals(1, count(html, "<a name=\"input\">"));
    }

    @Test
    @DisplayName("The outline holds the ten chapters in the order of ManualChapters.ALL, sections below them")
    void outlineInOrder() throws IOException {
        List<ManualRenderer.Heading> outline = ManualRenderer.load().outline();
        List<String> chapters = outline.stream().filter(h -> h.level() == 2).map(ManualRenderer.Heading::anchor).toList();
        assertEquals(ManualChapters.ALL, chapters);
        assertEquals(2, outline.getFirst().level());
        assertTrue(outline.stream().anyMatch(h -> h.level() == 3), "sections");
        assertEquals("Stage 2: Strategic concept",
                outline.stream().filter(h -> h.anchor().equals(ManualChapters.CONCEPT)).findFirst().orElseThrow().text());
    }

    @Test
    @DisplayName("Every #link of the manual points to an existing anchor; anchors are unique")
    void internalLinksResolve() throws IOException {
        ManualRenderer manual = ManualRenderer.load();
        Set<String> anchors = manual.anchors();
        assertEquals(manual.outline().size(), anchors.size(), "unique anchors");
        assertFalse(manual.internalLinks().isEmpty());
        for (String link : manual.internalLinks()) {
            assertTrue(anchors.contains(link), "#" + link + " has no target");
        }
    }

    @Test
    @DisplayName("Every image of the manual uses one of the five fixed file names under images/")
    void imageNames() throws IOException {
        String markdown = new String(ManualRenderer.class.getResourceAsStream(ManualRenderer.RESOURCE).readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("!\\[[^]]*]\\(([^)]+)\\)").matcher(markdown);
        Set<String> images = new java.util.TreeSet<>();
        while (matcher.find()) {
            images.add(matcher.group(1));
        }
        assertEquals(Set.of("images/main-window.png", "images/input.png", "images/concept.png", "images/output.png",
                "images/journal.png"), images);
    }

    @Test
    @DisplayName("A missing image is left out without an exception; without a base no image is shown")
    void missingImageLeftOut(@TempDir Path dir) throws IOException {
        Path md = dir.resolve("manual.md");
        Files.writeString(md, "## A\n\n![Missing](images/missing.png)\n");
        String html = new ManualRenderer(Files.readString(md), md.toUri().toURL()).html(STYLE);
        assertFalse(html.contains("<img"));
        assertFalse(new ManualRenderer("![x](images/x.png)", null).html(STYLE).contains("<img"));
    }

    @Test
    @DisplayName("An image is shown with its size, a wide one scaled down to 700 px")
    void imageSize(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("images"));
        ImageIO.write(new BufferedImage(1400, 300, BufferedImage.TYPE_INT_RGB), "png", dir.resolve("images/wide.png").toFile());
        ImageIO.write(new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB), "png", dir.resolve("images/small.png").toFile());
        URL base = dir.resolve("manual.md").toUri().toURL();
        String html = new ManualRenderer("![Wide](images/wide.png)\n\n![Small](images/small.png)\n", base).html(STYLE);
        assertTrue(html.contains("<img src=\"images/wide.png\" width=\"700\" height=\"150\" alt=\"Wide\""), html);
        assertTrue(html.contains("<img src=\"images/small.png\" width=\"200\" height=\"100\" alt=\"Small\""), html);
    }

    @Test
    @DisplayName("Chapters take the anchor line above them, sections a unique GitHub-like anchor")
    void anchors() {
        ManualRenderer manual = new ManualRenderer("""
                <a name="first"></a>

                ## First chapter

                ### What's new?

                ### What's new?

                ## Second: chapter
                """, null);
        assertEquals(List.of(new ManualRenderer.Heading(2, "First chapter", "first"),
                        new ManualRenderer.Heading(3, "What's new?", "whats-new"),
                        new ManualRenderer.Heading(3, "What's new?", "whats-new-1"),
                        new ManualRenderer.Heading(2, "Second: chapter", "second-chapter")),
                manual.outline());
        String html = manual.html(STYLE);
        assertTrue(html.contains("<a name=\"first\"></a><h2>First chapter</h2>"), html);
        assertFalse(html.contains("&lt;a name"), "the anchor line is not shown as text");
    }

    @Test
    @DisplayName("Links inside the manual are collected without '#', external ones are not")
    void internalLinksCollected() {
        ManualRenderer manual = new ManualRenderer("See [input](#input), [web](https://example.com) and [mail](mailto:a@b.c).", null);
        assertEquals(Set.of("input"), manual.internalLinks());
    }

    @Test
    @DisplayName("slug: lower case, punctuation dropped, spaces as hyphens")
    void slug() {
        assertEquals("stage-1-input", ManualRenderer.slug("Stage 1: Input"));
        assertEquals("windows-says-windows-protected-your-pc--is-cow2win-safe",
                ManualRenderer.slug("Windows says \"Windows protected your PC\" – is Cow2Win safe?"));
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) {
            count++;
        }
        return count;
    }
}
