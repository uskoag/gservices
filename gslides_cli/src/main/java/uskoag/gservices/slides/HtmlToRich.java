package uskoag.gservices.slides;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;

/**
 * HTML is the level-1 text format because it is already this office's rich-text input
 * (uskoag-gmailcli --html), needs no new dependency, and unlike markdown can express
 * underline, colour, size and font. Crucially it means the common path carries NO index
 * arithmetic: offsets shift on every edit, and an agent computing them is an agent
 * getting them wrong.
 */
public final class HtmlToRich {

    private static final Set<String>
            BOLD = Set.of("b", "strong"),
            ITALIC = Set.of("i", "em"),
            UNDER = Set.of("u", "ins"),
            STRIKE = Set.of("s", "strike", "del"),
            BLOCK = Set.of("p", "div", "li", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote");

    private HtmlToRich() {}

    static RichText convert(String html) {
        var doc = Jsoup.parse(html, "", Parser.htmlParser());
        var sb = new StringBuilder();
        var spans = new ArrayList<RichSpan>();
        var paras = new ArrayList<RichPara>();
        var stack = new ArrayDeque<Element>();
        for (var child : doc.body().childNodes()) walk(child, sb, stack, spans, paras);
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') sb.setLength(sb.length() - 1);
        return new RichText(sb.toString(), spans, paras).merged();
    }

    private static void walk(Node n, StringBuilder sb, Deque<Element> stack,
                             List<RichSpan> spans, List<RichPara> paras) {
        if (n instanceof TextNode t) {
            var s = t.getWholeText().replace(' ', ' ');
            if (s.isEmpty()) return;
            var start = sb.length();
            sb.append(s);
            var sp = styleOf(stack, start, sb.length());
            if (!sp.plain()) spans.add(sp);
            return;
        }
        if (!(n instanceof Element e)) return;
        var tag = e.tagName().toLowerCase();

        if (tag.equals("br")) { sb.append('\n'); return; }

        var blockStart = sb.length();
        stack.push(e);
        for (var c : e.childNodes()) walk(c, sb, stack, spans, paras);
        stack.pop();

        if (BLOCK.contains(tag)) {
            if (sb.length() > blockStart) {
                var bullet = tag.equals("li") ? bulletFor(e) : null;
                var align = alignOf(e);
                if (bullet != null || align != null) paras.add(new RichPara(blockStart, sb.length(), align, bullet));
            }
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
        }
    }

    private static String bulletFor(Element li) {
        var parent = li.parent();
        return parent != null && parent.tagName().equalsIgnoreCase("ol")
                ? "NUMBERED_DIGIT_ALPHA_ROMAN" : "BULLET_DISC_CIRCLE_SQUARE";
    }

    private static String alignOf(Element e) {
        var a = Css.parse(e.attr("style")).get("text-align");
        if (a == null) return null;
        return switch (a.toLowerCase()) {
            case "center" -> "CENTER";
            case "right" -> "END";
            case "justify" -> "JUSTIFIED";
            case "left" -> "START";
            default -> null;
        };
    }

    /** Effective style at this point = the union of every enclosing tag, nearest wins. */
    private static RichSpan styleOf(Deque<Element> stack, int start, int end) {
        Boolean bold = null, italic = null, under = null, strike = null;
        Double size = null;
        String font = null, color = null, link = null;
        for (var e : stack) {
            var tag = e.tagName().toLowerCase();
            if (BOLD.contains(tag)) bold = true;
            if (ITALIC.contains(tag)) italic = true;
            if (UNDER.contains(tag)) under = true;
            if (STRIKE.contains(tag)) strike = true;
            if (tag.equals("a") && link == null && !e.attr("href").isBlank()) link = e.attr("href");
            var css = Css.parse(e.attr("style"));
            if (size == null) size = Css.points(css.get("font-size"));
            if (font == null) font = Css.font(css.get("font-family"));
            if (color == null) color = Css.hex(css.get("color"));
            if (bold == null && "bold".equalsIgnoreCase(css.get("font-weight"))) bold = true;
            if (italic == null && "italic".equalsIgnoreCase(css.get("font-style"))) italic = true;
        }
        return new RichSpan(start, end, bold, italic, under, strike, size, font, color, link);
    }
}
