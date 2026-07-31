package uskoag.gservices.slides;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * The output contract: stdout carries only the command's data or one SUCCESS: line,
 * stderr carries only [ERROR] (always) and [INFO] (only under -v). Keeping the two
 * channels clean is what makes the tool safe for an agent to pipe.
 */
public final class Out {

    static boolean verbose = false, quiet = false;

    /** Formulas, links and Devanagari must survive verbatim, hence no HTML escaping. */
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private Out() {}

    static void initStreams() {
        try {
            xyz.jphil.windows_console_set_unicode_output.WindowsConsoleSetUnicodeOutput.enable();
        } catch (Throwable ignored) {
            // no-op off-Windows, or on a JDK without FFM; the UTF-8 streams below still apply
        }
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
    }

    static void info(String m) { if (verbose) System.err.println("[INFO] " + m); }

    static void error(String m) { System.err.println("[ERROR] " + m); }

    static void data(String s) { System.out.println(s); }

    static void json(Object o) { System.out.println(GSON.toJson(o)); }

    /** Under --dry-run nothing was sent, so claiming SUCCESS would be a lie. */
    static void success(String m) {
        if (quiet) return;
        System.out.println(Api.dryRun ? "DRY-RUN (nothing sent): " + m : "SUCCESS: " + m);
    }

    static void die(String m) { error(m); System.exit(1); }

    /** One greppable line per failure, preferring the API's own message over the status line. */
    static void fail(Exception e) {
        if (e instanceof GoogleJsonResponseException g) {
            var code = g.getStatusCode();
            var msg = (g.getDetails() != null && g.getDetails().getMessage() != null)
                    ? g.getDetails().getMessage() : g.getStatusMessage();
            error(switch (code) {
                case 403 -> "PERMISSION DENIED (403): " + msg;
                case 404 -> "NOT FOUND (404): " + msg;
                case 401 -> "AUTH FAILURE (401): " + msg;
                case 429 -> "RATE LIMITED (429): " + msg;
                default -> "API ERROR " + code + ": " + msg;
            });
        } else {
            var msg = e.getMessage();
            error(msg != null && !msg.isEmpty() ? msg : e.getClass().getSimpleName());
        }
        if (verbose) e.printStackTrace(System.err);
        System.exit(1);
    }
}
