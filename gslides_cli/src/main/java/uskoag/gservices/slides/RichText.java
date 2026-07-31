package uskoag.gservices.slides;

import java.util.ArrayList;
import java.util.List;

/**
 * The level-1 rich-text value: plain characters plus a run overlay plus a paragraph
 * overlay. Deliberately neutral of any Google type, because the same shape recurs in
 * Sheets (CellData.textFormatRuns), Docs (TextRun/ParagraphStyle) and docx (w:r in w:p).
 * When a second write-consumer appears this record is what gets promoted to a shared
 * module -- not before, or it gets shaped by the one case that existed.
 */
public record RichText(String text, List<RichSpan> spans, List<RichPara> paras) {

    static RichText plain(String t) { return new RichText(t, List.of(), List.of()); }

    boolean styled() { return !spans.isEmpty() || !paras.isEmpty(); }

    /** Collapse runs that carry identical styling, so one <b> spanning three text nodes is one request. */
    RichText merged() {
        if (spans.size() < 2) return this;
        var out = new ArrayList<RichSpan>();
        for (var s : spans) {
            if (s.plain()) continue;
            var last = out.isEmpty() ? null : out.get(out.size() - 1);
            if (last != null && last.end() == s.start() && last.sameStyleAs(s))
                out.set(out.size() - 1, new RichSpan(last.start(), s.end(),
                        last.bold(), last.italic(), last.underline(), last.strike(),
                        last.size(), last.font(), last.color(), last.link()));
            else out.add(s);
        }
        return new RichText(text, out, paras);
    }
}
