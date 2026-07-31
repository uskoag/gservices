package uskoag.wallet.daemon;

import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import uskoag.gservices.OAuthToken;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Migration, which replaces rotation and is better.
 *
 * <p>Hand it an old app-key once: it decrypts the existing {@code tokens_<md5>} store, re-encrypts what
 * it finds into the keyring, and can then delete the old directory. No account re-consents, across any
 * number of orgs.
 *
 * <p>The useful consequence is that once those stores are gone, an app-key that has leaked into a
 * transcript stops being a secret — a passphrase that decrypts nothing is not a passphrase. That is
 * cleaner than rotating, which would mean re-consenting every account everywhere.
 */
public final class ImportOldStore {

    private ImportOldStore() {
    }

    public static Path defaultRoot() {
        return Path.of(System.getProperty("user.home"), "uskoag", "gdrive_gdocs_auth");
    }

    /** Every account directory that carries a credentials.json, whether or not it has a token yet. */
    public static List<String> accounts(Path root) {
        var out = new ArrayList<String>();
        if (!Files.isDirectory(root)) return out;
        try (var dirs = Files.list(root)) {
            dirs.filter(Files::isDirectory)
                    .filter(d -> Files.exists(d.resolve("credentials.json")))
                    .forEach(d -> out.add(d.getFileName().toString()));
        } catch (IOException ignored) {
        }
        out.sort(String::compareToIgnoreCase);
        return out;
    }

    /**
     * Reads one account's stored credential using the old app-key and returns it ready for the keyring.
     * Empty when that account was never logged in with this key, which is a normal outcome, not an error.
     */
    public static CredentialRecord read(Path root, String account, String appKey, String profile,
                                        List<String> scopes) throws IOException {
        var dir = root.resolve(account);
        var credentialsJson = Files.readString(dir.resolve("credentials.json"));

        var rest = scopes.subList(1, scopes.size()).toArray(String[]::new);
        var token = OAuthToken.oauthToken("uskoag-wallet-import", appKey, scopes.getFirst(), rest)
                .allCredsDir(root)
                .credential(account);

        var stored = token.loadStoredCredential(new NetHttpTransport());
        if (stored == null || stored.getRefreshToken() == null) return null;

        try (var reader = new InputStreamReader(Files.newInputStream(dir.resolve("credentials.json")))) {
            var secrets = GoogleClientSecrets.load(GsonFactory.getDefaultInstance(), reader);
            return OAuthRunner.record(account, profile, credentialsJson, scopes, secrets, stored);
        }
    }
}
