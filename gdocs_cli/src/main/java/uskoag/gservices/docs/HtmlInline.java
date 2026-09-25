package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.TextRun;
import java.util.ArrayList;
import org.jsoup.nodes.Element;

import static uskoag.gservices.docs.Styles.*;

/** One text run as nested inline tags, innermost the text itself. */
final class HtmlInline {

    private HtmlInline() {}

    static void run(Element parent, TextRun r) {
        var text = Styles.text(r);
        if (text.isEmpty()) return;
        var el = parent;
        if (nonEmpty(r.getSuggestedInsertionIds())) el = el.appendElement("ins");
        if (nonEmpty(r.getSuggestedDeletionIds())) el = el.appendElement("del");
        var s = r.getTextStyle();
        if (s != null) {
            var url = link(s);
            if (url != null) el = el.appendElement("a").attr("href", url);
            if (on(s.getBold())) el = el.appendElement("b");
            if (on(s.getItalic())) el = el.appendElement("i");
            if (on(s.getUnderline()) && url == null) el = el.appendElement("u");
            if (on(s.getStrikethrough())) el = el.appendElement("s");
            var css = css(r);
            if (!css.isEmpty()) el = el.appendElement("span").attr("style", css);
        }
        var lines = text.split("\n", -1);
        for (var i = 0; i < lines.length; i++) {
            if (i > 0) el.appendElement("br");
            el.appendText(lines[i]);
        }
    }

    private static String css(TextRun r) {
        var s = r.getTextStyle();
        var parts = new ArrayList<String>();
        var fg = hex(s.getForegroundColor());
        if (fg != null && !fg.equals("#000000") && link(s) == null) parts.add("color: " + fg);
        var bg = hex(s.getBackgroundColor());
        if (bg != null) parts.add("background-color: " + bg);
        if (s.getFontSize() != null && s.getFontSize().getMagnitude() != null) parts.add("font-size: " + fmt(s.getFontSize().getMagnitude()) + "pt");
        if (s.getWeightedFontFamily() != null && s.getWeightedFontFamily().getFontFamily() != null)
            parts.add("font-family: " + s.getWeightedFontFamily().getFontFamily());
        if ("SUPERSCRIPT".equals(s.getBaselineOffset())) parts.add("vertical-align: super");
        if ("SUBSCRIPT".equals(s.getBaselineOffset())) parts.add("vertical-align: sub");
        return String.join("; ", parts);
    }

    private static String fmt(double d) { return d == Math.rint(d) ? "" + (long) d : "" + d; }
}
