package uskoag.gservices.docs;

import com.google.api.services.drive.model.Revision;
import java.util.*;

/**
 * Which two versions a diff compares, from whatever the caller gave. With --since and --by alone it
 * answers "what did this person change since then": from the version just before their first saved
 * version after that time, to their last one.
 */
record RevRange(RevSpec from, RevSpec to) {

    static RevRange of(String id, String from, String to, String since, String by, boolean steps) throws Exception {
        var all = RevSpec.saved(id);
        var lo = since == null ? Long.MIN_VALUE : Times.parse(since);
        var f = from == null ? null : RevSpec.resolve(id, from);
        var t = to == null ? null : RevSpec.resolve(id, to);
        if (by != null && (f == null || t == null)) {
            var after = f == null ? 0 : f.n();
            var theirs = all.stream().filter(r -> Revisions.by(r, by) && Revisions.millis(r) > lo && RevSpec.num(r) > after).toList();
            if (theirs.isEmpty()) Out.die("no saved version by \"" + by + "\"" + (since == null ? "" : " since " + Times.show(lo))
                    + "; `history` lists who saved each one");
            if (t == null) t = RevSpec.of(theirs.getLast(), all);
            if (f == null) {
                var i = all.indexOf(theirs.getFirst());
                f = i == 0 ? RevSpec.at(id, 1, all) : RevSpec.of(all.get(i - 1), all);
            }
        }
        if (t == null) t = RevSpec.of(all.getLast(), all);
        if (f == null && since != null) f = RevSpec.resolve(id, since);
        if (f == null) f = steps ? RevSpec.of(all.getFirst(), all) : previous(id, t, all);
        if (f.n() >= t.n()) Out.die("--from r" + f.n() + " is not earlier than --to r" + t.n());
        return new RevRange(f, t);
    }

    private static RevSpec previous(String id, RevSpec t, List<Revision> all) throws Exception {
        Revision prev = null;
        for (var r : all) if (RevSpec.num(r) < t.n()) prev = r;
        return prev != null ? RevSpec.of(prev, all) : RevSpec.at(id, Math.max(1, t.n() - 1), all);
    }

    /** Every revision number in the range (--every n thins it), or only Drive's saved ones with --saved. The ends are always included. */
    static List<RevSpec> stepsBetween(String id, RevSpec f, RevSpec t, boolean savedOnly, int every) throws Exception {
        var all = RevSpec.saved(id);
        var out = new ArrayList<RevSpec>();
        out.add(f);
        if (savedOnly) {
            for (var r : all) { var n = RevSpec.num(r); if (n > f.n() && n < t.n()) out.add(RevSpec.of(r, all)); }
        } else {
            for (var n = f.n() + every; n < t.n(); n += every) out.add(RevSpec.at(id, n, all));
        }
        out.add(t);
        return out;
    }
}
