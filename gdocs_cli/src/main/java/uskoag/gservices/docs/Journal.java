package uskoag.gservices.docs;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * Every change this tool makes to a document's text, recorded where only we can record it. Drive cannot
 * tell an AI edit from a human one made under the same account; this journal can, because it is written by
 * the process that made the edit, with the revision numbers on both sides and who drove it.
 * One NDJSON file per document under ~/uskoag/gservices/gdocs/journal/. A failure to journal never fails the edit.
 */
public final class Journal {

    private Journal() {}

    static Path file(String docId) {
        return Path.of(System.getProperty("user.home"), "uskoag", "gservices", "gdocs", "journal", docId + ".ndjson");
    }

    static <T> T around(String docId, String verb, String summary, Callable<T> edit) throws Exception {
        var before = head(docId);
        var at = Instant.now();
        var result = edit.call();
        try {
            var after = before;
            for (var i = 0; i < 4 && after <= before; i++) { if (i > 0) Thread.sleep(1500); after = head(docId); }
            write(docId, entry(verb, summary, at, before, after));
        } catch (Exception e) {
            Out.error("edit applied, but the journal entry could not be written: " + e.getMessage());
        }
        return result;
    }

    private static long head(String docId) {
        try {
            var all = Revisions.list(docId);
            return all.isEmpty() ? -1 : RevSpec.num(all.getLast());
        } catch (Exception e) {
            Out.info("revision number unavailable for the journal: " + e.getMessage());
            return -1;
        }
    }

    private static Map<String, Object> entry(String verb, String summary, Instant at, long before, long after) {
        var m = new LinkedHashMap<String, Object>();
        m.put("time", at.toString());
        m.put("account", Auth.email);
        m.put("accountRole", Identity.actingAsAi() ? "ai" : Identity.split() ? "not the AI account" : "shared (no identity set)");
        if (Identity.owner != null) m.put("owner", Identity.owner.email());
        m.put("tool", Auth.APP_NAME);
        m.put("verb", verb);
        m.put("summary", summary);
        m.put("revBefore", before);
        m.put("revAfter", after);
        var agent = System.getenv("AI_AGENT");
        if (agent == null && System.getenv("CLAUDECODE") != null) agent = "claude-code";
        m.put("agent", agent == null ? "unknown (no AI agent in the environment; possibly a person at the console)" : agent);
        m.put("aiName", AiSign.prefix().replaceAll("^AI\\((.*)\\): $", "$1"));
        var session = System.getenv("CLAUDE_CODE_SESSION_ID");
        if (session != null) m.put("session", session);
        var caller = uskoag.gservices.Caller.current();
        if (caller != null) { m.put("cwd", caller.workingDir()); m.put("command", caller.commandLine()); }
        return m;
    }

    private static void write(String docId, Map<String, Object> m) throws Exception {
        var f = file(docId);
        Files.createDirectories(f.getParent());
        Files.writeString(f, Out.GSON.toJson(m) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        Out.info("journal: r" + m.get("revBefore") + " → r" + m.get("revAfter") + " recorded in " + f);
    }

    static List<JsonObject> entries(String docId) {
        var f = file(docId);
        if (!Files.exists(f)) return List.of();
        try {
            return Files.readAllLines(f).stream().filter(l -> !l.isBlank()).map(l -> JsonParser.parseString(l).getAsJsonObject()).toList();
        } catch (Exception e) {
            Out.error("journal unreadable, attribution falls back to Drive only: " + e.getMessage());
            return List.of();
        }
    }
}
