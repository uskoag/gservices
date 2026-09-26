package uskoag.gservices.docs;

import com.google.api.services.drive.model.Revision;
import java.util.List;

/**
 * What --revision/--from/--to accept, resolved to Google's revision number.
 *
 * <p>The number is Google's own edit counter, and Drive's history lists only a few of them. Every number
 * in between is still a real version that can be exported, which is what makes a step-by-step walk
 * possible; its time and author are simply not published, so those are reported as a range.
 *
 * @param n      the revision number (as in the export URL)
 * @param saved  the listed revision it is, or null for an in-between number
 */
record RevSpec(long n, Revision saved, String when, String who) {

    static List<Revision> cache;
    static String cacheFor;

    static List<Revision> saved(String docId) throws Exception {
        if (!docId.equals(cacheFor)) { cache = Revisions.list(docId); cacheFor = docId; }
        if (cache.isEmpty()) Out.die("Drive lists no revisions for this document");
        return cache;
    }

    static long head(String docId) throws Exception { return num(saved(docId).getLast()); }

    static long num(Revision r) {
        try { return Long.parseLong(r.getId()); }
        catch (NumberFormatException e) { Out.die("revision id " + r.getId() + " is not numeric; only native Google Docs are supported"); return 0; }
    }

    /** 140 (a revision number) · #3 (third in `history`) · first · current|latest|head · head~5 · a time · "before <time>". */
    static RevSpec resolve(String docId, String spec) throws Exception {
        var all = saved(docId);
        var s = spec.strip().toLowerCase();
        if (s.equals("current") || s.equals("latest") || s.equals("head")) return of(all.getLast(), all);
        if (s.equals("first")) return of(all.getFirst(), all);
        if (s.startsWith("head~")) return at(docId, head(docId) - Long.parseLong(s.substring(5)), all);
        if (s.startsWith("#")) {
            var i = Integer.parseInt(s.substring(1));
            if (i < 1 || i > all.size()) Out.die(spec + ": history has #1..#" + all.size());
            return of(all.get(i - 1), all);
        }
        if (s.matches("\\d{1,7}")) return at(docId, Long.parseLong(s), all);
        var before = s.startsWith("before ");
        var t = Times.parse(before ? spec.strip().substring(7) : spec);
        Revision hit = null;
        for (var r : all) if (before ? Revisions.millis(r) < t : Revisions.millis(r) <= t) hit = r;
        if (hit == null) Out.die("no saved revision " + (before ? "before " : "at or before ") + Times.show(t)
                + "; the first is " + Times.show(Revisions.millis(all.getFirst())));
        return of(hit, all);
    }

    static RevSpec at(String docId, long n, List<Revision> all) throws Exception {
        if (n < 1 || n > head(docId)) Out.die("revision " + n + " is outside 1.." + head(docId));
        for (var r : all) if (num(r) == n) return of(r, all);
        Revision prev = null, next = null;
        for (var r : all) { if (num(r) < n) prev = r; else if (next == null) next = r; }
        var when = (prev == null ? "(start)" : Times.show(Revisions.millis(prev))) + " .. " + (next == null ? "?" : Times.show(Revisions.millis(next)));
        var who = next == null ? "(unknown)" : "(between saved versions; " + Revisions.who(next) + " saved the next)";
        return new RevSpec(n, null, when, who);
    }

    static RevSpec of(Revision r, List<Revision> all) {
        return new RevSpec(num(r), r, Times.show(Revisions.millis(r)), Revisions.who(r));
    }

    String label() { return "r" + n + (saved == null ? "" : " (#" + (cache.indexOf(saved) + 1) + ")"); }
}
