package uskoag.gservices.sheetcli;

import com.google.gson.*;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Drive Activity: who edited the file inside a time window. It names people, not cells or revisions, so
 * it cannot say which person made which change; what it can say is how many people edited between two
 * points in time — one person makes the span theirs for certain, several make it mixed.
 */
final class Activity {

    record Editor(String personId, boolean me, long first, long last, int count) {}

    static boolean unavailable;
    private static final Map<String, List<Editor>> CACHE = new HashMap<>();

    private Activity() {}

    /** Distinct editors in (from, to]; empty and flagged unavailable when the wallet or Google says no. */
    static List<Editor> editors(String id, long from, long to) {
        if (unavailable) return List.of();
        return CACHE.computeIfAbsent(id + from + ":" + to, k -> {
            try { return query(id, from, to); }
            catch (Exception e) {
                unavailable = true;
                var m = String.valueOf(e.getMessage());
                var fix = m.contains("has not been used in project") || m.contains("SERVICE_DISABLED")
                        ? "the Drive Activity API is switched off in the Google Cloud project behind this account's OAuth client"
                        : m.contains("wallet") || m.contains("scope") ? "grant 'Drive: activity' to this account: uskoag-walletcli login <email> --groups drive.activity"
                        : "see the message";
                Out.note("[activity] Drive Activity not available (" + m + "). Authorship stays 'inferred'. Fix: " + fix + ".");
                return List.of();
            }
        });
    }

    private static List<Editor> query(String id, long from, long to) throws Exception {
        var access = Auth.activity();
        var url = Raw.root(access, "https://driveactivity.googleapis.com/") + "v2/activity:query";
        var byPerson = new LinkedHashMap<String, long[]>();
        var meIds = new HashSet<String>();
        String page = null;
        do {
            var body = new LinkedHashMap<String, Object>();
            body.put("itemName", "items/" + id);
            body.put("filter", "time > " + from + " AND time <= " + to + " AND detail.action_detail_case:EDIT");
            body.put("consolidationStrategy", Map.of("none", Map.of()));
            body.put("pageSize", 100);
            if (page != null) body.put("pageToken", page);
            var req = Raw.authed(access, url).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Out.GSON.toJson(body))).build();
            var res = Raw.send(req, "activity:query");
            if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode() + " " + brief(new String(res.body(), StandardCharsets.UTF_8)));
            var o = JsonParser.parseString(new String(res.body(), StandardCharsets.UTF_8)).getAsJsonObject();
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
            var pid = actorId(actor.getAsJsonObject(), meIds);
            var v = byPerson.computeIfAbsent(pid, k -> new long[]{Long.MAX_VALUE, 0, 0});
            v[0] = Math.min(v[0], t); v[1] = Math.max(v[1], t); v[2]++;
        }
    }

    /** Every actor counts, the anonymous included: a file editable by anyone with the link is edited by people Google cannot name. */
    private static String actorId(JsonObject ao, Set<String> meIds) {
        if (ao.has("user")) {
            var u = ao.getAsJsonObject("user");
            if (u.has("knownUser")) {
                var ku = u.getAsJsonObject("knownUser");
                var pid = ku.has("personName") ? ku.get("personName").getAsString().replace("people/", "") : "?";
                if (ku.has("isCurrentUser") && ku.get("isCurrentUser").getAsBoolean()) meIds.add(pid);
                return pid;
            }
            if (u.has("deletedUser")) return DELETED;
            return UNKNOWN;
        }
        if (ao.has("anonymous")) return ANONYMOUS;
        if (ao.has("administrator")) return "(a domain administrator)";
        if (ao.has("system")) return "(Google, a system action)";
        if (ao.has("impersonation")) return "(an impersonated account)";
        return UNKNOWN;
    }

    static final String ANONYMOUS = "(anonymous: someone using the anyone-with-the-link access)",
            UNKNOWN = "(an account Google will not name to this reader)",
            DELETED = "(a deleted account)";

    private static long millis(String iso) { return Instant.parse(iso).toEpochMilli(); }

    private static String brief(String s) { s = s.replaceAll("\\s+", " "); return s.length() > 300 ? s.substring(0, 300) + "…" : s; }
}
