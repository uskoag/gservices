package uskoag.gservices.slides;

import com.google.api.services.slides.v1.model.ParagraphStyle;
import com.google.api.services.slides.v1.model.TextStyle;
import java.util.ArrayList;

/** Compact style summaries, so a runs dump stays readable and cheap in tokens. */
public final class Styles {

    private Styles() {}

    static String summary(TextStyle st) {
        if (st == null) return "(inherit)";
        var p = new ArrayList<String>();
        if (Boolean.TRUE.equals(st.getBold())) p.add("B");
        if (Boolean.TRUE.equals(st.getItalic())) p.add("I");
        if (Boolean.TRUE.equals(st.getUnderline())) p.add("U");
        if (Boolean.TRUE.equals(st.getStrikethrough())) p.add("S");
        if (st.getFontSize() != null && st.getFontSize().getMagnitude() != null)
            p.add(Math.round(st.getFontSize().getMagnitude()) + "pt");
        if (st.getFontFamily() != null) p.add(st.getFontFamily());
        var hex = Colors.hex(st.getForegroundColor());
        if (hex != null) p.add(hex);
        if (st.getLink() != null && st.getLink().getUrl() != null) p.add("link:" + st.getLink().getUrl());
        return p.isEmpty() ? "(default)" : String.join(",", p);
    }

    /** Google TextStyle -> our neutral span, so a captured style can be re-applied verbatim. */
    static RichSpan toSpan(TextStyle st, int start, int end) {
        if (st == null) return new RichSpan(start, end, null, null, null, null, null, null, null, null);
        var size = st.getFontSize() != null ? st.getFontSize().getMagnitude() : null;
        var color = Colors.hex(st.getForegroundColor());
        if (color != null && color.startsWith("#")) color = color.substring(1);
        var link = st.getLink() != null ? st.getLink().getUrl() : null;
        return new RichSpan(start, end, st.getBold(), st.getItalic(), st.getUnderline(),
                st.getStrikethrough(), size, st.getFontFamily(), color, link);
    }

    static String paraSummary(ParagraphStyle ps) {
        if (ps == null) return null;
        var p = new ArrayList<String>();
        if (ps.getAlignment() != null) p.add(ps.getAlignment());
        if (ps.getLineSpacing() != null) p.add("ls=" + ps.getLineSpacing());
        if (ps.getIndentStart() != null && ps.getIndentStart().getMagnitude() != null)
            p.add("indent=" + Math.round(Geom.inches(ps.getIndentStart().getMagnitude()) * 100) / 100.0 + "in");
        return p.isEmpty() ? null : String.join(",", p);
    }
}
