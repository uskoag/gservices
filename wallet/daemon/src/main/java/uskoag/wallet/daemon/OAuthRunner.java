package uskoag.wallet.daemon;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.MemoryDataStoreFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

/**
 * The consent flow, run by the wallet and by nothing else.
 *
 * <p>This is structural rather than a convenience: the refresh token is born in the consent response,
 * so a client that ran the flow would hold one at least once and the whole guarantee would be gone
 * before it started. The token store here is in memory, so nothing is written to disk except the
 * keyring the caller then saves.
 */
public final class OAuthRunner {

    private OAuthRunner() {
    }

    public static CredentialRecord consent(String account, String profile, String credentialsJson,
                                           List<String> scopes, int port) throws IOException {
        var secrets = GoogleClientSecrets.load(GsonFactory.getDefaultInstance(), new StringReader(credentialsJson));
        var flow = new GoogleAuthorizationCodeFlow.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance(), secrets, scopes)
                .setDataStoreFactory(MemoryDataStoreFactory.getDefaultInstance())
                .setAccessType("offline")
                .setApprovalPrompt("force")
                .build();

        var receiver = new LocalServerReceiver.Builder().setPort(port).build();
        var credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize(account);
        return record(account, profile, credentialsJson, scopes, secrets, credential);
    }

    static CredentialRecord record(String account, String profile, String credentialsJson, List<String> scopes,
                                   GoogleClientSecrets secrets, Credential credential) throws IOException {
        if (credential.getRefreshToken() == null) {
            throw new IOException("Google returned no refresh token for " + account
                    + " — revoke the app at myaccount.google.com and consent again");
        }
        var r = new CredentialRecord(account, profile);
        r.credentialsJson = credentialsJson;
        r.clientId = secrets.getDetails().getClientId();
        r.clientSecret = secrets.getDetails().getClientSecret();
        r.refreshToken = credential.getRefreshToken();
        r.scopes = List.copyOf(scopes);
        return r;
    }
}
