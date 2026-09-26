package uskoag.gservices.docs;

import java.nio.file.*;

/**
 * The document as it was at revision n, through the export URL Drive publishes per revision. A saved
 * version never changes, so each (revision, format) is fetched once and kept under
 * ~/uskoag/gservices/gdocs/revisions/<doc>/ — walking a few hundred versions a second time costs nothing.
 */
public final class RevExport {

    static boolean refresh;

    private RevExport() {}

    static Path cacheDir(String docId) {
        return Path.of(System.getProperty("user.home"), "uskoag", "gservices", "gdocs", "revisions", docId);
    }

    /** md | txt | html | docx; Google's own names for them in the export URL. */
    static String exportFormat(String fmt) {
        return switch (fmt) {
            case "md", "markdown" -> "markdown";
            case "txt", "text" -> "txt";
            case "html" -> "html";
            case "docx" -> "docx";
            default -> { Out.die("revision formats: md, txt, html, docx"); yield null; }
        };
    }

    static String ext(String fmt) { return fmt.equals("markdown") ? "md" : fmt.equals("text") ? "txt" : fmt; }

    static String text(String docId, long rev, String fmt) throws Exception {
        return new String(bytes(docId, rev, fmt), java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Some revision numbers hold no content of their own (Google answers 400): the document then reads as
     * it did at the nearest earlier number that has one. The substitution is remembered in r<n>.same-as so
     * it costs one lookup, not one per walk.
     */
    static byte[] bytes(String docId, long rev, String fmt) throws Exception {
        var file = cacheDir(docId).resolve("r" + rev + "." + ext(fmt));
        if (!refresh && Files.exists(file)) return Files.readAllBytes(file);
        var alias = cacheDir(docId).resolve("r" + rev + ".same-as");
        if (!refresh && Files.exists(alias)) return bytes(docId, Long.parseLong(Files.readString(alias).strip()), fmt);
        byte[] data;
        try { data = fetch(docId, rev, exportFormat(fmt)); }
        catch (NoContent e) {
            if (rev <= 1) throw e;
            Out.info("revision " + rev + " holds no content of its own; reading it as revision " + (rev - 1));
            data = bytes(docId, rev - 1, fmt);
            var same = Files.exists(cacheDir(docId).resolve("r" + (rev - 1) + ".same-as"))
                    ? Files.readString(cacheDir(docId).resolve("r" + (rev - 1) + ".same-as")).strip() : String.valueOf(rev - 1);
            Files.createDirectories(alias.getParent());
            Files.writeString(alias, same);
            return data;
        }
        Files.createDirectories(file.getParent());
        Files.write(file, data);
        return data;
    }

    private static byte[] fetch(String docId, long rev, String exportFormat) throws Exception {
        var access = Auth.exportAccess();
        var url = Raw.root(access, "https://docs.google.com/") + "feeds/download/documents/export/Export?id=" + docId + "&revision=" + rev + "&exportFormat=" + exportFormat;
        Out.info("export revision " + rev + " as " + exportFormat);
        var res = Raw.send(Raw.authed(access, url).GET().build(), "revision " + rev);
        if (res.statusCode() == 400 || res.statusCode() == 404) throw new NoContent(rev);
        if (res.statusCode() != 200) throw new IllegalStateException("export of revision " + rev + " failed: HTTP " + res.statusCode()
                + (res.statusCode() == 403 ? " — if the wallet is locked or refused, unlock or approve and run again" : "")
                + (res.statusCode() == 404 || res.statusCode() == 400 ? " — Google keeps no content for that revision number" : ""));
        return res.body();
    }
}
