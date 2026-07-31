package uskoag.gservices;

/** Standalone smoke test for {@link QuoteStripper} — no Google auth needed.
 *  Run: mvn exec:java -Dexec.mainClass="uskoag.gservices.QuoteStripperSmokeTest" -Dexec.classpathScope=test */
public class QuoteStripperSmokeTest {

    public static void main(String[] args) {
        show("Gmail-style \"On ... wrote:\" boundary",
                String.join("\n",
                        "Sounds good, let's go with Tuesday.",
                        "",
                        "On Mon, Jul 14, 2026 at 3:02 PM Bob <bob@x.com> wrote:",
                        "> What day works for you?",
                        ">",
                        "> On Mon, Jul 14, 2026 at 1:00 PM Alice <alice@x.com> wrote:",
                        "> > Can we meet this week?"),
                "Sounds good, let's go with Tuesday.");

        show("Outlook-style From:/Sent:/To: header block",
                String.join("\n",
                        "Thanks, approved.",
                        "",
                        "From: Bob <bob@x.com>",
                        "Sent: Monday, July 14, 2026 1:00 PM",
                        "To: Alice <alice@x.com>",
                        "Subject: Budget",
                        "",
                        "Please approve the attached budget."),
                "Thanks, approved.");

        show("-----Original Message----- divider",
                String.join("\n",
                        "Confirmed.",
                        "",
                        "-----Original Message-----",
                        "From: Bob",
                        "Sent: Monday",
                        "",
                        "Please confirm."),
                "Confirmed.");

        show("no quoted history -> unchanged",
                "Just a plain reply with no history.",
                "Just a plain reply with no history.");

        var html = "<div>New reply text</div><blockquote class=\"gmail_quote\">"
                + "<div>On Mon, Bob wrote:</div><div>Old quoted text</div></blockquote>";
        var strippedHtml = QuoteStripper.stripHtml(html);
        System.out.println("### html blockquote stripped");
        System.out.println("    in:  " + html);
        System.out.println("    out: " + strippedHtml);
        System.out.println("    contains 'New reply text': " + strippedHtml.contains("New reply text"));
        System.out.println("    contains 'Old quoted text' (should be false): " + strippedHtml.contains("Old quoted text"));
        System.out.println();
    }

    static void show(String title, String input, String expected) {
        var actual = QuoteStripper.stripPlain(input);
        System.out.println("### " + title);
        System.out.println("    expected: " + expected.replace("\n", "\\n"));
        System.out.println("    actual:   " + actual.replace("\n", "\\n"));
        System.out.println("    match:    " + expected.equals(actual));
        System.out.println();
    }
}
