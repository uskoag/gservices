package uskoag.gservices.slides;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

/**
 * Local allowlist gating every deck the tool touches, same shape as
 * uskoag-sheetcli's SpreadsheetCli.xml. XML rather than JSON because commenting an
 * entry out is a real use for an allowlist.
 */
public final class GSlidesConfig {

    static final Path FILE = Auth.OWN_CREDS.resolve("GSlidesCli.xml");

    private static final LinkedHashMap<String, Perm> perms = new LinkedHashMap<>();

    private GSlidesConfig() {}

    static void load() throws Exception {
        if (!Files.exists(FILE)) { save(); Out.info("created " + FILE); return; }
        var doc = Jsoup.parse(Files.readString(FILE), "", Parser.xmlParser());
        for (var e : doc.select("allowRead")) add(e.text().trim(), e.attr("name"), false);
        for (var e : doc.select("allowWrite")) add(e.text().trim(), e.attr("name"), true);
        Out.info("loaded " + perms.size() + " permission(s) from " + FILE);
    }

    private static void add(String id, String name, boolean write) {
        if (!id.isEmpty()) perms.put(id, new Perm(id, name, write));
    }

    /** Fails loudly rather than letting an unlisted deck reach the API. */
    static void require(String presId, String op) {
        var p = perms.get(presId);
        if (p == null || !p.allows(op))
            Out.die("PERMISSION DENIED: no " + op + " permission for " + presId
                    + " -- add it with: uskoag-gslides grant --" + op + " " + presId + " \"<name>\"");
    }

    static void grant(String id, String name, boolean write) throws Exception {
        perms.put(id, new Perm(id, name, write));
        save();
        Out.success("granted " + (write ? "write" : "read") + " on " + id);
    }

    static void revoke(String id) throws Exception {
        if (perms.remove(id) == null) Out.die("not on the allowlist: " + id);
        save();
        Out.success("revoked " + id);
    }

    static List<String> describe() {
        var out = new ArrayList<String>();
        for (var p : perms.values()) out.add(p.describe());
        return out;
    }

    private static void save() throws Exception {
        Files.createDirectories(FILE.getParent());
        var doc = Jsoup.parse("<GSlidesCli><permissions/></GSlidesCli>", "", Parser.xmlParser());
        var node = doc.selectFirst("permissions");
        node.appendChild(new Comment(" uskoag-gslides grant --write <presentationId> \"<name>\" "));
        for (var p : perms.values())
            node.appendElement(p.write() ? "allowWrite" : "allowRead")
                .attr("name", p.name() == null ? "" : p.name())
                .text(p.id());
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml).prettyPrint(true).indentAmount(4);
        Files.writeString(FILE, doc.outerHtml());
    }
}
