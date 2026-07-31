package uskoag.gservices;

import com.google.api.client.http.javanet.NetHttpTransport;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Today's behaviour, unchanged and permanently supported: the app-key decrypts a per-account token
 * store on disk and the client ends up holding a real Google credential.
 *
 * <p>The key is resolved lazily and only when a token is actually needed, so a tool that finds a
 * wallet never prompts for one.
 */
public final class AppKeyCredentialSource implements CredentialSource {

    private final Supplier<String> appKey;

    public AppKeyCredentialSource(Supplier<String> appKey) {
        this.appKey = appKey;
    }

    @Override
    public String name() {
        return "app-key";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public ServiceAccess access(AccessSpec spec) throws IOException {
        var key = appKey.get();
        if (key == null || key.isBlank()) throw new IOException("no app-key available");

        var scopes = spec.scopes();
        var rest = scopes.subList(1, scopes.size()).toArray(String[]::new);
        var token = OAuthToken.oauthToken(spec.appName(), key, scopes.getFirst(), rest);
        if (spec.legacyCredsRoot() != null) token.allCredsDir(java.nio.file.Path.of(spec.legacyCredsRoot()));
        if (spec.account() != null && !spec.account().isBlank()) token.credential(spec.account());
        else token.defaultCredential();

        var credential = token.credentials(new NetHttpTransport());
        var email = token.activeCredDir().getFileName().toString();
        return ServiceAccess.direct(credential, email, name());
    }
}
