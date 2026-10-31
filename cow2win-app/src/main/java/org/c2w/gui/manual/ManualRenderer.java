package org.c2w.gui.manual;

import org.c2w.infra.Logger;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.HtmlWriter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The manual ({@code /manual/manual.md} on the classpath) as HTML for a {@code JEditorPane}, plus
 * its outline for the table of contents - no Swing here. Parsed with commonmark-java.
 *
 * <p>Every {@code ##} and {@code ###} heading gets an {@code <a name>} jump target in front
 * ({@code JEditorPane.scrollToReference} ignores {@code id} attributes). A chapter ({@code ##}) takes
 * its fixed anchor from an {@code <a name="..."></a>} line right above it in the Markdown - so the
 * same {@code #...} links also work on GitHub - see {@link ManualChapters}; any other heading gets
 * one derived from its text like GitHub does (lower case, hyphens, made unique).
 *
 * <p>Relative image paths are resolved against the manual's own URL - in the IDE and in the jar
 * alike. A missing image is left out (one log line), a wide one is scaled down to
 * {@link #MAX_IMAGE_WIDTH}.
 */
public final class ManualRenderer {

    /** Classpath path of the manual. */
    public static final String RESOURCE = "/manual/manual.md";

    /** Widest image shown, in pixels - keeps the text column readable on a 1024 x 768 screen. */
    static final int MAX_IMAGE_WIDTH = 700;

    /** An {@code <a name="...">} line in the Markdown - the fixed anchor of the heading below it. */
    private static final Pattern ANCHOR_LINE = Pattern.compile("^\\s*<a\\s+name=\"([^\"]+)\"\\s*>\\s*</a>\\s*$");

    /** One heading of the outline: level 2 (chapter) or 3, its text and its anchor. */
    public record Heading(int level, String text, String anchor) {
    }

    /** Colors (as {@code #rrggbb}), font family and size (pt) of the generated style sheet. */
    public record Style(String fontFamily, int fontSize, String text, String heading, String link,
                        String codeBackground, String rule) {
    }

    private final Node document;
    private final URL base;
    /** Anchor of every {@code ##} / {@code ###} heading node. */
    private final Map<Node, String> anchors = new IdentityHashMap<>();
    private final List<Heading> outline = new ArrayList<>();
    private final Set<String> internalLinks = new LinkedHashSet<>();

    /**
     * @param markdown the manual's text
     * @param base     URL relative image paths are resolved against (the manual's own URL), may be null - then no image is shown
     */
    public ManualRenderer(String markdown, URL base) {
        this.base = base;
        this.document = Parser.builder().build().parse(markdown);
        collectAnchors();
        document.accept(new AbstractVisitor() {
            @Override
            public void visit(Link link) {
                if (link.getDestination().startsWith("#")) {
                    internalLinks.add(link.getDestination().substring(1));
                }
                visitChildren(link);
            }
        });
    }

    /** The manual from the classpath ({@link #RESOURCE}). */
    public static ManualRenderer load() throws IOException {
        URL url = ManualRenderer.class.getResource(RESOURCE);
        if (url == null) {
            throw new IOException(RESOURCE + " not found");
        }
        try (InputStream in = url.openStream()) {
            return new ManualRenderer(new String(in.readAllBytes(), StandardCharsets.UTF_8), url);
        }
    }

    /** The URL relative image paths are resolved against - for {@code HTMLDocument.setBase}. */
    public URL base() {
        return base;
    }

    /** The {@code ##} and {@code ###} headings in document order. */
    public List<Heading> outline() {
        return outline;
    }

    /** Every anchor a {@code #...} link can jump to. */
    public Set<String> anchors() {
        return new LinkedHashSet<>(anchors.values());
    }

    /** The targets (without "#") of every link inside the manual. */
    public Set<String> internalLinks() {
        return internalLinks;
    }

    /** The whole manual as an HTML document with an embedded style sheet in {@code style}. */
    public String html(Style style) {
        HtmlRenderer renderer = HtmlRenderer.builder()
                .nodeRendererFactory(HeadingRenderer::new)
                .nodeRendererFactory(ImageRenderer::new)
                .build();
        return "<html><head><style type=\"text/css\">\n" + styleSheet(style) + "</style></head><body>\n"
                + renderer.render(document) + "</body></html>";
    }

    /** CSS rules for what {@code JEditorPane} understands (HTML 3.2, a little CSS). */
    static String styleSheet(Style s) {
        int size = s.fontSize();
        return "body { font-family: " + s.fontFamily() + "; font-size: " + size + "pt; color: " + s.text() + ";"
                + " margin-left: 14px; margin-right: 14px; margin-top: 6px; margin-bottom: 14px; }\n"
                + "h1 { font-size: " + (size + 8) + "pt; color: " + s.heading() + "; margin-top: 4px; margin-bottom: 4px; }\n"
                + "h2 { font-size: " + (size + 5) + "pt; color: " + s.heading() + "; margin-top: 10px; margin-bottom: 4px; }\n"
                + "h3 { font-size: " + (size + 2) + "pt; color: " + s.heading() + "; margin-top: 4px; margin-bottom: 2px; }\n"
                + "p { margin-top: 4px; margin-bottom: 6px; }\n"
                + "ul { margin-top: 2px; margin-bottom: 6px; margin-left: 22px; }\n"
                + "ol { margin-top: 2px; margin-bottom: 6px; margin-left: 22px; }\n"
                + "li { margin-bottom: 2px; }\n"
                + "a { color: " + s.link() + "; }\n"
                + "code { font-family: Monospaced; font-size: " + size + "pt; background-color: " + s.codeBackground() + "; }\n"
                + "pre { font-family: Monospaced; font-size: " + size + "pt; background-color: " + s.codeBackground() + ";"
                + " margin-left: 10px; padding: 6px; }\n"
                + "blockquote { margin-left: 16px; padding-left: 8px; border-left: 2px solid " + s.rule() + "; }\n"
                + "table { border-collapse: collapse; }\n"
                + "td { padding: 3px; color: " + s.text() + "; }\n"
                + "th { padding: 3px; color: " + s.heading() + "; }\n";
    }

    /**
     * Gives every {@code ##} / {@code ###} heading its anchor and builds the outline. An anchor line
     * ({@link #ANCHOR_LINE}) right above a heading names it and is removed - the heading renderer
     * writes the anchor itself.
     */
    private void collectAnchors() {
        Set<String> used = new HashSet<>();
        String pending = null;
        Node node = document.getFirstChild();
        while (node != null) {
            Node next = node.getNext();
            String explicit = anchorLine(node);
            if (explicit != null) {
                pending = explicit;
                node.unlink();
            } else if (node instanceof org.commonmark.node.Heading heading
                    && (heading.getLevel() == 2 || heading.getLevel() == 3)) {
                String text = textOf(heading);
                String anchor = pending != null ? pending : unique(slug(text), used);
                used.add(anchor);
                anchors.put(heading, anchor);
                outline.add(new Heading(heading.getLevel(), text, anchor));
                pending = null;
            } else {
                pending = null;
            }
            node = next;
        }
    }

    /** The name of an anchor line ({@link #ANCHOR_LINE}), or null if {@code node} is none. */
    private static String anchorLine(Node node) {
        String html;
        if (node instanceof HtmlBlock block) {
            html = block.getLiteral();
        } else if (node instanceof Paragraph) {
            StringBuilder literal = new StringBuilder();
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof HtmlInline inline) {
                    literal.append(inline.getLiteral());
                } else if (!(child instanceof SoftLineBreak)) {
                    return null;
                }
            }
            html = literal.toString();
        } else {
            return null;
        }
        Matcher matcher = ANCHOR_LINE.matcher(html.strip());
        return matcher.matches() ? matcher.group(1) : null;
    }

    /** GitHub-like anchor of a heading text: lower case, only letters, digits, "-" and "_", spaces as "-". */
    static String slug(String text) {
        StringBuilder slug = new StringBuilder();
        for (char c : text.strip().toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
                slug.append(c);
            } else if (c == ' ') {
                slug.append('-');
            }
        }
        return slug.toString();
    }

    /** {@code anchor}, or {@code anchor-1}, {@code anchor-2}, ... if it is taken - like GitHub. */
    private static String unique(String anchor, Set<String> used) {
        String candidate = anchor;
        for (int i = 1; used.contains(candidate); i++) {
            candidate = anchor + "-" + i;
        }
        return candidate;
    }

    /** The plain text of {@code node} - text and inline code, without markup. */
    private static String textOf(Node node) {
        StringBuilder text = new StringBuilder();
        node.accept(new AbstractVisitor() {
            @Override
            public void visit(Text t) {
                text.append(t.getLiteral());
            }

            @Override
            public void visit(Code code) {
                text.append(code.getLiteral());
            }
        });
        return text.toString();
    }

    /** Writes {@code <a name>} in front of every heading with an anchor. */
    private final class HeadingRenderer implements NodeRenderer {
        private final HtmlNodeRendererContext context;
        private final HtmlWriter html;

        HeadingRenderer(HtmlNodeRendererContext context) {
            this.context = context;
            this.html = context.getWriter();
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return Set.of(org.commonmark.node.Heading.class);
        }

        @Override
        public void render(Node node) {
            org.commonmark.node.Heading heading = (org.commonmark.node.Heading) node;
            String tag = "h" + heading.getLevel();
            html.line();
            String anchor = anchors.get(heading);
            if (anchor != null) {
                html.tag("a", Map.of("name", anchor));
                html.tag("/a");
            }
            html.tag(tag);
            for (Node child = heading.getFirstChild(); child != null; child = child.getNext()) {
                context.render(child);
            }
            html.tag("/" + tag);
            html.line();
        }
    }

    /** An image with its size ({@link #MAX_IMAGE_WIDTH} at most) - or nothing if it cannot be read. */
    private final class ImageRenderer implements NodeRenderer {
        private final HtmlWriter html;

        ImageRenderer(HtmlNodeRendererContext context) {
            this.html = context.getWriter();
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return Set.of(Image.class);
        }

        @Override
        public void render(Node node) {
            Image image = (Image) node;
            String source = image.getDestination();
            BufferedImage picture = read(source);
            if (picture == null) {
                return;
            }
            int width = picture.getWidth();
            int height = picture.getHeight();
            if (width > MAX_IMAGE_WIDTH) {
                height = Math.round(height * (float) MAX_IMAGE_WIDTH / width);
                width = MAX_IMAGE_WIDTH;
            }
            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put("src", source);
            attributes.put("width", String.valueOf(width));
            attributes.put("height", String.valueOf(height));
            attributes.put("alt", textOf(image));
            html.tag("img", attributes, true);
        }

        /** {@code source} relative to {@link #base} - {@code new URL(URL, String)} is the only way that also works inside a jar. */
        @SuppressWarnings("deprecation")
        private BufferedImage read(String source) {
            if (base == null) {
                return null;
            }
            try {
                BufferedImage picture = ImageIO.read(new URL(base, source));
                if (picture == null) {
                    Logger.log("Manual: image " + source + " is no readable image - left out");
                }
                return picture;
            } catch (MalformedURLException e) {
                Logger.log("Manual: image " + source + " has an invalid path - left out");
                return null;
            } catch (IOException e) {
                Logger.log("Manual: image " + source + " not found - left out");
                return null;
            }
        }
    }
}
