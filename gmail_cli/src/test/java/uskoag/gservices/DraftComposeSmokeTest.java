package uskoag.gservices;

import com.google.api.client.util.Base64;

import java.nio.charset.StandardCharsets;

/**
 * Standalone smoke test (no Google auth needed) for prp/09:
 * - {@link GmailCli#composeMessage} — the --to/--no-to validation shared by {@code draft} and
 *   {@code draft-update}, exercised without ever calling the Gmail API (no --reply-to here, which is
 *   the only branch that does).
 * - {@link GmailCli#plainTextToHtml} — the {@code >>}/{@code >> AI:} threaded-review coloring extended
 *   from GLOBAL_CLAUDE.md's file convention to mail bodies.
 * Run: mvn exec:java -Dexec.mainClass="uskoag.gservices.DraftComposeSmokeTest" -Dexec.classpathScope=test
 */
public class DraftComposeSmokeTest {

    public static void main(String[] args) throws Exception {
        boolean ok = true;
        ok &= checkMissingToIsRejected();
        ok &= checkNoToHoldsPlaceholder();
        ok &= checkOrdinaryToWorks();
        ok &= checkUserNoteColored();
        ok &= checkAiNoteColoredAndIndented();
        ok &= checkContinuationLineKeepsColor();
        ok &= checkPlainLineUnstyled();
        ok &= checkNoteTextIsHtmlEscaped();

        System.out.println(ok ? "ALL PASS" : "FAILURE — see above");
        if (!ok) System.exit(1);
    }

    // ---- composeMessage: --to / --no-to -------------------------------------

    private static boolean checkMissingToIsRejected() throws Exception {
        GmailCli.Args a = args("-s", "hi", "-b", "body");
        boolean threw;
        try {
            GmailCli.composeMessage(a, null);
            threw = false;
        } catch (RuntimeException e) {
            threw = e.getClass().getSimpleName().equals("DraftUsageError") && e.getMessage().contains("--to");
        }
        return report("missing --to (no --no-to) is rejected", threw);
    }

    private static boolean checkNoToHoldsPlaceholder() throws Exception {
        GmailCli.Args a = args("-s", "hi", "-b", "body", "--no-to");
        GmailCli.Composed c = GmailCli.composeMessage(a, null);
        String raw = decode(c.message.getRaw());
        boolean noRecipientHeaders = !raw.contains("\nTo:") && !raw.contains("\nCc:") && !raw.contains("\nBcc:");
        return report("--no-to holds a placeholder draft with no recipient headers", noRecipientHeaders);
    }

    private static boolean checkOrdinaryToWorks() throws Exception {
        GmailCli.Args a = args("--to", "them@x.com", "-s", "hi", "-b", "body");
        GmailCli.Composed c = GmailCli.composeMessage(a, null);
        String raw = decode(c.message.getRaw());
        return report("ordinary --to is honored in the built message", raw.contains("them@x.com"));
    }

    // ---- plainTextToHtml: >> / >> AI: coloring -------------------------------

    private static boolean checkUserNoteColored() {
        String html = GmailCli.plainTextToHtml(">> please fix paragraph 2");
        boolean pass = html.contains("color:#1a73e8") && html.contains("background-color:#e8f0fe")
                && html.contains("&gt;&gt; please fix paragraph 2");
        return report("a \">>\" line is a highlighted chip for the user's note", pass, html);
    }

    private static boolean checkAiNoteColoredAndIndented() {
        String html = GmailCli.plainTextToHtml(">> AI: done, see paragraph 2");
        boolean pass = html.contains("color:#188038") && html.contains("background-color:#e6f4ea")
                && html.contains("margin-left:1.5em") && html.contains("&gt;&gt; AI: done, see paragraph 2");
        return report("a \">> AI:\" line is a highlighted chip for Claude's reply and indented", pass, html);
    }

    private static boolean checkContinuationLineKeepsColor() {
        String html = GmailCli.plainTextToHtml(">> AI: done.\n    Also flagged the date in para 4.");
        int firstSpan = html.indexOf("color:#188038");
        boolean pass = firstSpan >= 0 && html.contains("Also flagged the date in para 4")
                && countOccurrences(html, "color:#188038") == 2;
        return report("an indented continuation line keeps the AI note's color", pass, html);
    }

    private static boolean checkPlainLineUnstyled() {
        String html = GmailCli.plainTextToHtml("Dear Sir, please find attached the response.");
        boolean pass = !html.contains("<span") && html.contains("Dear Sir, please find attached the response.");
        return report("an ordinary line carries no color span", pass, html);
    }

    private static boolean checkNoteTextIsHtmlEscaped() {
        String html = GmailCli.plainTextToHtml(">> compare <old> & <new>");
        boolean pass = html.contains("&lt;old&gt;") && html.contains("&amp;") && !html.contains("<old>");
        return report("note text is still html-escaped inside the color span", pass, html);
    }

    // ---- helpers --------------------------------------------------------------

    private static GmailCli.Args args(String... argv) {
        return new GmailCli.Args(argv);
    }

    private static String decode(String urlSafeBase64) {
        return new String(Base64.decodeBase64(urlSafeBase64), StandardCharsets.UTF_8);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0, idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) { count++; idx += needle.length(); }
        return count;
    }

    private static boolean report(String label, boolean pass) {
        System.out.println((pass ? "PASS" : "FAIL") + " - " + label);
        return pass;
    }

    private static boolean report(String label, boolean pass, String detail) {
        System.out.println((pass ? "PASS" : "FAIL") + " - " + label);
        if (!pass) System.out.println(detail);
        return pass;
    }
}
