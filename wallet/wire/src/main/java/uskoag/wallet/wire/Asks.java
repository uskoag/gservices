package uskoag.wallet.wire;

import java.util.List;

/**
 * The small request and reply shapes the control verbs use. Grouped in one file because each is two
 * lines and a file apiece would be noise.
 */
public final class Asks {

    private Asks() {
    }

    public record Unlock(String passphrase) {
    }

    public record Passwd(String current, String fresh) {
    }

    /** One OAuth client for one organisation. Domains let later accounts skip naming the org at all. */
    public record AddOrg(String id, String label, String credentialsJson, List<String> domains) {
    }

    /**
     * Consent for one account. {@code scopes} is what this run needs; the wallet requests the union of
     * that and everything already granted, so widening never revokes what another tool relies on.
     */
    public record Login(String account, String org, List<String> scopes, int port) {
    }

    /**
     * Migrate existing {@code tokens_<md5>} stores. {@code profiles} is a multi-select and defaults to
     * every known tool, because one account's tokens are scattered across several tool directories and
     * picking them off one at a time was the wrong shape.
     */
    public record Import(String org, String appKey, List<String> profiles, List<String> accounts,
                         String root, boolean deleteOld) {
    }

    public record Export(String account, boolean raw) {
    }

    public record Forget(String account) {
    }

    public record Recent(int limit) {
    }

    public record Done(boolean ok, String message) {

        public static Done yes(String message) {
            return new Done(true, message);
        }

        public static Done no(String message) {
            return new Done(false, message);
        }
    }
}
