package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A paragraph with its document indexes. {@code text} has exactly one char per index unit, so an
 * offset into it plus {@code start} is a document index: text runs contribute their content and every
 * other element (image, footnote ref, chip) a placeholder of its own width.
 */
record Para(int start, int end, String text, Paragraph p) {

    static final char OBJ = '￼';

    /** Text as matched: without the paragraph mark, with look-alike quotes and spaces folded one-for-one. */
    String plain() { return norm(text.endsWith("\n") ? text.substring(0, text.length() - 1) : text); }

    static String norm(String s) {
        return s.replace(' ', ' ').replace('’', '\'').replace('‘', '\'')
                .replace('“', '"').replace('”', '"');
    }

    /** Anchors are copied from the markdown rendering, so its bold markers are dropped before matching. */
    static String anchor(String s) { return s == null ? null : norm(s.replace("**", "")).strip(); }

    String preview() {
        var s = plain().replace(OBJ, ' ');
        return s.length() > 70 ? s.substring(0, 70) + "…" : s;
    }

    TextRun runAt(int docIndex) {
        for (var e : p.getElements())
            if (e.getTextRun() != null && e.getStartIndex() <= docIndex && docIndex < e.getEndIndex()) return e.getTextRun();
        return null;
    }

    List<TextRun> runs() {
        var out = new ArrayList<TextRun>();
        for (var e : p.getElements()) if (e.getTextRun() != null && !Styles.text(e.getTextRun()).isBlank()) out.add(e.getTextRun());
        return out;
    }

    static List<Para> of(DocumentTab tab) {
        var out = new ArrayList<Para>();
        if (tab != null && tab.getBody() != null) walk(tab.getBody().getContent(), out);
        return out;
    }

    private static void walk(List<StructuralElement> els, List<Para> out) {
        if (els == null) return;
        for (var e : els) {
            if (e.getParagraph() != null) out.add(from(e));
            else if (e.getTable() != null)
                for (var r : e.getTable().getTableRows()) for (var c : r.getTableCells()) walk(c.getContent(), out);
            else if (e.getTableOfContents() != null) walk(e.getTableOfContents().getContent(), out);
        }
    }

    private static Para from(StructuralElement e) {
        var parts = new ArrayList<String>();
        for (var pe : e.getParagraph().getElements()) {
            var w = pe.getEndIndex() - pe.getStartIndex();
            var c = pe.getTextRun() != null ? pe.getTextRun().getContent() : null;
            parts.add(c != null && c.length() == w ? c : String.valueOf(OBJ).repeat(Math.max(w, 0)));
        }
        return new Para(e.getStartIndex(), e.getEndIndex(), String.join("", parts), e.getParagraph());
    }
}
