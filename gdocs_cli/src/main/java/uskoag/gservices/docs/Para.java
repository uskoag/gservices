package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A paragraph with its document indexes. {@code text} has exactly one char per index unit, so an
 * offset into it plus {@code start} is a document index: text runs contribute their content and every
 * other element (image, footnote ref, chip) a placeholder of its own width.
 */
record Para(int start, int end, String text, Paragraph p) {

    static final char OBJ = '￼';

    private static final Pattern ESCAPED = Pattern.compile("\\\\(\\p{Punct})");

    /** Text as matched: without the paragraph mark, with look-alike quotes and spaces folded one-for-one. */
    String plain() { return norm(text.endsWith("\n") ? text.substring(0, text.length() - 1) : text); }

    static String norm(String s) {
        return s.replace(' ', ' ').replace('’', '\'').replace('‘', '\'')
                .replace('“', '"').replace('”', '"');
    }

    /**
     * Anchors are copied from the markdown rendering, where a literal asterisk is escaped and bold is wrapped in
     * asterisks. The text as written (escapes undone) is tried first, so a paragraph that really starts with
     * "**" can be named; the same with bold markers dropped follows, for a heading copied from the rendering.
     */
    static List<String> anchorForms(String s) {
        if (s == null || s.isBlank()) return List.of();
        var literal = norm(ESCAPED.matcher(s).replaceAll("$1")).strip();
        var bare = norm(literal.replace("**", "")).strip();
        return literal.equals(bare) || bare.isEmpty() ? List.of(literal) : List.of(literal, bare);
    }

    String preview() {
        var s = plain().replace(OBJ, ' ');
        return s.length() > 70 ? s.substring(0, 70) + "…" : s;
    }

    TextRun runAt(int docIndex) {
        for (var e : p.getElements())
            if (e.getTextRun() != null && idx(e.getStartIndex()) <= docIndex && docIndex < idx(e.getEndIndex())) return e.getTextRun();
        return null;
    }

    /** Google omits a zero index from the JSON (proto3's default-value elision), so the very first
     *  element of a segment that itself starts at document index 0 — true of a footnote's own body,
     *  never of a tab's — reads back with startIndex simply absent, not 0. */
    static int idx(Integer i) { return i == null ? 0 : i; }

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

    /** A footnote's own body: {@code Footnote.getContent()}, a segment of its own starting at index 0. */
    static List<Para> of(List<StructuralElement> content) {
        var out = new ArrayList<Para>();
        walk(content, out);
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
            var w = idx(pe.getEndIndex()) - idx(pe.getStartIndex());
            var c = pe.getTextRun() != null ? pe.getTextRun().getContent() : null;
            parts.add(c != null && c.length() == w ? c : String.valueOf(OBJ).repeat(Math.max(w, 0)));
        }
        return new Para(idx(e.getStartIndex()), idx(e.getEndIndex()), String.join("", parts), e.getParagraph());
    }
}
