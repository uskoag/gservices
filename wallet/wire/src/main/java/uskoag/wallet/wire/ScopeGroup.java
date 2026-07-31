package uskoag.wallet.wire;

import java.util.List;

/**
 * One consent, one token, one expiry.
 *
 * <p>Separating these is not tidiness. Google expires refresh tokens for unverified apps per token, so
 * a single union token dies when its shortest-lived scope does — an unused mail grant would otherwise
 * take Sheets, Docs and Slides down with it.
 *
 * @param scopes the exact strings sent to Google, so nothing is inferred at consent time
 * @param custom true for a group the user defined by hand, which the built-in catalogue cannot cover
 */
public record ScopeGroup(String id, String label, String detail, List<String> scopes, Tier2 tier, boolean custom) {

    public static ScopeGroup of(String id, String label, String detail, Tier2 tier, String... scopes) {
        return new ScopeGroup(id, label, detail, List.of(scopes), tier, false);
    }

    public static ScopeGroup handWritten(String id, List<String> scopes) {
        return new ScopeGroup(id, id, "hand-written scope set", List.copyOf(scopes), Tier2.RESTRICTED, true);
    }

    public boolean covers(List<String> wanted) {
        return scopes.containsAll(wanted);
    }

    /** Fewer scopes wins when several groups would serve a request. Least privilege, by default. */
    public int width() {
        return scopes.size();
    }
}
