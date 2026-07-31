package uskoag.wallet.daemon;

import uskoag.wallet.wire.Needs;
import uskoag.wallet.wire.Tier;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Which of an account's tokens serves this request.
 *
 * <p>Chosen per request rather than per tool, because a tool is not one scope set: uskoag-gslides edits
 * a deck, exports it and uploads an image, and those want three different tokens. Every proxied request
 * goes to exactly one API at exactly one tier, so the narrowest sufficient token can be picked at the
 * moment it is actually known.
 *
 * <p>Narrowest first, then explicit order. The effect is that the everyday token does the everyday work
 * even when a wider one exists — so the wide one stays cold, and its use stands out in the audit
 * instead of being lost in the noise.
 */
public final class TokenPicker {

    private TokenPicker() {
    }

    public static Optional<CredentialRecord> pick(List<CredentialRecord> tokens, String api, Tier tier) {
        var needed = Needs.forRequest(api, tier);
        var wider = Needs.alternatives(api, tier);
        return tokens.stream()
                .filter(t -> t.refreshToken != null)
                .filter(t -> t.covers(needed) || t.coversAny(wider))
                .min(Comparator.comparingInt((CredentialRecord t) -> t.order)
                        .thenComparingInt(t -> t.scopes().size()));
    }

    /**
     * What to tell someone when nothing fits, in the words that say which consent to run — never a bare
     * 403, which is what the old single-token store gave you and what made this hard to diagnose.
     */
    public static String explain(String account, String api, Tier tier, List<CredentialRecord> held) {
        var needed = Needs.forRequest(api, tier);
        var groups = uskoag.wallet.wire.Groups.covering(needed);
        var suggestion = groups.isEmpty() ? "(no built-in group covers it - grant a custom scope set)"
                : groups.getFirst().id();
        return account + " has no token for " + api + "/" + tier
                + ". Needs " + String.join(", ", needed)
                + ". Holds: " + (held.isEmpty() ? "nothing" : held.stream().map(CredentialRecord::group).toList())
                + ". Run: uskoag-walletcli login " + account + " --groups " + suggestion;
    }
}
