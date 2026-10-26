package uskoag.gservices.sheetcli;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.javanet.NetHttpTransport;
import java.net.URI;
import java.net.http.*;
import uskoag.gservices.ServiceAccess;

/**
 * Hand-rolled calls through the wallet, for endpoints no generated client covers (a revision export,
 * Drive Activity). The wallet's initializer is asked which headers it would set and they are copied
 * across, so these go through the same grant as everything else. Pacing (429, 5xx) and dropped
 * connections are retried with backoff, because a walk makes hundreds of these.
 */
final class Raw {

    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).followRedirects(HttpClient.Redirect.NORMAL).build();

    private Raw() {}

    static String root(ServiceAccess access, String direct) { return access.rootUrl() == null ? direct : access.rootUrl(); }

    static HttpRequest.Builder authed(ServiceAccess access, String url) throws Exception {
        var req = HttpRequest.newBuilder(URI.create(url));
        var probe = new NetHttpTransport().createRequestFactory().buildGetRequest(new GenericUrl(url));
        access.initializer().initialize(probe);
        probe.getHeaders().forEach((k, v) -> {
            if (k.equalsIgnoreCase("user-agent") || k.equalsIgnoreCase("accept-encoding")) return;
            for (var x : v instanceof Iterable<?> it ? it : java.util.List.of(v)) req.header(k, String.valueOf(x));
        });
        return req;
    }

    static HttpResponse<byte[]> send(HttpRequest req, String what) throws Exception {
        var res = once(req);
        for (var wait = 2000L; (res.statusCode() == 429 || res.statusCode() >= 500) && wait <= 64000; wait *= 2) {
            Out.info("HTTP " + res.statusCode() + " on " + what + "; Google is pacing, retrying in " + wait / 1000 + "s");
            if (!Out.quiet && wait >= 8000) System.err.println("[rate limited] " + what + ": waiting " + wait / 1000 + "s");
            Thread.sleep(wait);
            res = once(req);
        }
        return res;
    }

    /** A dropped connection is retried: one lost socket must not end a walk over a hundred versions. */
    private static HttpResponse<byte[]> once(HttpRequest req) throws Exception {
        for (var wait = 1000L; ; wait *= 2) {
            try { return HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray()); }
            catch (java.io.IOException e) {
                if (wait > 16000) throw e;
                Out.info("connection dropped (" + e.getMessage() + "); retrying in " + wait / 1000 + "s");
                Thread.sleep(wait);
            }
        }
    }
}
