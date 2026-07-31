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

    public record AddCredentials(String account, String profile, String credentialsJson) {
    }

    public record Login(String account, String profile, List<String> scopes, int port) {
    }

    public record Import(String root, String appKey, String profile, List<String> scopes,
                         List<String> accounts, boolean deleteOld) {
    }

    public record Export(String account, String profile, boolean raw) {
    }

    public record Forget(String account, String profile) {
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
