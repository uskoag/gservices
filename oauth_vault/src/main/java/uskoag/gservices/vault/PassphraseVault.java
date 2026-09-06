package uskoag.gservices.vault;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import uskoag.gservices.EncryptedFileDataStoreFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Refresh tokens encrypted at rest under a passphrase, with several named identities per account.
 *
 * <p>The passphrase goes through {@link Kdf} against a random per-installation salt kept in
 * {@code vault.json}; the resulting key is handed to {@link EncryptedFileDataStoreFactory} as its app-key.
 * Passing the raw passphrase there would also work, but that class names its folder after {@code md5(appKey)},
 * which would leave an MD5 of a human-chosen password on disk - under exactly the infostealer that encrypting
 * the token is meant to defeat. Here the folder is the fixed word {@code tokens} and the only
 * password-derived artefact on disk is the verifier.
 *
 * <p>There is no recovery: the key exists only in the operator's head and in this process's memory. Losing
 * it means deleting the tokens and authorizing again, which is two browser clicks.
 *
 * <p>No UI. The caller supplies the passphrase and, for {@link #login}, a consumer that shows the consent
 * URL - a URL which is byte-identical for every identity, so whatever displays it must name the identity
 * alongside or the operator cannot tell which login they are completing.
 *
 * <pre>
 *   &lt;dir&gt;/vault.json                salt + verifier (nothing secret)
 *   &lt;dir&gt;/&lt;account&gt;/tokens/         encrypted refresh tokens, keyed by identity
 * </pre>
 */
public final class PassphraseVault {

    public static final String VAULT_FILE = "vault.json", TOKENS_DIR = "tokens", STORE_ID = "StoredCredential",
        DEFAULT_ACCOUNT = "default";

    static final ObjectMapper OM = new ObjectMapper();

    public final Path dir;
    final String appKey;
    final NetHttpTransport transport;
    final ClientSecretsSource clients;
    final List<String> scopes;

    PassphraseVault(Path dir, String appKey, NetHttpTransport transport, ClientSecretsSource clients, List<String> scopes) {
        this.dir = dir;
        this.appKey = appKey;
        this.transport = transport;
        this.clients = clients;
        this.scopes = List.copyOf(scopes);
    }

    public static boolean enrolled(Path dir) { return Files.isRegularFile(dir.resolve(VAULT_FILE)); }

    /**
     * Derive the key, check it against the stored verifier, enrolling a fresh salt when this installation has
     * none. The passphrase array is zeroed before returning, whatever the outcome.
     */
    public static VaultUnlock unlock(Path dir, char[] passphrase, ClientSecretsSource clients, List<String> scopes) {
        if (passphrase == null || passphrase.length == 0) return VaultUnlock.of(UnlockStatus.DECLINED, null);
        try {
            Files.createDirectories(dir);
            var f = dir.resolve(VAULT_FILE);
            byte[] salt;
            var stored = (String) null;
            if (Files.isRegularFile(f)) {
                var n = OM.readTree(Files.readAllBytes(f));
                salt = Base64.getDecoder().decode(n.path("salt").asText());
                stored = n.path("verifier").asText(null);
            } else salt = Kdf.salt();

            var key = Kdf.derive(passphrase, salt);
            var fresh = stored == null;
            if (!fresh && !Kdf.verifies(key, stored)) return VaultUnlock.of(UnlockStatus.WRONG_PASSPHRASE, null);
            if (fresh) {
                var o = OM.createObjectNode();
                o.put("salt", Base64.getEncoder().encodeToString(salt));
                o.put("iterations", Kdf.ITERATIONS);
                o.put("verifier", Kdf.makeVerifier(key));
                o.put("note", "Salt and verifier only. No token, no passphrase, nothing secret is in this file.");
                Files.write(f, OM.writerWithDefaultPrettyPrinter().writeValueAsBytes(o));
            }
            return VaultUnlock.opened(new PassphraseVault(dir, Kdf.hex(key),
                GoogleNetHttpTransport.newTrustedTransport(), clients, scopes), fresh);
        } catch (Exception e) {
            return VaultUnlock.of(UnlockStatus.FAILED, String.valueOf(e));
        } finally {
            Arrays.fill(passphrase, '\0');
        }
    }

    /** Account folders holding a token store, or {@link #DEFAULT_ACCOUNT} when there are none yet. */
    public static List<String> accounts(Path dir) {
        var found = new TreeSet<String>();
        if (Files.isDirectory(dir)) {
            try (var s = Files.list(dir)) {
                s.filter(Files::isDirectory).filter(p -> Files.isDirectory(p.resolve(TOKENS_DIR)))
                 .forEach(p -> found.add(p.getFileName().toString()));
            } catch (IOException ignore) { }
        }
        if (found.isEmpty()) found.add(DEFAULT_ACCOUNT);
        return List.copyOf(found);
    }

    /**
     * Which identities hold a token, WITHOUT the passphrase - the key set is filenames, not ciphertext, so
     * nothing is decrypted. It lets a startup screen say what is set up before anyone has typed anything.
     */
    public static Set<String> identitiesOnDisk(Path dir, String account) {
        try {
            var tokens = dir.resolve(account).resolve(TOKENS_DIR);
            if (!Files.isDirectory(tokens)) return Set.of();
            return new EncryptedFileDataStoreFactory(tokens.toFile(), "unused-no-decryption-happens")
                .getDataStore(STORE_ID).keySet();
        } catch (Exception e) { return Set.of(); }
    }

    public String resolveAccount(String wanted) {
        var all = accounts(dir);
        if (wanted != null && !wanted.isBlank()) return all.contains(wanted.trim()) ? wanted.trim() : null;
        return all.isEmpty() ? null : all.get(0);
    }

    public Set<String> storedIdentities(String account) {
        try { return store(account).getDataStore(STORE_ID).keySet(); }
        catch (Exception e) { return Set.of(); }
    }

    /** An already-authorized identity. Never opens a browser, never prompts. */
    public IdentityToken token(String account, String identity) {
        if (account == null) return IdentityToken.missing(identity);
        try {
            if (!storedIdentities(account).contains(identity)) return IdentityToken.missing(identity);
            var c = flow(account).loadCredential(identity);
            return c == null ? IdentityToken.missing(identity) : IdentityToken.authorized(identity, c);
        } catch (Exception e) {
            return IdentityToken.unreadable(identity, String.valueOf(e.getMessage()));
        }
    }

    /**
     * Interactive authorization. {@code showUrl} receives the consent URL instead of it being pushed at the
     * default browser, which is routinely not the one the operator is signed into.
     */
    public IdentityToken login(String account, String identity, int port, Consumer<String> showUrl) {
        try {
            var receiver = new LocalServerReceiver.Builder().setPort(port).build();
            var app = new AuthorizationCodeInstalledApp(flow(account), receiver) {
                @Override protected void onAuthorization(AuthorizationCodeRequestUrl url) {
                    if (showUrl != null) showUrl.accept(url.build());
                }
            };
            return IdentityToken.authorized(identity, app.authorize(identity));
        } catch (Exception e) {
            return IdentityToken.unreadable(identity, String.valueOf(e.getMessage()));
        }
    }

    public boolean forget(String account, String identity) {
        try {
            var ds = store(account).getDataStore(STORE_ID);
            if (!ds.containsKey(identity)) return false;
            ds.delete(identity);
            return true;
        } catch (Exception e) { return false; }
    }

    /** Identities with no refresh token are skipped: they would install and then fail on first use. */
    public List<BundleEntry> exportTokens(String account, Collection<String> identities) throws IOException {
        var ds = store(account).getDataStore(STORE_ID);
        var out = new ArrayList<BundleEntry>();
        for (var id : identities) {
            try {
                if (!(ds.get(id) instanceof StoredCredential sc)) continue;
                if (sc.getRefreshToken() == null || sc.getRefreshToken().isBlank()) continue;
                out.add(new BundleEntry(id, sc.getRefreshToken(), sc.getAccessToken(), sc.getExpirationTimeMilliseconds(), null));
            } catch (Exception ignore) { }
        }
        return out;
    }

    /**
     * Rebuild credentials into this machine's store, encrypted under THIS vault's passphrase. Written
     * directly rather than through the authorization flow: a refresh token plus the client is already a
     * complete credential, and the library exchanges it for an access token on first use.
     */
    public int importTokens(String account, List<BundleEntry> entries) throws IOException {
        var ds = store(account).getDataStore(STORE_ID);
        var n = 0;
        for (var e : entries) {
            if (e.refreshToken() == null || e.refreshToken().isBlank()) continue;
            var sc = new StoredCredential();
            sc.setRefreshToken(e.refreshToken());
            sc.setAccessToken(e.accessToken());
            sc.setExpirationTimeMilliseconds(e.expiresAtMs());
            ds.set(e.identity(), sc);
            n++;
        }
        return n;
    }

    public String clientId() {
        try {
            var d = clients.load().getDetails();
            return d == null ? null : d.getClientId();
        } catch (Exception e) { return null; }
    }

    public Credential credentialOrNull(String account, String identity) {
        return token(account, identity).credential();
    }

    EncryptedFileDataStoreFactory store(String account) throws IOException {
        var tokens = dir.resolve(account).resolve(TOKENS_DIR);
        Files.createDirectories(tokens);
        return new EncryptedFileDataStoreFactory(tokens.toFile(), appKey);
    }

    GoogleAuthorizationCodeFlow flow(String account) throws IOException {
        var builder = new GoogleAuthorizationCodeFlow.Builder(transport, GsonFactory.getDefaultInstance(),
            clients.load(), scopes).setDataStoreFactory(store(account)).setAccessType("offline");
        return new GoogleAuthorizationCodeFlow(builder) {
            /**
             * Forces the account and identity chooser every time. Without a prompt Google reuses the session:
             * a second identity silently binds to the first one's channel, and - worse, because it is silent -
             * a re-grant returns NO refresh token, so the stored credential cannot be refreshed or exported.
             */
            @Override public GoogleAuthorizationCodeRequestUrl newAuthorizationUrl() {
                var url = super.newAuthorizationUrl();
                url.set("prompt", "select_account consent");
                return url;
            }
        };
    }

    static GoogleClientSecrets load(InputStreamReader r) throws IOException {
        return GoogleClientSecrets.load(GsonFactory.getDefaultInstance(), r);
    }

    static InputStreamReader utf8(java.io.InputStream in) {
        return new InputStreamReader(in, StandardCharsets.UTF_8);
    }
}
