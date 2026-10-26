package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.Tab;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * A tab's footnote references, numbered in reading order — the same order and the same numbers
 * `read --format md` shows as {@code [^n]}, so a number copied off a `read` can be used here directly.
 * A footnote's own body (its content, its own segment starting at index 0) is a separate lookup:
 * {@code tab.getDocumentTab().getFootnotes().get(footnoteId)}.
 */
final class Footnotes {

    /** number is 1-based, reading order; start/end is the reference character's range in the tab's body. */
    record Ref(String footnoteId, int number, int start, int end) {}

    private Footnotes() {}

    static List<Ref> refs(Tab tab) {
        var out = new ArrayList<Ref>();
        var seen = new LinkedHashMap<String, Integer>();
        for (var p : Para.of(tab.getDocumentTab()))
            for (var e : p.p().getElements())
                if (e.getFootnoteReference() != null) {
                    var id = e.getFootnoteReference().getFootnoteId();
                    var n = seen.computeIfAbsent(id, k -> seen.size() + 1);
                    out.add(new Ref(id, n, Para.idx(e.getStartIndex()), Para.idx(e.getEndIndex())));
                }
        return out;
    }

    /** {@code sel} is the reading-order number (as `read` shows it, e.g. "3" for {@code [^3]}) or the raw footnote id. */
    static Ref select(Tab tab, String sel) {
        if (sel == null || sel.isBlank()) Out.die("--footnote is required (a number as `read` shows it, e.g. 3 for [^3], or a footnote id)");
        var refs = refs(tab);
        for (var r : refs) if (r.footnoteId().equals(sel)) return r;
        if (sel.chars().allMatch(Character::isDigit)) {
            var n = Integer.parseInt(sel);
            for (var r : refs) if (r.number() == n) return r;
            Out.die("footnote " + n + " out of range: tab " + Tabs.title(tab) + " has " + refs.size() + " footnote(s)");
        }
        Out.die("no footnote '" + sel + "' in tab " + Tabs.title(tab) + " — run `read --tab " + Tabs.title(tab)
                + "` and use the [^n] number, or the footnote id");
        return null;
    }
}
