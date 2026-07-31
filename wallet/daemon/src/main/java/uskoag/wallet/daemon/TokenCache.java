package uskoag.wallet.daemon;

import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns a refresh token into a live access token, and keeps it until shortly before it expires.
 *
 * <p>This is the only place in the system where a Google access token exists, and it never leaves the
 * process: the proxy reads it to write one header. The client is never given one at any moment, which
 * is what makes the tier rules a control rather than a request.
 */
public final class TokenCache {

    private static final long EARLY_MS = 120_000;

    private final Map<String, Live> cache = new ConcurrentHashMap<>();
    private final NetHttpTransport transport = new NetHttpTransport();

    public String accessToken(CredentialRecord cred) throws IOException {
        var key = cred.key();
        var live = cache.get(key);
        if (live != null && live.usableAt(System.currentTimeMillis() + EARLY_MS)) return live.token();
        synchronized (this) {
            live = cache.get(key);
            if (live != null && live.usableAt(System.currentTimeMillis() + EARLY_MS)) return live.token();
            var fresh = refresh(cred);
            cache.put(key, fresh);
            return fresh.token();
        }
    }

    private Live refresh(CredentialRecord cred) throws IOException {
        if (cred.refreshToken == null || cred.refreshToken.isBlank()) {
            throw new IOException("no refresh token for " + cred.key() + " — run 'wallet login' for this account");
        }
        var req = new GoogleRefreshTokenRequest(transport, GsonFactory.getDefaultInstance(),
                cred.refreshToken, cred.clientId, cred.clientSecret);
        var res = req.execute();
        var ttl = res.getExpiresInSeconds() == null ? 3600 : res.getExpiresInSeconds();
        cred.lastUsed = System.currentTimeMillis();
        return new Live(res.getAccessToken(), System.currentTimeMillis() + ttl * 1000);
    }

    public void clear() {
        cache.clear();
    }
}
