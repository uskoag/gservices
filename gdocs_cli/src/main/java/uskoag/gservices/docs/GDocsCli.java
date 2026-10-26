package uskoag.gservices.docs;

import uskoag.gservices.comments.Identity;
import uskoag.gservices.comments.AiSign;
import java.util.ArrayList;
import java.util.List;

/** uskoag-gdocscli — Google Docs from the command line. One switch line plus one Verb* class per verb. */
public class GDocsCli {

    public static void main(String[] args) {
        Out.initStreams();
        uskoag.gservices.Caller.record("uskoag-gdocscli", args);
        var a = new ArrayList<>(List.of(args));

        Out.verbose = Args.flag(a, "-v", "--verbose");
        Out.quiet = Args.flag(a, "--quiet");
        var wantsHelp = Args.flag(a, "--help", "-h");
        Auth.email = Identity.acting(Args.val(a, "--email", "-e"));
        Identity.reading = () -> Auth.email;
        DocRef.suggestions = Args.val(a, "--suggestions");
        AiSign.name = Args.val(a, "--ai-name");
        AiSign.off = Args.flag(a, "--no-ai-sign");

        if (wantsHelp) { help(System.out); return; }
        if (a.isEmpty()) { help(System.err); System.exit(1); }

        var verb = a.remove(0).toLowerCase();
        try {
            switch (verb) {
                case "help" -> help(System.out);
                case "tabs" -> VerbTabs.run(a);
                case "tab" -> VerbTab.run(a);
                case "footnote" -> VerbFootnote.run(a);
                case "link" -> VerbLink.run(a);
                case "toc" -> VerbToc.run(a);
                case "highlight" -> VerbHighlight.run(a);
                case "align" -> VerbAlign.run(a);
                case "history", "revisions" -> VerbHistory.run(a);
                case "identity" -> VerbIdentity.run(a);
                case "diff", "compare" -> VerbDiff.run(a);
                case "read", "get" -> VerbRead.run(a);
                case "replace", "insert", "delete", "apply" -> VerbEdit.run(verb, a);
                case "comments", "suggestions" -> VerbComments.list(verb, a);
                case "comment" -> VerbComments.run(a);
                case "write" -> VerbWrite.run(a);
                case "import" -> VerbImport.run(a);
                case "list" -> VerbList.run(a);
                case "images" -> VerbImage.list(a);
                case "image" -> VerbImage.run(a);
                case "accept", "reject", "delete-suggestion" -> VerbSuggestions.run(verb, a);
                default -> {
                    Out.error("unknown command: " + verb);
                    help(System.err);
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            Out.fail(e);
        }
    }

    static void help(java.io.PrintStream out) { out.print(Res.text("gdocs-help.txt")); }
}
