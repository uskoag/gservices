package uskoag.gservices.docs;

import uskoag.gservices.comments.Identity;
import com.google.api.services.drive.model.Revision;
import com.google.gson.JsonObject;
import java.util.*;

/**
 * Who made revision n, and how sure that is. Sources, strongest first:
 * our own journal (this tool made the edit, so it knows it was the AI and in which session);
 * Drive (a saved version names the account that saved it);
 * Drive Activity (for an in-between number: one editor in the span makes it certain, several make it mixed);
 * inference (an in-between number is given the author of the saved version that closes its span,
 * which is wrong whenever two people edited inside one span — so it is labelled, never passed off as fact).
 */
final class Authors {

    /** @param confidence certain | mixed | inferred */
    record Who(String name, String source, String confidence) {
        String show() { return name + "  [" + confidence + ": " + source + "]"; }

        /** The same person however they were named (saved version, Activity match, account reading), for clubbing a walk. */
        String key() {
            var parts = new java.util.TreeSet<String>();
            for (var p : name.replaceFirst("^mixed: ", "").split(" \\+ ")) parts.add(person(p));
            return (name.startsWith("mixed: ") ? "mixed:" : "") + String.join("+", parts);
        }

        private static String person(String p) {
            var lt = p.indexOf('<');
            if (lt >= 0 && p.indexOf('>', lt) > lt) return p.substring(lt + 1, p.indexOf('>', lt)).toLowerCase();
            var bare = p.replaceAll(" \\(.*\\)", "").replaceAll(" \\[.*\\]", "").strip();
            return bare.toLowerCase();
        }
    }

    static boolean useActivity = true;

    private final String docId;
    private final List<Revision> saved;
    private final List<JsonObject> journal;

    Authors(String docId) throws Exception {
        this.docId = docId;
        this.saved = RevSpec.saved(docId);
        this.journal = Journal.entries(docId);
    }

    Who of(long n) {
        for (var j : journal) {
            var b = j.get("revBefore").getAsLong();
            var a = j.get("revAfter").getAsLong();
            if (b >= 0 && a > b && n > b && n <= a) {
                var ai = str(j, "aiName");
                return new Who("AI (" + ai + ", " + str(j, "agent") + ") as " + str(j, "account"),
                        "our journal, " + str(j, "verb") + " at " + Times.show(java.time.Instant.parse(str(j, "time")).toEpochMilli()), "certain");
            }
        }
        Revision prev = null, next = null;
        for (var r : saved) {
            var k = RevSpec.num(r);
            if (k == n) return new Who(Revisions.who(r), "Drive saved version r" + k, "certain");
            if (k < n) prev = r; else if (next == null) next = r;
        }
        if (next == null) return new Who("(unknown)", "after the last saved version", "inferred");
        var span = "between r" + (prev == null ? 0 : RevSpec.num(prev)) + " and r" + RevSpec.num(next);
        var eds = useActivity ? Activity.editors(docId, prev == null ? 0 : Revisions.millis(prev), Revisions.millis(next)) : List.<Activity.Editor>of();
        if (eds.size() == 1) return new Who(person(eds.getFirst()), "Drive Activity: the only editor " + span, "certain");
        if (eds.size() > 1) return new Who("mixed: " + String.join(" + ", eds.stream().map(this::person).toList()),
                "Drive Activity: " + eds.size() + " people edited " + span + "; " + String.join("; ", eds.stream().map(e -> person(e) + " "
                        + Times.show(e.first()) + ".." + Times.show(e.last()) + " (" + e.count() + ")").toList()), "mixed");
        var closer = Revisions.who(next);
        var same = prev != null && Revisions.who(prev).equals(closer);
        return new Who(closer, same ? "between r" + RevSpec.num(prev) + " and r" + RevSpec.num(next) + ", both saved by them"
                : "r" + RevSpec.num(next) + " closes this span" + (prev == null ? "" : "; r" + RevSpec.num(prev) + " was " + Revisions.who(prev)), "inferred");
    }

    /** A Drive Activity actor by name: its id is a Drive permissionId, so saved versions and identity.json can name it. */
    private String person(Activity.Editor e) {
        for (var r : saved) {
            var u = r.getLastModifyingUser();
            if (u != null && e.personId().equals(u.getPermissionId())) return Revisions.who(r);
        }
        var byTime = byTime().get(e.personId());
        if (byTime != null) return byTime;
        var role = Identity.role(null, e.personId(), null);
        if (!role.equals("other")) return (role.equals("ai") ? Identity.ai.email() : Identity.owner.email()) + " " + Identity.label(role);
        return e.me() ? Auth.email + " (the account reading)" : "person " + e.personId();
    }

    private Map<String, String> byTime;

    /**
     * Drive Activity names people by a Google person id, which is not the permissionId the revision list
     * carries. A saved version names its author, and whoever alone was editing in the ten minutes before
     * it is that author, so the id is matched by time and says so.
     */
    private Map<String, String> byTime() {
        if (byTime != null) return byTime;
        byTime = new HashMap<>();
        if (!useActivity) return byTime;
        for (var r : saved) {
            var t = Revisions.millis(r);
            var eds = Activity.editors(docId, t - 10 * 60_000, t);
            if (eds.size() == 1 && !eds.getFirst().me()) byTime.putIfAbsent(eds.getFirst().personId(), Revisions.who(r) + " (matched by time to saved r" + RevSpec.num(r) + ")");
        }
        return byTime;
    }

    /** Everyone known to have touched (from, to]: Drive's saved versions and our journal entries. */
    List<String> within(long from, long to) {
        var out = new LinkedHashSet<String>();
        for (var r : saved) { var k = RevSpec.num(r); if (k > from && k <= to) out.add(Revisions.who(r) + " (saved r" + k + ")"); }
        for (var j : journal) {
            var a = j.get("revAfter").getAsLong();
            if (a > from && j.get("revBefore").getAsLong() < to) out.add("AI " + str(j, "aiName") + " via " + str(j, "tool") + " as " + str(j, "account")
                    + " (journal r" + j.get("revBefore").getAsLong() + "→r" + a + ")");
        }
        var fs = saved.stream().filter(r -> RevSpec.num(r) == from).findFirst();
        var ts = saved.stream().filter(r -> RevSpec.num(r) == to).findFirst();
        if (useActivity && fs.isPresent() && ts.isPresent())
            for (var e : Activity.editors(docId, Revisions.millis(fs.get()), Revisions.millis(ts.get())))
                out.add(person(e) + " (Drive Activity: " + e.count() + " edit action(s), " + Times.show(e.first()) + ".." + Times.show(e.last()) + ")");
        return new ArrayList<>(out);
    }

    private static String str(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "?"; }
}
