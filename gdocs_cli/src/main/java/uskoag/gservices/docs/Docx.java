package uskoag.gservices.docs;

import java.nio.file.*;

/** The Docs API has no export; Drive's files.export renders the whole document, every tab, as one docx. */
public final class Docx {

    static final String MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private Docx() {}

    static void export(String id, String tabSel, String out) throws Exception {
        if (tabSel != null) Out.die("--format docx exports the whole document; a single tab cannot be exported through the API yet."
                + " Read it as md or html with --tab instead.");
        var meta = Auth.drive().files().get(id).setFields("name").setSupportsAllDrives(true).execute();
        var p = Path.of(out != null ? out : VerbRead.slug(meta.getName()) + ".docx");
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        Out.info("files.export " + id + " -> " + p);
        try (var os = Files.newOutputStream(p)) {
            Auth.drive().files().export(id, MIME).executeMediaAndDownloadTo(os);
        }
        Out.success("wrote " + p + " (" + Files.size(p) + " bytes)");
    }
}
