package uskoag.gservices.docs;

import com.google.api.client.http.FileContent;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;
import java.nio.file.*;

/**
 * Docs fetches an inserted image from a URL, once, at insert time, and keeps its own copy. The URL has
 * to be public, so a local file is uploaded to Drive, shared read-only by link for the insert, and
 * trashed right after, so the picture does not stay public in Drive. The same route gslides uses.
 */
final class Images {

    record Source(String url, String uploadedId) {}

    private Images() {}

    static Source source(String ref) throws Exception {
        if (ref == null) Out.die("--image <file|https-url> is required");
        if (ref.startsWith("https://") || ref.startsWith("http://")) return new Source(ref, null);
        var p = Path.of(ref);
        if (!Files.isRegularFile(p)) Out.die("no such image file: " + ref);
        var drive = Auth.driveUpload();
        var created = drive.files().create(new File().setName("gdocscli-upload " + p.getFileName()), new FileContent(mime(p.toString()), p.toFile()))
                .setFields("id").execute();
        drive.permissions().create(created.getId(), new Permission().setType("anyone").setRole("reader")).execute();
        Out.info("uploaded " + p.getFileName() + " -> " + created.getId() + " (public for the insert only)");
        return new Source("https://lh3.googleusercontent.com/d/" + created.getId() + "=w2048", created.getId());
    }

    /** Trash, not delete: reversible, and the document keeps its own copy of the picture. */
    static void cleanup(Source s) {
        if (s.uploadedId() == null) return;
        try {
            Auth.driveUpload().files().update(s.uploadedId(), new File().setTrashed(true)).execute();
            Out.info("trashed the temporary upload " + s.uploadedId());
        } catch (Exception e) {
            Out.error("could not trash the temporary upload " + s.uploadedId() + " (it is still shared by link): " + e.getMessage());
        }
    }

    static String mime(String n) {
        var s = n.toLowerCase();
        if (s.endsWith(".png")) return "image/png";
        if (s.endsWith(".gif")) return "image/gif";
        if (s.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
}
