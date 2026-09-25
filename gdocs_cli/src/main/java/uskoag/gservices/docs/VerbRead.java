package uskoag.gservices.docs;

import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.docs.v1.model.*;
import java.util.List;
import java.nio.file.*;
import java.util.*;
import org.jsoup.nodes.Document.OutputSettings;
import com.google.api.services.docs.v1.model.Document;
import org.jsoup.nodes.Element;

/**
 * read <doc> — every tab by default, or those named by --tab. Formats: md (default), html, json (the
 * API's own structure, untouched) and docx (Drive's export of the whole document).
 */
public final class VerbRead {

    private VerbRead() {}

    static void run(List<String> a) throws Exception {
        var fmt = Args.valOr(a, "--format", "md").toLowerCase();
        if (Args.flag(a, "--md")) fmt = "md";
        if (Args.flag(a, "--html")) fmt = "html";
        if (Args.flag(a, "--json")) fmt = "json";
        if (Args.flag(a, "--docx")) fmt = "docx";
        var sel = Args.val(a, "--tab", "-t");
        var out = Args.val(a, "--out", "-o");
        var outDir = Args.val(a, "--out-dir");
        var id = DocRef.id(Args.req(a, "<doc>"));
        Args.noneLeft(a, "read");
        if (fmt.equals("docx")) { Docx.export(id, sel, out); return; }
        if (!Set.of("md", "html", "json").contains(fmt)) Out.die("--format must be md, html, json or docx");

        var doc = DocRef.get(id);
        var all = Tabs.flat(doc);
        var tabs = Tabs.select(all, sel);
        if (outDir != null) { perTab(doc, all, tabs, fmt, Path.of(outDir)); return; }
        emit(render(doc, all, tabs, fmt), out);
    }

    static String render(Document doc, List<Tab> all, List<Tab> tabs, String fmt) throws Exception {
        return switch (fmt) {
            case "json" -> tabs.size() == all.size() ? doc.toPrettyString() : GsonFactory.getDefaultInstance().toPrettyString(tabs);
            case "html" -> html(doc, all, tabs);
            default -> md(doc, all, tabs);
        };
    }

    private static String md(Document doc, List<Tab> all, List<Tab> tabs) {
        var parts = new ArrayList<String>();
        parts.add("<!-- document: \"" + doc.getTitle() + "\" id=" + doc.getDocumentId() + " revision=" + doc.getRevisionId()
                + " tabs=" + all.size() + " -->");
        for (var t : tabs) {
            parts.add("<!-- tab " + (all.indexOf(t) + 1) + "/" + all.size() + " id=" + Tabs.id(t) + " title=\"" + Tabs.title(t)
                    + "\" path=\"" + Tabs.path(t, all) + "\" -->");
            parts.add(new MdRender(t.getDocumentTab()).render());
        }
        return String.join("\n\n", parts).strip() + "\n";
    }

    private static String html(Document doc, List<Tab> all, List<Tab> tabs) {
        var h = org.jsoup.nodes.Document.createShell("");
        h.outputSettings(new OutputSettings().prettyPrint(true).indentAmount(1).outline(false));
        h.head().appendElement("meta").attr("charset", "utf-8");
        h.title(doc.getTitle());
        var article = h.body().appendElement("article")
                .attr("data-document-id", doc.getDocumentId()).attr("data-revision-id", doc.getRevisionId());
        for (var t : tabs) {
            var section = new Element("section").attr("data-tab-id", Tabs.id(t)).attr("data-tab-title", Tabs.title(t))
                    .attr("data-tab-path", Tabs.path(t, all)).attr("data-tab-n", "" + (all.indexOf(t) + 1));
            article.appendChild(section);
            new HtmlRender(t.getDocumentTab()).into(section);
        }
        return "<!DOCTYPE html>\n" + h.outerHtml() + "\n";
    }

    private static void perTab(Document doc, List<Tab> all, List<Tab> tabs, String fmt, Path dir) throws Exception {
        Files.createDirectories(dir);
        for (var t : tabs) {
            var n = all.indexOf(t) + 1;
            var file = dir.resolve(String.format("%02d-%s.%s", n, slug(Tabs.title(t)), fmt));
            Files.writeString(file, render(doc, all, List.of(t), fmt));
            Out.data(file.toString());
        }
        Out.success("wrote " + tabs.size() + " tab file(s) to " + dir);
    }

    static String slug(String s) {
        var x = s.replaceAll("[\\\\/:*?\"<>|]", "").replaceAll("\\s+", " ").strip();
        return x.isEmpty() ? "untitled" : x.length() > 60 ? x.substring(0, 60).strip() : x;
    }

    private static void emit(String text, String out) throws Exception {
        if (out == null) { System.out.print(text); return; }
        var p = Path.of(out);
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        Files.writeString(p, text);
        Out.success("wrote " + p);
    }
}
