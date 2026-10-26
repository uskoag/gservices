package uskoag.gservices.gdocsmon;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** uskoag-gdocsmon: CLI verbs, plus `daemon`, which launches the tray app (through a plain main, the shaded-jar JavaFX rule). */
public final class Main {

    private Main() {}

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        var a = new ArrayList<>(List.of(args));
        if (a.isEmpty() || a.contains("--help") || a.contains("-h") || a.get(0).equals("help")) { help(); return; }
        var verb = a.remove(0);
        try {
            switch (verb) {
                case "daemon" -> javafx.application.Application.launch(MonApp.class);
                case "watch" -> watch(a);
                case "unwatch" -> print(ensured("/unwatch", body("doc", docId(req(a))), 10));
                case "notify" -> notifyUser(a);
                case "relay" -> relay(a);
                case "status" -> status();
                case "stop" -> { if (Client.alive()) Client.call("/stop", new JsonObject(), 5); System.out.println("stopped"); }
                default -> { System.err.println("unknown verb: " + verb); help(); System.exit(1); }
            }
        } catch (Exception e) {
            System.err.println("error: " + (e.getMessage() == null ? e : e.getMessage()));
            System.exit(1);
        }
    }

    private static void watch(List<String> a) throws Exception {
        var b = new JsonObject();
        var acct = val(a, "-e", "--email");
        if (acct != null) b.addProperty("account", acct);
        var every = val(a, "--every");
        if (every != null) b.addProperty("every", Integer.parseInt(every));
        var trigger = val(a, "--trigger");
        if (trigger != null) b.addProperty("trigger", trigger);
        b.addProperty("doc", docId(req(a)));
        print(ensured("/watch", b, 10));
    }

    private static void notifyUser(List<String> a) throws Exception {
        var doc = val(a, "--doc");
        var b = body("text", String.join(" ", a));
        if (doc != null) b.addProperty("doc", docId(doc));
        print(ensured("/notify", b, 10));
    }

    /** Run from a Claude Code session, in the background: attaches that session to the doc and delivers into it. */
    private static void relay(List<String> a) throws Exception {
        var acct = val(a, "-e", "--email");
        var every = val(a, "--every");
        var trigger = val(a, "--trigger");
        var doc = val(a, "--doc");
        if (doc == null) doc = req(a);
        Relay.run(docId(doc), acct, every, trigger);
    }

    private static void status() throws Exception {
        if (!Client.alive()) { System.out.println("no daemon running"); return; }
        var r = Client.call("/status", new JsonObject(), 10);
        System.out.println("pid " + r.get("pid") + "   relays polling: " + r.get("relaysPolling"));
        for (var k : List.of("watches", "attachments", "pending", "approved")) {
            var arr = r.getAsJsonArray(k);
            System.out.println(k + " (" + arr.size() + ")");
            for (var x : arr) System.out.println("  " + x.getAsString());
        }
    }

    private static JsonObject ensured(String path, JsonObject b, long t) throws Exception { Client.ensure(); return Client.call(path, b, t); }

    private static void print(JsonObject o) { System.out.println(o); }

    private static JsonObject body(String k, String v) { var b = new JsonObject(); b.addProperty(k, v); return b; }

    static String docId(String s) {
        var m = java.util.regex.Pattern.compile("/d/([A-Za-z0-9_-]{20,})").matcher(s);
        return m.find() ? m.group(1) : s.strip();
    }

    private static String val(List<String> a, String... names) {
        for (var n : names) {
            var i = a.indexOf(n);
            if (i >= 0 && i + 1 < a.size()) { var v = a.get(i + 1); a.remove(i + 1); a.remove(i); return v; }
        }
        return null;
    }

    private static String req(List<String> a) {
        if (a.isEmpty()) throw new IllegalArgumentException("<doc> is required");
        return a.remove(0);
    }

    private static void help() throws Exception {
        try (var in = Main.class.getResourceAsStream("/gdocsmon-help.txt")) { System.out.print(new String(in.readAllBytes(), StandardCharsets.UTF_8)); }
    }
}
