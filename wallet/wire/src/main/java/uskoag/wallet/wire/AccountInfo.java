package uskoag.wallet.wire;

import java.util.List;

/**
 * One row of the account inventory the wallet makes explicit.
 *
 * <p>Today this is implicit — scattered across directories, discoverable only by listing folders, with
 * login state unknowable because tokens are encrypted per key. Being able to see it at all is a
 * usability win independent of any security property.
 *
 * @param scopes everything ever consented for this account, across every tool, as one union
 */
public record AccountInfo(
        String email,
        String org,
        boolean hasRefreshToken,
        List<String> scopes,
        List<String> readyProfiles,
        long lastUsed,
        long addedAt) {
}
