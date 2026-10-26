package uskoag.gservices.gdocsmon;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

/**
 * Polls each watched document by running the already-tested `uskoag-gdocscli comments|suggestions --awaiting
 * --json` as a child process (java -jar, not the .exe: jr suppresses stdout when it finds no console).
 * Also the idle clock: nothing to watch, nothing pending, nobody waiting for 5 minutes, and the daemon exits.
 */
final class Poller {

    static final long IDLE_EXIT_MS = 5 * 60_000L;

    private static final ScheduledExecutorService tick = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "gdocsmon-tick"));
    private static final ExecutorService work = Executors.newFixedThreadPool(2, r -> daemon(r, "gdocsmon-poll"));

    private Poller() {}

    static void start(Runnable exit) {
        tick.scheduleWithFixedDelay(() -> {
            try {
                State.sweep();
                var now = System.currentTimeMillis();
                for (var w : State.watchList()) {
                    State.touch();
                    if (!w.busy && now - w.lastPoll >= w.every * 1000L) { w.busy = true; work.submit(() -> poll(w)); }
                }
                if (State.idle() && now - State.lastActive > IDLE_EXIT_MS) { State.note("idle for 5 minutes, exiting"); exit.run(); }
            } catch (Throwable t) { State.note("tick failed: " + t); }
        }, 2, 5, TimeUnit.SECONDS);
    }

    static void pollNow(Watch w) { w.lastPoll = 0; }

    private static void poll(Watch w) {
        try {
            var fired = 0;
            for (var kind : List.of("comment", "suggestion")) {
                for (var t : run(w, kind + "s")) {
                    var e = Rules.classify(w, kind, t.getAsJsonObject());
                    if (e != null) { State.add(e); fired++; }
                }
            }
            if (w.lastError != null) State.note("poll recovered " + w.doc);
            w.lastError = null;
            if (fired > 0) State.note(fired + " new event(s) from " + w.doc);
        } catch (Throwable t) {
            var msg = t.getMessage() == null ? t.toString() : t.getMessage();
            if (!msg.equals(w.lastError)) State.note("poll failed " + w.doc + ": " + msg);
            w.lastError = msg;
        } finally {
            w.polls++;
            w.lastPoll = System.currentTimeMillis();
            w.busy = false;
            State.changed();
        }
    }

    private static JsonArray run(Watch w, String verb) throws Exception {
        var cmd = new ArrayList<>(List.of(Home.java().toString(), "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "--enable-native-access=ALL-UNNAMED",
                "-jar", Home.cliJar().toString()));
        if (w.account != null) cmd.addAll(List.of("-e", w.account));
        cmd.addAll(List.of(verb, w.doc, "--awaiting", "--json"));
        var pb = new ProcessBuilder(cmd).redirectError(Home.POLL_ERR.toFile());
        pb.environment().put("UKAG_WALLET_SESSION", "gdocs monitor");
        Files.createDirectories(Home.DIR);
        var p = pb.start();
        var out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(180, TimeUnit.SECONDS)) { p.destroyForcibly(); throw new IllegalStateException(verb + " timed out after 180s"); }
        if (p.exitValue() != 0) throw new IllegalStateException(verb + " exit " + p.exitValue() + ": " + tail());
        var s = out.strip();
        var i = s.indexOf('[');
        if (i < 0) throw new IllegalStateException(verb + " printed no JSON array: " + Ev.clip(s, 200));
        return JsonParser.parseString(s.substring(i)).getAsJsonArray();
    }

    private static String tail() {
        try {
            var lines = Files.readAllLines(Home.POLL_ERR, StandardCharsets.UTF_8);
            return String.join(" | ", lines.subList(Math.max(0, lines.size() - 3), lines.size()));
        } catch (Exception e) { return "(no stderr)"; }
    }

    private static Thread daemon(Runnable r, String name) { var t = new Thread(r, name); t.setDaemon(true); return t; }
}
