package uskoag.gservices.slides;

import java.util.List;

public final class VerbPerms {

    private VerbPerms() {}

    static void list() {
        var rows = GSlidesConfig.describe();
        if (rows.isEmpty()) Out.data("(allowlist empty -- add one with: uskoag-gslides grant --write <deck> \"<name>\")");
        else rows.forEach(Out::data);
    }

    static void grant(List<String> a) throws Exception {
        var read = Args.flag(a, "--read");
        var write = Args.flag(a, "--write");
        if (read == write) Out.die("grant: pass exactly one of --read or --write");
        var deck = Deck.presId(Args.req(a, "<deck>"));
        var name = Args.pos(a);
        Args.noneLeft(a, "grant");
        GSlidesConfig.grant(deck, name, write);
    }

    static void revoke(List<String> a) throws Exception {
        var deck = Deck.presId(Args.req(a, "<deck>"));
        Args.noneLeft(a, "revoke");
        GSlidesConfig.revoke(deck);
    }
}
