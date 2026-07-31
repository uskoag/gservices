package uskoag.gservices.slides;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Two genuinely different render paths, both kept:
 *
 *  - full resolution via the undocumented /export/png?pageid= endpoint, which Google
 *    rate limits, hence the delay between slides;
 *  - the official getThumbnail API, capped near 1600px but with no delay, which makes
 *    it the cheap option for the render-look-fix loop.
 */
public final class PngExport {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS).build();

    private PngExport() {}

    /**
     * The full-resolution render, authorised without this process ever seeing a Google token.
     *
     * <p>{@code access.rootUrl()} is the loopback root when a wallet is brokering and null otherwise,
     * so the same code serves both: with a wallet the request goes to the wallet, which classifies it,
     * names the deck and attaches the bearer on the far side; without one it goes straight to
     * docs.google.com exactly as before. The header is applied by the initializer either way, which is
     * what removed the need for {@code getAccessToken()} — the call that used to force a real
     * credential into this jar for this one endpoint.
     */
    static byte[] fullRes(String presId, String pageId, uskoag.gservices.ServiceAccess access)
            throws Exception {
        var root = access.rootUrl() == null ? "https://docs.google.com/" : access.rootUrl();
        var url = root + "presentation/d/" + presId + "/export/png?id=" + presId + "&pageid=" + pageId;

        var req = HttpRequest.newBuilder(URI.create(url)).GET();
        // The generated clients take an initializer; this endpoint is hand-rolled, so the same
        // initializer is asked what header it would have set and that header is copied across. Going
        // through it rather than around it is what keeps one refresh path for the whole tool.
        var probe = new com.google.api.client.http.javanet.NetHttpTransport()
                .createRequestFactory().buildGetRequest(new com.google.api.client.http.GenericUrl(url));
        access.initializer().initialize(probe);
        var auth = probe.getHeaders().getAuthorization();
        if (auth != null) req.header("Authorization", auth);

        var res = HTTP.send(req.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() != 200)
            throw new IllegalStateException("export of " + pageId + " failed: HTTP " + res.statusCode()
                    + (res.statusCode() == 403 ? " — if a wallet is brokering, this is likely its refusal"
                       + " rather than Google's; check the approval dialog" : ""));
        return res.body();
    }

    static byte[] viaThumbnail(String presId, String pageId, String size) throws Exception {
        var thumb = Auth.slides().presentations().pages().getThumbnail(presId, pageId)
                .setThumbnailPropertiesThumbnailSize(size).execute();
        try (var in = URI.create(thumb.getContentUrl()).toURL().openStream()) {
            return in.readAllBytes();
        }
    }

    /**
     * The API offers no width parameter, only LARGE/MEDIUM/SMALL, so capping the width
     * happens locally. Worth doing: what an agent pays to look at an image scales with
     * its pixel dimensions.
     */
    static byte[] downscale(byte[] png, int maxWidth) throws Exception {
        var src = ImageIO.read(new java.io.ByteArrayInputStream(png));
        if (src == null || src.getWidth() <= maxWidth) return png;
        var h = (int) Math.round(src.getHeight() * (maxWidth / (double) src.getWidth()));
        var dst = new BufferedImage(maxWidth, h, BufferedImage.TYPE_INT_ARGB);
        var g = dst.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, maxWidth, h, null);
        g.dispose();
        var bos = new ByteArrayOutputStream();
        ImageIO.write(dst, "png", bos);
        return bos.toByteArray();
    }

    static Path write(Path target, byte[] data) throws Exception {
        if (target.getParent() != null) Files.createDirectories(target.getParent());
        Files.write(target, data);
        return target.toAbsolutePath();
    }

    static String name(int slideNumber, String pageId) {
        return String.format("slide_%03d_%s.png", slideNumber, pageId);
    }
}
