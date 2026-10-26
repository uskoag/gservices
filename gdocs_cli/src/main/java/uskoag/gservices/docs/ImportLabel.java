package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;

/**
 * How a list item's label looks (a bold "1."): the list level's own text style, then the item's, as Docs draws it.
 * A preset list has no level style, so the import puts this on the item's paragraph mark, which is what styles the
 * label. Only what a reader sees is carried: bold, italic, size, colour. Fonts are left out, because a bullet level's
 * font is a symbol font that would turn a numbered label into symbols.
 */
final class ImportLabel {

    private static final java.util.List<String> SEEN = java.util.List.of("bold", "italic", "fontSize", "foregroundColor");

    private ImportLabel() {}

    static TextStyle style(DocumentTab tab, Paragraph p) {
        var out = new TextStyle();
        var lists = tab.getLists();
        var list = lists == null ? null : lists.get(p.getBullet().getListId());
        var lv = list == null || list.getListProperties() == null ? null : list.getListProperties().getNestingLevels();
        var lvl = p.getBullet().getNestingLevel() == null ? 0 : p.getBullet().getNestingLevel();
        if (lv != null && lv.size() > lvl) copy(lv.get(lvl).getTextStyle(), out);
        copy(p.getBullet().getTextStyle(), out);
        return out;
    }

    private static void copy(TextStyle from, TextStyle to) {
        if (from != null) for (var k : SEEN) if (from.get(k) != null) to.set(k, from.get(k));
    }
}
