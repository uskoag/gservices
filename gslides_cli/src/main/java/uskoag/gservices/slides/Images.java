package uskoag.gservices.slides;

import com.google.api.client.http.FileContent;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The Slides API fetches an image URL server-side once, so the URL only has to be
 * publicly reachable at insert time -- but it MUST be public. A local file or a private
 * Drive file cannot be used. Making that invisible is the point: anywhere this tool
 * accepts an image, a local path just works, which is what makes krea-ai output usable
 * without a manual upload step.
 */
public final class Images {

    private Images() {}

    static String urlFor(String ref) throws Exception {
        if (ref == null) return null;
        if (ref.startsWith("http://") || ref.startsWith("https://")) return ref;
        var p = Path.of(ref);
        if (!Files.isRegularFile(p)) Out.die("no such image file: " + ref);
        var up = upload(p, p.getFileName().toString(), null);
        Out.info("uploaded " + p.getFileName() + " -> " + up.id());
        return up.lh3Url();
    }

    static UploadedImage upload(Path f, String name, String folderId) throws Exception {
        var meta = new File().setName(name != null ? name : f.getFileName().toString());
        if (folderId != null) meta.setParents(List.of(folderId));
        var created = Auth.drive().files()
                .create(meta, new FileContent(mime(f.getFileName().toString()), f.toFile()))
                .setFields("id").execute();
        Auth.drive().permissions()
                .create(created.getId(), new Permission().setType("anyone").setRole("reader"))
                .execute();
        return UploadedImage.of(created.getId());
    }

    static String mime(String n) {
        var s = n.toLowerCase();
        if (s.endsWith(".png")) return "image/png";
        if (s.endsWith(".gif")) return "image/gif";
        if (s.endsWith(".webp")) return "image/webp";
        if (s.endsWith(".svg")) return "image/svg+xml";
        return "image/jpeg";
    }
}
