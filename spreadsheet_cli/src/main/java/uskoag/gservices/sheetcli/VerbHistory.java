package uskoag.gservices.sheetcli;

import java.util.*;

/** history <id> — every saved version Drive keeps, oldest first, with who and when; --who adds who edited inside each span. */
final class VerbHistory {

    private VerbHistory() {}

    static void run(List<String> a) throws Exception {
        var json = Args.flag(a, "--json");
        var who = Args.flag(a, "--who");
        var since = Args.val(a, "--since");
        var until = Args.val(a, "--until");
        var by = Args.val(a, "--by");
        var id = Args.sheetId(Args.req(a, "<spreadsheetId>"));
        Args.noneLeft(a, "history");

        var all = Revisions.list(id);
        var lo = since == null ? Long.MIN_VALUE : Times.parse(since);
        var hi = until == null ? Long.MAX_VALUE : Times.parse(until);
        var rows = new ArrayList<Map<String, Object>>();
        for (var i = 0; i < all.size(); i++) {
            var r = all.get(i);
            var t = Revisions.millis(r);
            if (t < lo || t > hi || !Revisions.by(r, by)) continue;
            var span = who && i > 0 ? People.in(id, Revisions.millis(all.get(i - 1)), t) : null;
            if (json) rows.add(row(i + 1, r, t, span));
            else {
                Out.data(String.format("#%-4d r%-8s %s  %s%s", i + 1, r.getId(), Times.show(t), Revisions.who(r),
                        Boolean.TRUE.equals(r.getKeepForever()) ? "  [kept]" : ""));
                if (span != null) Out.data("        edited since #" + i + ": " + span.show());
            }
        }
        if (who && hi == Long.MAX_VALUE && !all.isEmpty()) {
            var now = System.currentTimeMillis();
            var after = People.in(id, Revisions.millis(all.getLast()), now);
            if (json) {
                var m = new LinkedHashMap<String, Object>();
                m.put("n", "current");
                m.put("modified", Times.iso(now));
                m.put("editedSincePrevious", span(after));
                rows.add(m);
            } else Out.data("current  edited since #" + all.size() + ": " + after.show());
        }
        if (json) Out.json(rows);
        else Out.note("(" + all.size() + " saved version(s). Drive keeps few versions of a busy sheet, so edits between two of them form one span."
                + (who ? "" : " --who adds who edited inside each span.") + ")");
    }

    private static Map<String, Object> row(int n, com.google.api.services.drive.model.Revision r, long t, People span) {
        var m = new LinkedHashMap<String, Object>();
        m.put("n", n);
        m.put("revision", r.getId());
        m.put("modified", Times.iso(t));
        m.put("user", r.getLastModifyingUser() == null ? null : r.getLastModifyingUser().getDisplayName());
        m.put("email", Revisions.email(r));
        m.put("keepForever", Boolean.TRUE.equals(r.getKeepForever()));
        if (span != null) m.put("editedSincePrevious", span(span));
        return m;
    }

    private static Map<String, Object> span(People p) {
        var m = new LinkedHashMap<String, Object>();
        m.put("people", p.names());
        m.put("confidence", p.confidence());
        m.put("source", p.source());
        return m;
    }
}
