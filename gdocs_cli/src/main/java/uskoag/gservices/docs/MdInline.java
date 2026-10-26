package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.List;
import java.util.*;

import static uskoag.gservices.docs.Styles.*;

/**
 * One paragraph's elements as a markdown line. Adjacent runs carrying the same markup are merged
 * first, so a word Google split across three runs reads **once** rather than as ****-joined pieces.
 * Suggestions shown inline are written as CriticMarkup, {++inserted++} and {--deleted--}.
 */
final class MdInline {

    private final DocumentTab tab;
    private final Map<String, Integer> footnotes;

    MdInline(DocumentTab tab, Map<String, Integer> footnotes) { this.tab = tab; this.footnotes = footnotes; }

    String line(List<ParagraphElement> els) {
        var out = new ArrayList<String>();
        var buf = new ArrayList<String>();
        var key = (String) null;
        var run = (TextRun) null;
        for (var e : els == null ? List.<ParagraphElement>of() : els) {
            if (e.getTextRun() != null) {
                var k = key(e.getTextRun());
                if (!k.equals(key)) { flush(out, buf, run); key = k; }
                run = e.getTextRun();
                buf.add(Styles.text(run));
            } else {
                flush(out, buf, run);
                key = null;
                out.add(other(e));
            }
        }
        flush(out, buf, run);
        return String.join("", out).replace("\n", "<br>");
    }

    private static String key(TextRun r) {
        var s = r.getTextStyle();
        var k = s == null ? "" : (on(s.getBold()) ? "b" : "") + (on(s.getItalic()) ? "i" : "") + (on(s.getStrikethrough()) ? "s" : "") + "|" + link(s);
        return k + "|" + nonEmpty(r.getSuggestedInsertionIds()) + nonEmpty(r.getSuggestedDeletionIds());
    }

    private static void flush(List<String> out, List<String> buf, TextRun r) {
        if (buf.isEmpty()) return;
        var text = String.join("", buf);
        buf.clear();
        out.add(wrap(text, r));
    }

    /** Markers hug the text; surrounding spaces stay outside, or `** word**` would not parse. */
    private static String wrap(String text, TextRun r) {
        var core = text.strip();
        if (core.isEmpty()) return text;
        var at = text.indexOf(core);
        var lead = text.substring(0, at);
        var trail = text.substring(at + core.length());
        var s = r.getTextStyle();
        core = core.replace("*", "\\*");
        var url = link(s);
        if (url != null) core = "[" + core + "](" + url + ")";
        if (s != null && on(s.getStrikethrough())) core = "~~" + core + "~~";
        if (s != null && on(s.getItalic())) core = "*" + core + "*";
        if (s != null && on(s.getBold())) core = "**" + core + "**";
        if (nonEmpty(r.getSuggestedInsertionIds())) core = "{++" + core + "++}" + ids(r.getSuggestedInsertionIds());
        if (nonEmpty(r.getSuggestedDeletionIds())) core = "{--" + core + "--}" + ids(r.getSuggestedDeletionIds());
        return lead + core + trail;
    }

    private String other(ParagraphElement e) {
        if (e.getInlineObjectElement() != null) {
            var id = e.getInlineObjectElement().getInlineObjectId();
            var uri = imageUri(tab, id);
            return uri == null ? "[object " + id + "]" : "![" + imageAlt(tab, id) + "](" + uri + ")";
        }
        if (e.getFootnoteReference() != null) return "[^" + footnote(e.getFootnoteReference().getFootnoteId()) + "]";
        if (e.getPerson() != null && e.getPerson().getPersonProperties() != null) {
            var p = e.getPerson().getPersonProperties();
            return "[" + Objects.requireNonNullElse(p.getName(), p.getEmail()) + "](mailto:" + p.getEmail() + ")";
        }
        if (e.getRichLink() != null && e.getRichLink().getRichLinkProperties() != null) {
            var p = e.getRichLink().getRichLinkProperties();
            return "[" + Objects.requireNonNullElse(p.getTitle(), p.getUri()) + "](" + p.getUri() + ")";
        }
        if (e.getHorizontalRule() != null) return "\n---\n";
        if (e.getEquation() != null) return "[equation]";
        return "";
    }

    /** read --comments: each suggestion's id after its markup, so an AI can accept, reject or reply to it by id. */
    static boolean showSuggestionIds;

    private static String ids(java.util.List<String> ids) {
        return showSuggestionIds ? "[^s:" + String.join(",", ids) + "]" : "";
    }

    int footnote(String id) { return footnotes.computeIfAbsent(id, k -> footnotes.size() + 1); }
}
