package uskoag.gservices;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the caller is actually doing, in the words that let a person judge an approval.
 *
 * <p>Two facts:
 *
 * <ul>
 * <li><b>The working directory</b>, which names the project. Free — the JVM sets {@code user.dir} from the
 *     process working directory — but only for a tool that does its own work. A daemon serving somebody
 *     else's invocation has to be told; see {@link #adopt}.
 * <li><b>The command line</b>, which the tool already has: it is the {@code String[] args} handed to
 *     {@code main}. What is NOT available is any way to go and fetch it afterwards —
 *     {@code ProcessHandle.current().info()} returns {@code arguments() = null} and
 *     {@code commandLine() = empty} on Windows, even for the calling process itself, so the old code that
 *     asked the process table printed "(unknown)" on every approval dialog this project ever showed.
 *     Nothing is scraped now; the tool states what it was given.
 * </ul>
 *
 * <p><b>Call {@link #record} before the argument parser runs.</b> Several of these tools consume their
 * arguments destructively — {@code removeFlag} on a mutable list, picocli binding into fields — so a
 * capture taken afterwards would be missing exactly the flags that say what the invocation was going to do.
 *
 * <p>What comes out is a reconstructed command rather than a verbatim one, and it is worth knowing the
 * difference. Java's {@code args} has no {@code argv[0]}, so the program name is supplied by each tool as
 * a literal — the exe name somebody would type, not the main class the JVM was handed. Arguments
 * containing spaces are re-quoted. So it reads as the command that was run and is not a byte-for-byte copy
 * of the shell's line; for judging an approval that is the more useful of the two anyway.
 *
 * <p><b>This is legibility, not authentication.</b> A declaration is self-reported and there is no way to
 * check it — the same objection that ended the per-tool-contract idea. Anything wanting a wide grant can
 * declare a plausible command line, so nothing here is a control and nothing downstream may treat it as
 * one. What it buys is a person being able to answer the question instead of guessing at an id.
 *
 * <p>Costs nothing when no wallet is installed: this is plain data with no dependency on anything.
 */
public final class Caller {

    /**
     * @param declared true only when a tool stated these explicitly. A value recovered from
     *                 {@code sun.java.command} or the process table is still worth showing and is still
     *                 not a declaration.
     */
    public record Facts(String workingDir, String commandLine, boolean declared) {
    }

    /** Long enough for a real batch invocation, bounded so a dialog cannot be flooded. */
    public static final int MAX_COMMAND = 4000;

    /**
     * An option whose value must never be shown or logged.
     *
     * <p>Secrets are not supposed to be on argv at all — that premise is the reason this whole wallet
     * exists — but the audit file is the wrong place to find out somebody broke the rule, since it is
     * durable and readable by anyone who opens it. So the value after a matching option is replaced
     * before the string leaves this process.
     */
    private static final String[] SECRET_OPTIONS = {"key", "secret", "token", "pass", "cred", "auth"};

    private static volatile Facts process;

    /**
     * Plain {@link ThreadLocal} and not inheritable, on purpose. It is pushed and popped around one
     * request, and an inheritable one would be captured by any thread created inside that window —
     * including a pool thread that then outlives it and reports one caller's invocation as another's.
     */
    private static final ThreadLocal<Facts> SCOPED = new ThreadLocal<>();

    private Caller() {
    }

    /**
     * Called as the first statement of a tool's {@code main}. Everything this process asks the wallet for
     * afterwards is attributed to this invocation.
     *
     * @param program how the tool is invoked, e.g. {@code uskoag-gsheetscli}. The exe name rather than
     *                the main class, because that is what somebody would have to type to reproduce it.
     */
    public static void record(String program, String... args) {
        process = new Facts(cwd(), compose(program, args), true);
    }

    /**
     * For a resident daemon running somebody else's invocation, which is the case
     * {@code uskoag-gdrivecli} presents: the CLI is thin and the daemon is the process that reaches the
     * wallet, so the daemon's own directory and argv are true and useless.
     *
     * <p>Thread-scoped and closed explicitly, so two concurrent requests cannot read each other's.
     *
     * @return a handle to close when the request ends; safe in a try-with-resources
     */
    public static Scope adopt(String workingDir, String commandLine) {
        var was = SCOPED.get();
        var dir = blank(workingDir) ? null : oneLine(workingDir, 400);
        var cmd = blank(commandLine) ? null : oneLine(commandLine, MAX_COMMAND);
        // Nothing usable forwarded is not a reason to adopt a half-fact: leave what was there, and let it
        // arrive undeclared, which is the honest description of an older client that said nothing.
        if (dir == null && cmd == null) return () -> SCOPED.set(was);
        SCOPED.set(new Facts(dir, cmd, true));
        return () -> {
            if (was == null) SCOPED.remove();
            else SCOPED.set(was);
        };
    }

    /** A close that cannot fail, so a try-with-resources over it needs no catch. */
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    /**
     * The best available account of this invocation. Never null and never throws — a fact that could not
     * be established must not be able to stop the work it describes.
     *
     * <p>Falls back to {@code sun.java.command}, which carries the main class and the arguments and is
     * set by the ordinary {@code java} launcher. That is worth showing and is deliberately NOT a
     * declaration: it is missing entirely under an embedded JVM, it names a class rather than the command
     * anybody typed, and marking it declared would quietly claim a tool had been migrated when it had not.
     */
    public static Facts current() {
        var scoped = SCOPED.get();
        if (scoped != null) return scoped;
        var p = process;
        if (p != null) return p;
        return new Facts(cwd(), scraped(), false);
    }

    private static String scraped() {
        try {
            var jc = System.getProperty("sun.java.command");
            if (!blank(jc)) return redactAll(oneLine(jc, MAX_COMMAND));
            return ProcessHandle.current().info().commandLine().map(s -> oneLine(s, MAX_COMMAND)).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static String cwd() {
        try {
            var d = System.getProperty("user.dir");
            return blank(d) ? null : oneLine(d, 400);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The program and its arguments as one line somebody could retype: an argument containing a space or
     * a quote is quoted, and any argument that is a secret's value is replaced first.
     */
    static String compose(String program, String... args) {
        var out = new StringBuilder(blank(program) ? "(unnamed tool)" : program.trim());
        if (args != null) {
            for (var a : redact(args)) out.append(' ').append(quoted(a));
        }
        return oneLine(out.toString(), MAX_COMMAND);
    }

    /**
     * Replaces the value of any option whose name looks like a secret, in both spellings:
     * {@code --app-key VALUE} and {@code --app-key=VALUE}.
     */
    static List<String> redact(String... args) {
        var out = new ArrayList<String>(args.length);
        var hide = false;
        for (var raw : args) {
            var a = raw == null ? "" : raw;
            if (hide && !a.startsWith("-")) {
                out.add("***");
                hide = false;
                continue;
            }
            hide = false;
            var eq = a.indexOf('=');
            if (a.startsWith("-") && eq > 0 && secretish(a.substring(0, eq))) {
                out.add(a.substring(0, eq + 1) + "***");
            } else {
                out.add(a);
                // Only an option can introduce a value to hide. A bare word that happens to contain
                // "key" is a filename far more often than it is a secret.
                hide = a.startsWith("-") && secretish(a);
            }
        }
        return out;
    }

    private static String redactAll(String line) {
        return String.join(" ", redact(line.split(" ")));
    }

    private static boolean secretish(String option) {
        var o = option.toLowerCase(Locale.ROOT);
        for (var s : SECRET_OPTIONS) if (o.contains(s)) return true;
        return false;
    }

    private static String quoted(String a) {
        return a.isEmpty() || a.indexOf(' ') >= 0 || a.indexOf('"') >= 0 ? "\"" + a.replace("\"", "\\\"") + "\"" : a;
    }

    /**
     * Printable, single-line, bounded — the same treatment {@code SessionId.label} gives a session name and
     * for the same reason. This string is rendered in a window somebody is about to make a security
     * decision in, and a caller able to inject newlines into it can push the real question off the screen.
     */
    private static String oneLine(String raw, int max) {
        var clean = raw.replaceAll("[\\p{Cntrl}\\p{Cc}\\p{Cf}]", " ").replaceAll("\\s{2,}", " ").trim();
        if (clean.isEmpty()) return null;
        return clean.length() <= max ? clean : clean.substring(0, max - 1) + "…";
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
