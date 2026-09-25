package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A replace, two ways. When the text occurs once in the whole tab, replaceAllText scoped to that tab:
 * Google keeps the replaced text's formatting itself. Otherwise (the match is only unique inside the
 * named paragraph, or --all within a paragraph) insert-after, restyle as the original, then delete —
 * the delete makes the wallet ask at its irreversible tier, which is why it is the fallback.
 */
final class BuildReplace {

    private BuildReplace() {}

    static Planned build(int order, Edit.Replace r, Scope s, boolean insertsCollide) {
        if (r.find() == null || r.find().isEmpty()) Out.die(r.label() + ": --find is required");
        if (r.with() == null) Out.die(r.label() + ": --with is required");
        var within = r.in() == null ? null : s.anchor(r.label() + " --in", r.in());
        var hits = s.occurrences(r.find(), within);
        var where = within == null ? "tab " + s.name() : "paragraph \"" + within.preview() + "\"";
        if (hits.isEmpty()) Out.die(r.label() + ": \"" + r.find() + "\" does not occur in " + where);
        if (hits.size() > 1 && !r.all()) Out.die(r.label() + ": \"" + r.find() + "\" occurs " + hits.size() + " times in " + where + "; narrow it with --in, or pass --all");

        var tabWide = s.occurrences(r.find(), null).size();
        var viaReplaceAll = tabWide == hits.size() && !insertsCollide;
        var reqs = new ArrayList<Request>();
        if (viaReplaceAll) reqs.add(new Request().setReplaceAllText(new ReplaceAllTextRequest()
                .setContainsText(new SubstringMatchCriteria().setText(r.find()).setMatchCase(true))
                .setReplaceText(r.with())
                .setTabsCriteria(new TabsCriteria().setTabIds(List.of(s.tabId())))));
        else for (var at : hits.reversed()) reqs.addAll(byRange(s, at, r.find().length(), r.with()));

        var report = new ArrayList<String>();
        report.add("replace in " + s.name() + ", " + hits.size() + " occurrence(s), via " + (viaReplaceAll ? "replaceAllText" : "insert+delete"));
        for (var at : hits) {
            var p = s.paraAt(at);
            var t = p.plain();
            var off = at - p.start();
            report.add("  paragraph @" + p.start() + ": \"" + p.preview() + "\"");
            report.add("    - " + around(t, off, off + r.find().length()));
            report.add("    + " + around(t.substring(0, off) + r.with() + t.substring(off + r.find().length()), off, off + r.with().length()));
        }
        return new Planned(order, s.tabId(), hits.get(0), reqs, report);
    }

    private static List<Request> byRange(Scope s, int at, int len, String with) {
        var end = at + len;
        var style = s.paraAt(at).runAt(at);
        var reqs = new ArrayList<Request>();
        reqs.add(new Request().setInsertText(new InsertTextRequest().setText(with).setLocation(new Location().setIndex(end).setTabId(s.tabId()))));
        if (style != null && style.getTextStyle() != null && !with.isEmpty())
            reqs.add(new Request().setUpdateTextStyle(new UpdateTextStyleRequest().setTextStyle(style.getTextStyle()).setFields("*")
                    .setRange(new Range().setStartIndex(end).setEndIndex(end + with.length()).setTabId(s.tabId()))));
        reqs.add(new Request().setDeleteContentRange(new DeleteContentRangeRequest()
                .setRange(new Range().setStartIndex(at).setEndIndex(end).setTabId(s.tabId()))));
        return reqs;
    }

    /** The changed span with some context either side, so a reviewer sees it in place. */
    static String around(String t, int from, int to) {
        var a = Math.max(0, from - 40);
        var b = Math.min(t.length(), to + 40);
        return (a > 0 ? "…" : "") + t.substring(a, from) + "[" + t.substring(from, to) + "]" + t.substring(to, b) + (b < t.length() ? "…" : "");
    }
}
