package uskoag.gservices;

import com.google.api.client.googleapis.services.AbstractGoogleClient;
import com.google.api.client.http.HttpRequestInitializer;

/**
 * Everything a generated Google client needs, which is less than it looks: an initializer that puts
 * the right header on each request, and optionally a different place to send it.
 *
 * @param rootUrl null for googleapis.com; a loopback URL when a wallet is brokering, in which case the
 *                client physically cannot reach Google except through it — enforcement is not a policy
 *                the client is trusted to respect, it is the only route that exists
 */
public record ServiceAccess(HttpRequestInitializer initializer, String rootUrl, String account, String sourceName) {

    public static ServiceAccess direct(HttpRequestInitializer initializer, String account, String sourceName) {
        return new ServiceAccess(initializer, null, account, sourceName);
    }

    /** Applies both halves to any generated client builder, so no caller has to remember the rootUrl. */
    public <B extends AbstractGoogleClient.Builder> B applyTo(B builder) {
        if (rootUrl != null) builder.setRootUrl(rootUrl);
        return builder;
    }
}
