package uskoag.gservices.docs;

import com.google.api.client.http.FileContent;
import com.google.api.services.drive.model.File;
import java.nio.file.*;
import java.util.List;

/**
 * PRP 27 probe: can the official APIs carry a custom list format ("(%1)") through a .docx?
 * (1) Drive's docx export of a Google Doc, uploaded again with conversion to a NEW Google Doc: its list formats.
 * (2) files.update of an EXISTING Google Doc with that .docx as media: same id? tabs? list formats?
 * Args: account sourceDocId scratchFolderId docxPath
 */
public class DocxRoundTrip {

    public static void main(String[] a) throws Exception {
        Auth.email = a[0];
        var docx = Path.of(a[3]);
        try (var out = Files.newOutputStream(docx)) { Auth.drive().files().export(a[1], Docx.MIME).executeMediaAndDownloadTo(out); }
        System.out.println("exported " + Files.size(docx) + " bytes");
        var meta = new File().setName("PRP27 docx roundtrip (new)").setMimeType("application/vnd.google-apps.document").setParents(List.of(a[2]));
        var created = Auth.driveUpload().files().create(meta, new FileContent(Docx.MIME, docx.toFile())).setSupportsAllDrives(true).setFields("id").execute();
        System.out.println("(1) converted to new doc " + created.getId());
        report(created.getId());

        var target = Auth.driveUpload().files().copy(a[1], new File().setName("PRP27 docx roundtrip (updated in place)").setParents(List.of(a[2]))).setSupportsAllDrives(true).setFields("id").execute();
        System.out.println("(2) copy to update in place: " + target.getId() + ", tabs before: " + Tabs.flat(DocRef.get(target.getId())).size());
        var upd = Auth.driveUpload().files().update(target.getId(), new File(), new FileContent(Docx.MIME, docx.toFile())).setSupportsAllDrives(true).setFields("id,mimeType").execute();
        System.out.println("(2) updated: id " + upd.getId() + " mime " + upd.getMimeType());
        report(target.getId());
    }

    static void report(String id) throws Exception {
        var doc = DocRef.get(id);
        var tabs = Tabs.flat(doc);
        System.out.println("  tabs: " + tabs.size() + " " + tabs.stream().map(Tabs::title).toList());
        for (var t : tabs) {
            var lists = t.getDocumentTab().getLists();
            if (lists == null) continue;
            var formats = lists.values().stream().map(l -> l.getListProperties().getNestingLevels().get(Math.min(1, l.getListProperties().getNestingLevels().size() - 1)).getGlyphFormat()).distinct().toList();
            System.out.println("  tab " + Tabs.title(t) + ": " + lists.size() + " lists, level-1 formats " + formats);
        }
    }
}
