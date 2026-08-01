package uskoag.gservices.slides;

/**
 * What is left of this tool's own allowlist, which is a set of redirects and nothing else.
 *
 * <p>It used to be {@code GSlidesCli.xml}, an on-disk list of deck ids gating every verb — the same
 * shape uskoag-gsheetscli had, and retired for the same reason. There is now one enforcement point, and
 * a second one is worse than none: two lists disagree, and the moment they do, the tool is either
 * refusing work the wallet permits or permitting work the wallet would have questioned. The wallet
 * wins because it is the only one the caller cannot edit — this file sat in plain XML beside the
 * binary, so anything running as this user could grant itself a deck.
 *
 * <p>It also could not have worked here any more. The old list was keyed to a directory derived from
 * the app-key, and there is no app-key.
 *
 * <p>The verbs redirect rather than vanishing: an unrecognised command gives a script an error, a
 * redirect gives it an instruction. They are not proxied through to the wallet either, because a tool
 * that could obtain its own permission by asking on its own behalf is precisely what the arrangement
 * exists to prevent.
 */
public final class GSlidesConfig {

    private GSlidesConfig() {}

    static void listperms() {
        redirect("listperms", "uskoag-walletcli policy list");
    }

    static void grant(String deck, String name, boolean write) {
        redirect("grant", "uskoag-walletcli policy allow --api slides --resource "
                + (deck == null || deck.isBlank() ? "<presentationId>" : deck)
                + " --tier " + (write ? "mutate" : "read")
                + " --account " + (Auth.email == null ? "<email>" : Auth.email));
    }

    static void revoke(String deck) {
        redirect("revoke", "uskoag-walletcli policy list        (find the rule id, then)\n"
                + "  uskoag-walletcli policy revoke <ruleId>");
    }

    private static void redirect(String verb, String instead) {
        Out.die(verb + " has moved to the USK OAG GServices Wallet, which is now the only thing deciding"
                + " what this tool may touch — for every tool, with an expiry and an audit, instead of one"
                + " XML file per tool.\n"
                + "  Run instead:\n    " + instead + "\n"
                + "  Permissions also form just by using a deck: the first touch asks once, and the dialog"
                + " names the presentation rather than showing its id.");
    }
}
