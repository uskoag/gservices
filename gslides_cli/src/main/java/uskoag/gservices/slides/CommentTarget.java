package uskoag.gservices.slides;

import com.google.api.services.slides.v1.model.*;
import java.util.*;
import java.util.List;

/**
 * Where `comment add` anchors: a whole slide, a whole element, a character range of a shape's text
 * (given as @S:E or found by --find), a whole table, or one table cell (optionally a range in it).
 * Resolved against the same read the batch is pinned to, so a deck edited in between is refused.
 */
record CommentTarget(Map<String, Object> anchor, String label) {

    static CommentTarget resolve(Presentation pres, String on, Integer slide, String find, String cell) {
        if (on == null && slide == null) Out.die("comment add: say where: --on <elem>[@S:E] or --slide N");
        if (on != null && slide != null) Out.die("comment add: --on and --slide are alternatives; an element id already names its slide");
        if (on == null) {
            if (find != null || cell != null) Out.die("comment add: --find and --cell need --on <elem>");
            var page = Deck.page(pres, slide.toString());
            return new CommentTarget(CommentApi.onObject(page.getObjectId()), "slide " + slide + " itself");
        }
        var er = ElemRange.parse(on);
        var el = Deck.element(pres, er.elem());
        if (er.start() != null && find != null) Out.die("comment add: give @S:E or --find, not both");
        if (el.getTable() != null) return table(el, er, find, cell);
        if (cell != null) Out.die("comment add: --cell applies to a table; " + er.elem() + " is " + Els.typeOf(el));
        if (er.start() == null && find == null) return new CommentTarget(CommentApi.onObject(el.getObjectId()), "element " + label(el));
        var text = text(Els.textContent(el));
        var r = er.start() != null ? check(text, er.start(), er.end()) : once(text, find, "in " + er.elem());
        return new CommentTarget(CommentApi.onText(el.getObjectId(), r[0], r[1]),
                "text \"" + clip(text.substring(r[0], r[1])) + "\" @" + r[0] + ":" + r[1] + " of " + label(el));
    }

    private static CommentTarget table(PageElement el, ElemRange er, String find, String cell) {
        if (cell == null) {
            if (er.start() != null || find != null) Out.die("comment add: " + er.elem() + " is a table; name the cell with --cell R,C (1-based)");
            return new CommentTarget(CommentApi.onTable(el.getObjectId()), "table " + el.getObjectId());
        }
        var rc = cell.split(",");
        if (rc.length != 2) Out.die("comment add: --cell R,C (1-based), e.g. --cell 2,3");
        var row = Integer.parseInt(rc[0].strip()) - 1;
        var col = Integer.parseInt(rc[1].strip()) - 1;
        var rows = el.getTable().getTableRows();
        if (row < 0 || rows == null || row >= rows.size()) Out.die("comment add: row out of range (1-" + (rows == null ? 0 : rows.size()) + ")");
        var cells = rows.get(row).getTableCells();
        if (col < 0 || cells == null || col >= cells.size()) Out.die("comment add: column out of range (1-" + (cells == null ? 0 : cells.size()) + ")");
        var name = "cell " + (row + 1) + "," + (col + 1) + " of table " + el.getObjectId();
        if (er.start() == null && find == null) return new CommentTarget(CommentApi.onCell(el.getObjectId(), row, col, null, null), name);
        var text = text(cells.get(col).getText());
        var r = er.start() != null ? check(text, er.start(), er.end()) : once(text, find, "in " + name);
        return new CommentTarget(CommentApi.onCell(el.getObjectId(), row, col, r[0], r[1]),
                "text \"" + clip(text.substring(r[0], r[1])) + "\" @" + r[0] + ":" + r[1] + " in " + name);
    }

    /** The text laid out at the API's own indices, so a position found here is a position Google accepts. */
    static String text(TextContent tc) {
        if (tc == null || tc.getTextElements() == null) return "";
        var sb = new StringBuilder();
        for (var te : tc.getTextElements()) {
            var s = te.getTextRun() != null ? te.getTextRun().getContent() : te.getAutoText() != null ? te.getAutoText().getContent() : null;
            if (s == null) continue;
            var at = te.getStartIndex() == null ? 0 : te.getStartIndex();
            while (sb.length() < at) sb.append(' ');
            if (sb.length() == at) sb.append(s);
        }
        return sb.toString();
    }

    private static int[] once(String text, String find, String where) {
        var hits = new ArrayList<Integer>();
        for (var i = text.indexOf(find); i >= 0; i = text.indexOf(find, i + 1)) hits.add(i);
        if (hits.size() != 1) Out.die("comment add: \"" + find + "\" occurs " + hits.size() + " times " + where + "; it must occur once");
        return new int[]{hits.get(0), hits.get(0) + find.length()};
    }

    private static int[] check(String text, int start, int end) {
        if (start < 0 || end > text.length() || start >= end) Out.die("comment add: range " + start + ":" + end + " is outside the text (0:" + text.length() + "); run `runs <deck> <elem>`");
        return new int[]{start, end};
    }

    private static String label(PageElement el) {
        var p = Els.preview(el, 40);
        return el.getObjectId() + " (" + Els.typeOf(el) + ")" + (p.isEmpty() ? "" : " \"" + p + "\"");
    }

    private static String clip(String s) { s = s.replace("\n", " ⏎ "); return s.length() > 60 ? s.substring(0, 60) + "…" : s; }
}
