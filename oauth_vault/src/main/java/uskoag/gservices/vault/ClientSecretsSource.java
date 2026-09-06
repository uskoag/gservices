package uskoag.gservices.vault;

import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where the OAuth CLIENT comes from.
 *
 * <p>A client id and secret identify the application, not the account - Google's installed-app flow assumes
 * they are distributable, which is why there is a consent screen at all. Shipping one inside the jar is what
 * lets a fresh machine authorize with nothing hand-copied first.
 *
 * <p>Changing the client invalidates every token stored against the old one: a refresh token only works with
 * the client that issued it, and the failure surfaces later as {@code invalid_grant}.
 */
@FunctionalInterface
public interface ClientSecretsSource {

    GoogleClientSecrets load() throws IOException;

    /** From the classpath, e.g. {@code "/credentials.json"} packed into the application jar. */
    static ClientSecretsSource resource(String path) {
        return () -> {
            try (var in = ClientSecretsSource.class.getResourceAsStream(path)) {
                if (in == null) throw new IOException("no OAuth client on the classpath at " + path);
                return PassphraseVault.load(PassphraseVault.utf8(in));
            }
        };
    }

    static ClientSecretsSource file(Path f) {
        return () -> {
            if (!Files.isRegularFile(f)) throw new IOException("no OAuth client at " + f);
            try (var in = Files.newInputStream(f)) {
                return PassphraseVault.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            }
        };
    }

    /** The classpath copy when the build carries one, else the file. */
    static ClientSecretsSource resourceOrFile(String path, Path f) {
        return () -> {
            try (var in = ClientSecretsSource.class.getResourceAsStream(path)) {
                if (in != null) return PassphraseVault.load(PassphraseVault.utf8(in));
            }
            return file(f).load();
        };
    }
}
