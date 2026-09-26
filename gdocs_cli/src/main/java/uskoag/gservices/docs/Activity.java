package uskoag.gservices.docs;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.http.HttpRequest;
import java.time.Instant;
import java.util.*;

/**
 * Drive Activity: who edited a document inside a time window. It names people, not revision numbers, so it
 * cannot say which person made which in-between revision; what it can say is how many people edited
 * between two saved versions — one person makes the whole span theirs for certain, several make it mixed.
 * Actors come back as "people/<id>"; for a Google account that id is its Drive permissionId, which is how
 * they are matched to names from the revision list and identity.json.
 */
final class Activity {

    /** A person's edits in the window: first and last seen, and how many actions. */
    record Editor(String personId, boolean me, long first, long last, int count) {}

    static boolean unavailable;
    private static final Map<String, List<Editor>> CACHE = new HashMap<>();

    private Activity() {}

    /** Distinct editors in (from, to]; empty and flagged unavailable when the wallet or Google says no. */
    static List<Editor> editors(String docId, long from, long to) {
        if (unavailable) return List.of();
        return CACHE.computeIfAbsent(docId + from + ":" + to, k -> {
            try { return query(docId, from, to); }
            catch (Exception e) {
                unavailable = true;
                Out.info("Drive Activity unavailable: " + e.getMessage());
                var m = String.valueOf(e.getMessage());
                var fix = m.contains("has not been used in project") || m.contains("SERVICE_DISABLED")
                        ? "the Drive Activity API is switched off in the Google Cloud project behind this account's OAuth client; enable it at the link above"
                        : m.contains("wallet") || m.contains("scope") ? "grant 'Drive: activity' to this account: uskoag-walletcli login <email> --groups drive.activity"
                        : "see the message";
                if (!Out.quiet) System.err.println("[activity] Drive Activity not available (" + m + "). In-between authors stay 'inferred'. Fix: " + fix + ".");
                return List.of();
            }
        });
    }

    private static List<Editor> query(String docId, long from, long to) throws Exception {
        var access = Auth.activityAccess();
        var url = Raw.root(access, "https://driveactivity.googleapis.com/") + "v2/activity:query";
        var byPerson = new LinkedHashMap<String, long[]>();
        var meIds = new HashSet<String>();
        String page = null;
        do {
            var body = new LinkedHashMap<String, Object>();
            body.put("itemName", "items/" + docId);
            body.put("filter", "time > " + from + " AND time <= " + to + " AND detail.action_detail_case:EDIT");
            body.put("consolidationStrategy", Map.of("none", Map.of()));
            body.put("pageSize", 100);
            if (page != null) body.put("pageToken", page);
            var req = Raw.authed(access, url).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Out.GSON.toJson(body))).build();
            var res = Raw.send(req, "activity:query");
            if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode() + " " + brief(new String(res.body())));
            var o = JsonParser.parseString(new String(res.body(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            if (o.has("activities")) for (var a : o.getAsJsonArray("activities")) add(a.getAsJsonObject(), byPerson, meIds);
            page = o.has("nextPageToken") ? o.get("nextPageToken").getAsString() : null;
        } while (page != null);
        var out = new ArrayList<Editor>();
        byPerson.forEach((p, v) -> out.add(new Editor(p, meIds.contains(p), v[0], v[1], (int) v[2])));
        return out;
    }

    private static void add(JsonObject a, Map<String, long[]> byPerson, Set<String> meIds) {
        var t = a.has("timestamp") ? millis(a.get("timestamp").getAsString())
                : a.has("timeRange") ? millis(a.getAsJsonObject("timeRange").get("endTime").getAsString()) : 0;
        if (!a.has("actors")) return;
        for (var actor : a.getAsJsonArray("actors")) {
            var ao = actor.getAsJsonObject();
            if (!ao.has("user") || !ao.getAsJsonObject("user").has("knownUser")) continue;
            var ku = ao.getAsJsonObject("user").getAsJsonObject("knownUser");
            var id = ku.has("personName") ? ku.get("personName").getAsString().replace("people/", "") : "?";
            if (ku.has("isCurrentUser") && ku.get("isCurrentUser").getAsBoolean()) meIds.add(id);
            var v = byPerson.computeIfAbsent(id, k -> new long[]{Long.MAX_VALUE, 0, 0});
            v[0] = Math.min(v[0], t); v[1] = Math.max(v[1], t); v[2]++;
        }
    }

    private static long millis(String iso) { return Instant.parse(iso).toEpochMilli(); }

    private static String brief(String s) { s = s.replaceAll("\\s+", " "); return s.length() > 300 ? s.substring(0, 300) + "…" : s; }
}
