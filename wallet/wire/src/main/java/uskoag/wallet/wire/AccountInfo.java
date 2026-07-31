package uskoag.wallet.wire;

import java.util.List;

/**
 * One row of the account inventory the wallet makes explicit.
 *
 * <p>Today this is implicit — scattered across directories, discoverable only by listing folders, with
 * login state unknowable because tokens are encrypted per key. Being able to see it at all is a
 * usability win independent of any security property.
 */
public record AccountInfo(
        String email,
        String profile,
        boolean hasClientSecret,
        boolean hasRefreshToken,
        List<String> scopes,
        long lastUsed,
        long addedAt) {
}
