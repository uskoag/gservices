package uskoag.gservices.sheetcli;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import java.util.*;

/** history, snapshot, diff and protect: verbs that live outside SpreadsheetCli and build their own narrow clients. */
public final class Verbs {

    public static final Set<String> NAMES = Set.of("history", "snapshot", "diff", "protect");

    private Verbs() {}

    /** Runs the verb and returns its exit code. */
    public static int run(String verb, List<String> args, String email, boolean verbose, boolean quiet) {
        Auth.email = email;
        Out.verbose = verbose;
        Out.quiet = quiet;
        var a = new ArrayList<>(args);
        try {
            switch (verb) {
                case "history" -> VerbHistory.run(a);
                case "snapshot" -> VerbSnapshot.run(a);
                case "diff" -> VerbDiff.run(a);
                case "protect" -> VerbProtect.run(a);
                default -> Out.die("unknown verb " + verb);
            }
            return 0;
        } catch (CliError e) {
            System.err.println("[ERROR] " + e.getMessage());
            return 1;
        } catch (GoogleJsonResponseException g) {
            var msg = g.getDetails() != null && g.getDetails().getMessage() != null ? g.getDetails().getMessage() : g.getStatusMessage();
            System.err.println("[ERROR] API " + g.getStatusCode() + ": " + msg);
            if (verbose) g.printStackTrace(System.err);
            return 1;
        } catch (Exception e) {
            System.err.println("[ERROR] " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            if (verbose) e.printStackTrace(System.err);
            return 1;
        }
    }
}
