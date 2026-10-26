package uskoag.gservices.gdocsmon;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * The "working on it" reply. Posted by the daemon itself, mechanically, the moment an event is cleared to ACT,
 * and BEFORE the event is handed to Claude, so it appears in seconds however long Claude takes. The event then
 * tells Claude to edit that post into its answer (`comment edit <doc> <postId>`), so the thread ends with one reply.
 * Signed like every AI post, so the thread stops being "awaiting" and the post never fires an event of its own.
 */
final class Placeholder {

    static final String TEXT = "Working on this…";

    private static final Pattern CREATED = Pattern.compile("created post (\\S+)");
    private static final ExecutorService pool = Executors.newFixedThreadPool(2, r -> { var t = new Thread(r, "gdocsmon-placeholder"); t.setDaemon(true); return t; });

    private Placeholder() {}

    /** Called for every event that just became APPROVED to act on. Delivery waits until this has finished, success or not. */
    static void submit(Ev e) {
        if (e.inform || e.placeholder != null) return;
        pool.submit(() -> {
            var got = "";
            try { got = post(e); State.note("placeholder " + got + " posted for " + e.id); }
            catch (Exception x) { State.note("placeholder failed for " + e.id + ": " + x.getMessage()); }
            synchronized (State.class) { e.placeholder = got; State.class.notifyAll(); }
            State.changed();
        });
    }

    private static String post(Ev e) throws Exception {
        var cmd = new ArrayList<>(List.of(Home.java().toString(), "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "--enable-native-access=ALL-UNNAMED",
                "-jar", Home.cliJar().toString()));
        if (e.account != null) cmd.addAll(List.of("-e", e.account));
        cmd.addAll(List.of("--ai-name", "Claude", "comment", "reply", e.doc, e.thread, "--text", TEXT, "--apply"));
        var pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        pb.environment().put("UKAG_WALLET_SESSION", "gdocs monitor");
        var p = pb.start();
        var out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(120, TimeUnit.SECONDS)) { p.destroyForcibly(); throw new IllegalStateException("timed out"); }
        var m = CREATED.matcher(out);
        if (p.exitValue() != 0 || !m.find()) throw new IllegalStateException("exit " + p.exitValue() + ": " + Ev.clip(out.strip(), 200));
        return m.group(1);
    }
}
