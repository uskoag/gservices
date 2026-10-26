package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.List;

/** Null-safe reads over the Docs model, which leaves every unset property null rather than false. */
public final class Styles {

    private Styles() {}

    static boolean on(Boolean b) { return Boolean.TRUE.equals(b); }

    static boolean nonEmpty(List<?> l) { return l != null && !l.isEmpty(); }

    static String link(TextStyle s) {
        if (s == null || s.getLink() == null) return null;
        var l = s.getLink();
        if (l.getUrl() != null) return l.getUrl();
        if (l.getHeading() != null) return "#heading=" + l.getHeading().getId();
        if (l.getHeadingId() != null) return "#heading=" + l.getHeadingId();
        if (l.getBookmarkId() != null) return "#" + l.getBookmarkId();
        return l.getTabId() != null ? "#tab=" + l.getTabId() : null;
    }

    static String hex(OptionalColor c) {
        if (c == null || c.getColor() == null || c.getColor().getRgbColor() == null) return null;
        var rgb = c.getColor().getRgbColor();
        return String.format("#%02x%02x%02x", ch(rgb.getRed()), ch(rgb.getGreen()), ch(rgb.getBlue()));
    }

    private static int ch(Float f) { return f == null ? 0 : Math.round(f * 255); }

    /** 0 for body text, 1-6 for a heading; TITLE reads as h1 and SUBTITLE as h2. */
    static int heading(Paragraph p) {
        var st = p.getParagraphStyle() == null ? null : p.getParagraphStyle().getNamedStyleType();
        if (st == null) return 0;
        return switch (st) {
            case "TITLE" -> 1;
            case "SUBTITLE" -> 2;
            default -> st.startsWith("HEADING_") ? Integer.parseInt(st.substring(8)) : 0;
        };
    }

    static int level(Paragraph p) {
        return p.getBullet() == null || p.getBullet().getNestingLevel() == null ? 0 : p.getBullet().getNestingLevel();
    }

    /** A glyph type (DECIMAL, ALPHA, ROMAN...) means numbered; a glyph symbol means bulleted. */
    static boolean ordered(Paragraph p, DocumentTab tab) {
        var lists = tab.getLists();
        if (p.getBullet() == null || lists == null) return false;
        var list = lists.get(p.getBullet().getListId());
        if (list == null || list.getListProperties() == null) return false;
        var levels = list.getListProperties().getNestingLevels();
        if (levels == null || levels.size() <= level(p)) return false;
        var g = levels.get(level(p)).getGlyphType();
        return g != null && !g.equals("GLYPH_TYPE_UNSPECIFIED") && !g.equals("NONE");
    }

    private static EmbeddedObject embedded(DocumentTab tab, String objectId) {
        var io = tab.getInlineObjects() == null ? null : tab.getInlineObjects().get(objectId);
        return io == null || io.getInlineObjectProperties() == null ? null : io.getInlineObjectProperties().getEmbeddedObject();
    }

    static String imageUri(DocumentTab tab, String objectId) {
        var eo = embedded(tab, objectId);
        return eo == null || eo.getImageProperties() == null ? null : eo.getImageProperties().getContentUri();
    }

    static String imageAlt(DocumentTab tab, String objectId) {
        var eo = embedded(tab, objectId);
        if (eo == null) return "";
        return eo.getTitle() != null ? eo.getTitle() : eo.getDescription() != null ? eo.getDescription() : "";
    }

    /** Text as it reads: the trailing paragraph mark dropped, a soft line break (vertical tab) kept as a newline. */
    static String text(TextRun r) {
        var c = r.getContent() == null ? "" : r.getContent();
        if (c.endsWith("\n")) c = c.substring(0, c.length() - 1);
        return c.replace('\u000b', '\n');
    }
}
