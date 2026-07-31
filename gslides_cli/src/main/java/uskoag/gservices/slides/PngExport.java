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

    static byte[] fullRes(String presId, String pageId, String accessToken) throws Exception {
        var url = "https://docs.google.com/presentation/d/" + presId
                + "/export/png?id=" + presId + "&pageid=" + pageId;
        var req = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + accessToken).GET().build();
        var res = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() != 200)
            throw new IllegalStateException("export of " + pageId + " failed: HTTP " + res.statusCode());
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
