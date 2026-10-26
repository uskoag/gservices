package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.Document;
import java.util.ArrayList;
import java.util.List;

import static uskoag.gservices.docs.Styles.on;

/**
 * After --apply the document is read again and each paragraph the verb touched is found once more — by its
 * start index (text did not move), by the picture it holds, or by its text — and its alignment and keep flags
 * compared with what was asked. Google stores the default alignment as unset, which reads back as START.
 */
final class AlignCheck {

    /** Which paragraph (at >= 0, else objectId, else text) and what it should now be; italic is checked on its first run. */
    record Want(String tabId, String text, String objectId, int at, ParaFmt fmt, Boolean italic) {

        static Want text(String tabId, String text, ParaFmt fmt) { return new Want(tabId, text, null, -1, fmt, null); }

        static Want image(String tabId, String objectId, ParaFmt fmt) { return new Want(tabId, null, objectId, -1, fmt, null); }

        static Want at(String tabId, int at, ParaFmt fmt) { return new Want(tabId, null, null, at, fmt, null); }

        Want italic(boolean v) { return new Want(tabId, text, objectId, at, fmt, v); }
    }

    private AlignCheck() {}

    static List<String> verify(List<Want> wants, Document doc) {
        var out = new ArrayList<String>();
        var all = Tabs.flat(doc);
        for (var w : wants) {
            var tab = all.stream().filter(t -> Tabs.id(t).equals(w.tabId())).findFirst().orElse(null);
            var paras = tab == null ? List.<Para>of() : Para.of(tab.getDocumentTab());
            var hits = paras.stream().filter(p -> matches(w, p)).toList();
            var head = w.at() >= 0 ? "@" + w.at() : w.objectId() != null ? "image " + w.objectId() : "\"" + clip(w.text()) + "\"";
            if (hits.isEmpty()) { out.add(head + ": paragraph not found after apply"); continue; }
            var p = hits.getLast();
            var st = p.p().getParagraphStyle();
            var got = st == null || st.getAlignment() == null ? "START" : st.getAlignment();
            if (w.fmt().align() != null && !w.fmt().align().equals(got)) out.add(head + ": alignment expected " + w.fmt().align() + ", got " + got);
            flag(out, head, "keepWithNext", w.fmt().keepWithNext(), st == null ? null : st.getKeepWithNext());
            flag(out, head, "keepLinesTogether", w.fmt().keepLinesTogether(), st == null ? null : st.getKeepLinesTogether());
            if (w.italic() != null) {
                var run = p.runs().isEmpty() ? null : p.runs().getFirst();
                var it = run != null && run.getTextStyle() != null && on(run.getTextStyle().getItalic());
                if (it != w.italic()) out.add(head + ": italic expected " + w.italic() + ", got " + it);
            }
        }
        return out;
    }

    private static boolean matches(Want w, Para p) {
        if (w.at() >= 0) return p.start() == w.at();
        if (w.objectId() != null) return p.p().getElements().stream().anyMatch(e -> e.getInlineObjectElement() != null && w.objectId().equals(e.getInlineObjectElement().getInlineObjectId()));
        return p.plain().equals(Para.norm(w.text()));
    }

    private static void flag(List<String> out, String head, String name, Boolean want, Boolean got) {
        if (want != null && want != on(got)) out.add(head + ": " + name + " expected " + want + ", got " + on(got));
    }

    private static String clip(String s) { return s.length() > 50 ? s.substring(0, 50) + "…" : s; }
}
