package uskoag.gservices.docs;

import java.util.*;

/** history <doc> — every saved version Drive keeps, oldest first, with who and when. */
public final class VerbHistory {

    private VerbHistory() {}

    static void run(List<String> a) throws Exception {
        var json = Args.flag(a, "--json");
        var links = Args.flag(a, "--links");
        var since = Args.val(a, "--since");
        var until = Args.val(a, "--until");
        var by = Args.val(a, "--by");
        var id = DocRef.id(Args.req(a, "<doc>"));
        Args.noneLeft(a, "history");

        var all = Revisions.list(id);
        var lo = since == null ? Long.MIN_VALUE : Times.parse(since);
        var hi = until == null ? Long.MAX_VALUE : Times.parse(until);
        var rows = new ArrayList<Map<String, Object>>();
        for (var i = 0; i < all.size(); i++) {
            var r = all.get(i);
            var t = Revisions.millis(r);
            if (t < lo || t > hi || !Revisions.by(r, by)) continue;
            if (json) {
                var m = new LinkedHashMap<String, Object>();
                m.put("n", i + 1);
                m.put("id", r.getId());
                m.put("modified", Times.iso(t));
                m.put("user", r.getLastModifyingUser() == null ? null : r.getLastModifyingUser().getDisplayName());
                m.put("email", Revisions.email(r));
                m.put("keepForever", Boolean.TRUE.equals(r.getKeepForever()));
                if (links) m.put("exportLinks", r.getExportLinks());
                rows.add(m);
            } else {
                Out.data(String.format("#%-4d %-12s %s  %s%s", i + 1, r.getId(), Times.show(t), Revisions.who(r),
                        Boolean.TRUE.equals(r.getKeepForever()) ? "  [kept]" : ""));
                if (links && r.getExportLinks() != null) r.getExportLinks().forEach((k, v) -> Out.data("        " + k + "  " + v));
            }
        }
        if (json) Out.json(rows);
        else if (!Out.quiet) System.err.println("(" + all.size() + " saved version(s); every revision number between them can also be read"
                + " and compared: read --revision <n>, diff --steps)");
    }
}
