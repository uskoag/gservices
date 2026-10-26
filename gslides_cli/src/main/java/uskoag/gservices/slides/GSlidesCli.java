package uskoag.gservices.slides;

import java.util.ArrayList;
import java.util.List;
import uskoag.gservices.comments.AiSign;
import uskoag.gservices.comments.Identity;

/**
 * uskoag-gslides -- Google Slides from the command line.
 *
 * Adding a verb is one switch line plus one Verb* class; the dispatcher stays small on
 * purpose. Global flags are stripped before the verb sees its arguments.
 */
public class GSlidesCli {

    public static void main(String[] args) {
        Out.initStreams();
        // What the wallet's approval dialog will show — the directory this ran in and this command. The
        // only route those facts have, since Windows does not hand a JVM its own argv.
        uskoag.gservices.Caller.record("uskoag-gslides", args);
        var a = new ArrayList<>(List.of(args));

        Out.verbose = Args.flag(a, "-v", "--verbose");
        Out.quiet = Args.flag(a, "--quiet");
        Api.dryRun = Args.flag(a, "--dry-run", "--dry");
        var wantsHelp = Args.flag(a, "--help", "-h");
        // -e wins; without it the AI account from identity.json (shared with uskoag-gdocscli), when one is set
        Auth.email = Identity.acting(Args.val(a, "--email", "-e"));
        Identity.reading = () -> Auth.email;
        AiSign.name = Args.val(a, "--ai-name");
        AiSign.off = Args.flag(a, "--no-ai-sign");
        // --key/-k is still consumed, and still ignored. There is no app-key any more; a script that
        // passes one should not die on an unrecognised flag, but neither should it be led to believe
        // the value did anything.
        if (Args.val(a, "--key", "-k") != null) {
            Out.info("--key is ignored: this tool holds no credential and the wallet decides access");
        }

        if (wantsHelp) { GSlidesHelp.print(System.out); return; }
        if (a.isEmpty()) { GSlidesHelp.print(System.err); System.exit(1); }

        var verb = a.remove(0).toLowerCase();
        try {
            switch (verb) {
                case "help" -> GSlidesHelp.print(System.out);
                case "listperms" -> VerbPerms.list();
                case "grant" -> VerbPerms.grant(a);
                case "revoke" -> VerbPerms.revoke(a);
                case "reauth" -> Auth.reauth();

                case "describe" -> VerbDescribe.run(a);
                case "get" -> VerbGet.run(a);
                case "runs" -> VerbRuns.run(a);
                case "export" -> VerbExport.run(a);

                case "deck" -> VerbDeck.run(a);
                case "page" -> VerbPage.run(a);
                case "create" -> VerbCreate.run(a);
                case "delete" -> VerbDelete.run(a);
                case "geom" -> VerbGeom.run(a);

                case "text" -> VerbText.run(a);
                case "style" -> VerbStyle.run(a);
                case "para" -> VerbPara.run(a);
                case "notes" -> VerbNotes.run(a);

                case "image" -> VerbImage.run(a);
                case "background" -> VerbBackground.run(a);
                case "imagebg" -> VerbImageBg.run(a);
                case "fill" -> VerbFill.run(a);

                case "comments" -> VerbComments.run(a);
                case "comment" -> VerbComment.run(a);
                case "identity" -> VerbIdentityShow.run(a);
                case "suggestions", "accept", "reject" -> Out.die(verb + ": Google Slides has no suggested edits (no Suggesting mode in the editor, nothing in the API)."
                        + " Propose a change as a comment instead: comment add <deck> --on <elem> --find \"<old>\" --text \"suggest: <new>\"");

                default -> {
                    Out.error("unknown command: " + verb);
                    GSlidesHelp.print(System.err);
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            Out.fail(e);
        }
    }
}
