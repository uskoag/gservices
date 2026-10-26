package uskoag.gservices.gdocsmon;

import com.google.gson.*;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * `relay --doc <id>`: started IN THE BACKGROUND by the Claude Code session that wants to listen to a document,
 * so it is that session's own child. It attaches (the owner approves in the tray), then long-polls the daemon
 * and writes each message into the session's inbox pipe, which Claude Code accepts from its own children only.
 * The session's inbox token never leaves this process. It exits when the attachment ends; that exit tells the session too.
 */
final class Relay {

    private Relay() {}

    static void run(String doc, String account, String every, String trigger) throws Exception {
        var sock = env("CLAUDE_CODE_MESSAGING_SOCKET");
        var token = env("CLAUDE_CODE_MESSAGING_TOKEN");
        var session = env("CLAUDE_CODE_SESSION_ID");
        Client.ensure();
        var w = new JsonObject();
        w.addProperty("doc", doc);
        if (account != null) w.addProperty("account", account);
        if (every != null) w.addProperty("every", Integer.parseInt(every));
        if (trigger != null) w.addProperty("trigger", trigger);
        Client.call("/watch", w, 10);
        var b = new JsonObject();
        b.addProperty("doc", doc);
        b.addProperty("session", session);
        b.addProperty("cwd", Path.of("").toAbsolutePath().toString());
        if (account != null) b.addProperty("account", account);
        var att = Client.call("/attach", b, 10).get("id").getAsString();
        System.err.println("attach " + att + " requested for session " + session + "; waiting for the owner's approval in the tray");
        var failures = 0;
        while (true) {
            JsonObject r;
            try {
                var q = new JsonObject();
                q.addProperty("id", att);
                q.addProperty("wait", 300);
                r = Client.call("/pull", q, 330);
                failures = 0;
            } catch (Exception e) {
                if (++failures > 20) throw new IllegalStateException("lost the gdocs monitor daemon: " + e.getMessage());
                Thread.sleep(3000);
                try { Client.ensure(); } catch (Exception ignored) {}
                continue;
            }
            for (var m : r.getAsJsonArray("messages")) {
                var o = m.getAsJsonObject();
                post(sock, token, o.get("text").getAsString());
                var id = o.get("id").getAsString();
                if (!id.isEmpty()) { var k = new JsonObject(); k.addProperty("id", id); Client.call("/ack", k, 10); }
            }
            var status = r.get("status").getAsString();
            if (status.equals(Att.DENIED) || status.equals(Att.ENDED)) {
                System.out.println("gdocs-monitor relay " + att + " for doc " + doc + " ended: " + (r.has("reason") ? r.get("reason").getAsString() : status)
                        + ". This session no longer receives this document's comments.");
                return;
            }
        }
    }

    /** One connection per message: the auth line, then a user message line (the format Claude Code's own inbox recipe uses). */
    static void post(String sock, String token, String text) throws Exception {
        var auth = new JsonObject();
        auth.addProperty("type", "auth");
        auth.addProperty("token", token);
        var msg = new JsonObject();
        msg.addProperty("role", "user");
        msg.addProperty("content", text);
        var line = new JsonObject();
        line.addProperty("type", "user");
        line.add("message", msg);
        var bytes = (auth + "\n" + line + "\n").getBytes(StandardCharsets.UTF_8);
        for (var i = 0; ; i++) {
            try (var f = new RandomAccessFile(sock, "rw")) { f.write(bytes); return; }
            catch (java.io.FileNotFoundException busy) { if (i > 20) throw busy; Thread.sleep(250); }
        }
    }

    private static String env(String k) {
        var v = System.getenv(k);
        if (v == null || v.isBlank()) throw new IllegalStateException(k + " is not set: run `relay` from inside a Claude Code session (its Bash tool), in the background");
        return v;
    }
}
