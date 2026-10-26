package uskoag.gservices.gdocsmon;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Which awaiting thread becomes an event. The input is one thread from `uskoag-gdocscli comments|suggestions
 * --awaiting --json`. Each post's "role" (owner | ai | other) comes from gdocscli's Identity: with a separate
 * AI account the author decides it, in the one-account mode the AI(...) signature does. The AI's own posts
 * never fire. Only the OWNER's words are instructions.
 *
 * Owner fires when: trigger=all, or the post names ai/claude/the AI account, or the AI already posted in the
 * thread. Anyone else fires on the same, or on tagging the owner, and always waits for approval.
 */
final class Rules {

    private static final Pattern AI_WORD = Pattern.compile("(?<![\\p{L}\\d])(ai|claude)(?![\\p{L}\\d])", Pattern.CASE_INSENSITIVE);

    private Rules() {}

    static Ev classify(Watch w, String kind, JsonObject t) {
        JsonObject last = null;
        var aiThread = false;
        for (var p : t.getAsJsonArray("posts")) {
            var o = p.getAsJsonObject();
            if (role(o).equals("ai")) aiThread = true;
            if (bool(o, "deleted") || str(o, "content") == null || str(o, "content").isBlank()) continue;
            last = o;
        }
        if (last == null || role(last).equals("ai")) return null;
        if (!w.seen.add(str(t, "id") + "/" + str(last, "postId"))) return null;

        var text = str(last, "content");
        var plain = text.replaceAll("[\\^*_~]", "").toLowerCase();
        var owner = role(last).equals("owner");
        var mention = AI_WORD.matcher(plain).find() || names(plain, "ai", null);
        var tagsOwner = names(plain, "owner", w.account);
        var why = mention ? "names the AI" : aiThread ? "reply in a thread the AI posted in" : tagsOwner ? "tags the owner" : w.trigger.equals("all") ? "every owner comment" : null;
        var fires = owner ? (mention || aiThread || w.trigger.equals("all")) : (mention || aiThread || tagsOwner);
        if (!fires) return null;

        var e = new Ev();
        e.doc = w.doc; e.account = w.account; e.kind = kind; e.thread = str(t, "id"); e.postId = str(last, "postId");
        e.author = str(last, "author"); e.owner = owner; e.why = why; e.text = text;
        e.tab = str(t, "tab"); e.para = str(t, "para"); e.quote = str(t, "quote") != null ? str(t, "quote") : str(t, "summary");
        return e;
    }

    /** gdocscli's role when it gave one; older builds: signed = AI, me = owner. */
    static String role(JsonObject p) {
        var r = str(p, "role");
        if (r != null) return r;
        return bool(p, "signed") ? "ai" : bool(p, "me") ? "owner" : "other";
    }

    /** Does the text tag this identity: its email, @local-part, or display name as an @-mention. */
    private static boolean names(String plain, String who, String extraEmail) {
        var ids = new ArrayList<String>();
        if (extraEmail != null) ids.add(extraEmail);
        var id = identity();
        if (id != null && id.has(who) && id.get(who).isJsonObject()) {
            var a = id.getAsJsonObject(who);
            if (str(a, "email") != null) ids.add(str(a, "email"));
            if (str(a, "name") != null && plain.contains("@" + str(a, "name").toLowerCase())) return true;
        }
        for (var mail : ids) {
            var m = mail.toLowerCase();
            if (plain.contains(m) || (m.contains("@") && plain.contains("@" + m.substring(0, m.indexOf('@'))))) return true;
        }
        return false;
    }

    private static JsonObject identity() {
        try {
            var f = Path.of(System.getProperty("user.home"), "uskoag", "gservices", "gdocs", "identity.json");
            return Files.exists(f) ? JsonParser.parseString(Files.readString(f)).getAsJsonObject() : null;
        } catch (Exception e) { return null; }
    }

    static String str(JsonObject o, String k) { var v = o.get(k); return v == null || v.isJsonNull() ? null : v.getAsString(); }

    static boolean bool(JsonObject o, String k) { var v = o.get(k); return v != null && !v.isJsonNull() && v.getAsBoolean(); }
}
