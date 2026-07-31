package uskoag.gservices.slides;

import java.util.List;

public final class VerbPerms {

    private VerbPerms() {}

    static void list() {
        GSlidesConfig.listperms();
    }

    /**
     * Argument parsing is kept deliberately loose here, unlike before. The point of these three is now
     * to print the replacement command, and refusing on a malformed flag would turn a redirect back
     * into the unrecognised-command error it exists to avoid.
     */
    static void grant(List<String> a) {
        var write = !Args.flag(a, "--read");
        Args.flag(a, "--write");
        var deck = a.isEmpty() ? null : Deck.presId(Args.pos(a));
        GSlidesConfig.grant(deck, null, write);
    }

    static void revoke(List<String> a) {
        GSlidesConfig.revoke(a.isEmpty() ? null : Deck.presId(Args.pos(a)));
    }
}
