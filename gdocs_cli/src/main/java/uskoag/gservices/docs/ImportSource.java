package uskoag.gservices.docs;

import com.google.api.client.http.FileContent;
import com.google.api.services.docs.v1.model.*;
import com.google.api.services.drive.model.File;
import java.nio.file.*;

/**
 * Where an import reads from. A .docx is uploaded to Drive with conversion to a Google Doc, because Google's own
 * converter is the best docx reader there is; the converted copy is read, then trashed. A Google Doc is read as it is.
 */
final class ImportSource implements AutoCloseable {

    static boolean keep;

    final Document doc;
    final String sourceId;
    final String name, convertedId;
    final Path docx;
    Tab tab;

    private ImportSource(Document doc, String name, String convertedId, Path docx) {
        this.sourceId = convertedId != null ? convertedId : doc.getDocumentId();
        this.doc = doc; this.name = name; this.convertedId = convertedId; this.docx = docx;
        this.tab = Tabs.flat(doc).get(0);
    }

    static ImportSource open(String from) throws Exception {
        var p = Path.of(from);
        if (!Files.isRegularFile(p)) return new ImportSource(DocRef.get(DocRef.id(from)), from, null, null);
        if (!p.toString().toLowerCase().endsWith(".docx")) Out.die("import: " + from + " is neither a .docx file nor a Google Doc id/url");
        var meta = new File().setName("gdocscli-import " + p.getFileName()).setMimeType("application/vnd.google-apps.document");
        var created = Auth.driveUpload().files().create(meta, new FileContent(Docx.MIME, p.toFile())).setFields("id").execute();
        Out.info("converted " + p.getFileName() + " -> scratch Google Doc " + created.getId() + " (trashed when the import ends)");
        try {
            return new ImportSource(DocRef.get(created.getId()), p.getFileName().toString(), created.getId(), p);
        } catch (Exception e) {
            trash(created.getId());
            throw e;
        }
    }

    /** The API leaves a table's own borders out of its JSON, so they are read from Google's docx export of the same document: one flag per table, in document order. */
    java.util.List<Boolean> tableBorders() {
        try {
            var bos = new java.io.ByteArrayOutputStream();
            Auth.drive().files().export(sourceId, Docx.MIME).executeMediaAndDownloadTo(bos);
            try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bos.toByteArray()))) {
                for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                    if (!e.getName().equals("word/document.xml")) continue;
                    var xml = new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    var out = new java.util.ArrayList<Boolean>();
                    var m = java.util.regex.Pattern.compile("<w:tbl>(.*?)</w:tblPr>", java.util.regex.Pattern.DOTALL).matcher(xml);
                    while (m.find()) out.add(m.group(1).contains("<w:tblBorders>") && m.group(1).contains("w:val=\"single\""));
                    var before = 0;
                    for (var t : Tabs.flat(doc)) { if (Tabs.id(t).equals(Tabs.id(tab))) break; before += tables(t.getDocumentTab().getBody().getContent()); }
                    var mine = tables(tab.getDocumentTab().getBody().getContent());
                    return before + mine <= out.size() ? out.subList(before, before + mine) : out;
                }
            }
        } catch (Exception ex) {
            Out.error("table borders could not be read from the docx export (" + ex.getMessage() + "); tables get Docs' default borders");
        }
        return java.util.List.of();
    }

    /** Google's temporary links to the pictures live about 30 minutes, so they are fetched again just before they are used. */
    java.util.Map<String, String> pictureLinks() throws Exception {
        var out = new java.util.HashMap<String, String>();
        var fresh = Tabs.flat(DocRef.get(sourceId)).stream().filter(t -> Tabs.id(t).equals(Tabs.id(tab))).findFirst().orElseThrow().getDocumentTab().getInlineObjects();
        if (fresh != null) fresh.forEach((k, v) -> { var ip = v.getInlineObjectProperties().getEmbeddedObject().getImageProperties(); if (ip != null) out.put(k, ip.getContentUri()); });
        return out;
    }

    /** Tables in a body, nested ones included: the count the docx export lists them by. */
    static int tables(java.util.List<StructuralElement> els) {
        var n = 0;
        for (var e : els == null ? java.util.List.<StructuralElement>of() : els)
            if (e.getTable() != null) { n++; for (var r : e.getTable().getTableRows()) for (var c : r.getTableCells()) n += tables(c.getContent()); }
        return n;
    }

    @Override public void close() {
        if (convertedId == null) return;
        if (keep) Out.data("scratch Google Doc kept: https://docs.google.com/document/d/" + convertedId + "/edit");
        else trash(convertedId);
    }

    private static void trash(String id) {
        try {
            Auth.driveUpload().files().update(id, new File().setTrashed(true)).execute();
            Out.info("trashed the scratch copy " + id);
        } catch (Exception e) {
            Out.error("could not trash the scratch Google Doc " + id + " (find it in Drive as \"gdocscli-import …\"): " + e.getMessage());
        }
    }
}
