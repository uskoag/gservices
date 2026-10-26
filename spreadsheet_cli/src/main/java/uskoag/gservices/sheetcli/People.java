package uskoag.gservices.sheetcli;

import com.google.api.services.drive.model.Revision;
import java.util.*;

/**
 * Who edited a time window, and how sure that is. Drive Activity names a Google person id, which is not
 * the permissionId a revision carries, so a person is named by matching: whoever alone was editing in the
 * ten minutes before a saved version is the account that saved it. A match is said to be a match.
 *
 * @param confidence certain (one editor) | mixed (several) | inferred (no Activity: the account that saved the closing version)
 *                   | unknown (Activity records nobody, which may only mean it has not caught up)
 */
record People(List<String> names, String confidence, String source) {

    private static final Map<String, Map<String, String>> BY_TIME = new HashMap<>();

    static People in(String id, long from, long to) throws Exception {
        var eds = Activity.editors(id, from, to);
        var span = Times.show(from) + " .. " + Times.show(to);
        if (eds.size() == 1) return new People(List.of(name(id, eds.getFirst())), "certain", "Drive Activity: the only editor " + span);
        if (eds.size() > 1) return new People(eds.stream().map(e -> name(id, e) + " (" + e.count() + " edit(s) " + Times.show(e.first()) + ".." + Times.show(e.last()) + ")").toList(),
                "mixed", "Drive Activity: " + eds.size() + " people edited " + span + "; it cannot say who made which change");
        if (!Activity.unavailable) return new People(List.of(), "unknown", "Drive Activity records no edits " + span
                + (System.currentTimeMillis() - to < 30 * 60_000 ? "; it reports edits minutes late, so recent ones may not be there yet" : ""));
        Revision closer = null;
        for (var r : Revisions.list(id)) if (Revisions.millis(r) >= to && closer == null) closer = r;
        return closer == null ? new People(List.of("(unknown)"), "inferred", "no Drive Activity and no saved version closes " + span)
                : new People(List.of(Revisions.who(closer)), "inferred", "no Drive Activity; " + Revisions.who(closer) + " saved the version closing " + span);
    }

    static String name(String id, Activity.Editor e) {
        if (e.personId().startsWith("(")) return e.personId();
        try {
            for (var r : Revisions.list(id)) {
                var u = r.getLastModifyingUser();
                if (u != null && e.personId().equals(u.getPermissionId())) return Revisions.who(r);
            }
            var hit = byTime(id).get(e.personId());
            if (hit != null) return hit;
        } catch (Exception ex) { Out.info("naming " + e.personId() + ": " + ex.getMessage()); }
        return e.me() ? Auth.email + " (the account reading)" : "person " + e.personId();
    }

    private static Map<String, String> byTime(String id) throws Exception {
        if (BY_TIME.containsKey(id)) return BY_TIME.get(id);
        var m = new HashMap<String, String>();
        for (var r : Revisions.list(id)) {
            var t = Revisions.millis(r);
            var eds = Activity.editors(id, t - 10 * 60_000, t);
            var savedByMe = Auth.email != null && Revisions.email(r).equalsIgnoreCase(Auth.email);
            if (eds.size() == 1 && !eds.getFirst().me() && !savedByMe && !eds.getFirst().personId().startsWith("(")) m.putIfAbsent(eds.getFirst().personId(), Revisions.who(r) + " (matched by time to the version it saved)");
        }
        BY_TIME.put(id, m);
        return m;
    }

    String show() {
        if (names.size() <= 1) return (names.isEmpty() ? "(none recorded)" : names.getFirst()) + "  [" + confidence + ": " + source + "]";
        return "[" + confidence + ": " + source + "]\n" + String.join("\n", names.stream().map(n -> "            " + n).toList());
    }
}
