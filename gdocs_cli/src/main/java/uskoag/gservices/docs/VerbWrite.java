package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * write — fill an EMPTY tab from simple markdown: # headings (1-6), "- " / "* " bullets, "1. " numbered
 * items, paragraphs, **bold**, *italic*. For a document just made with `uskoag-gdrivecli new doc`.
 * It refuses a tab that already has text: changing existing text is what the anchored edit verbs are for.
 */
final class VerbWrite {

    private VerbWrite() {}

    record Block(String style, String list, Segments seg) {}

    static void run(List<String> a) throws Exception {
        var apply = Args.flag(a, "--apply");
        var tabSel = Args.val(a, "--tab", "-t");
        var file = Args.val(a, "--from");
        var id = DocRef.id(Args.req(a, "<doc>"));
        Args.noneLeft(a, "write");
        if (file == null) Out.die("write needs --from <file.md>");
        var blocks = parse(Files.readString(Path.of(file)));
        if (blocks.isEmpty()) Out.die("write: " + file + " has no content");

        var doc = DocRef.get(id);
        var all = Tabs.flat(doc);
        if (tabSel == null && all.size() > 1) Out.die("write: the document has " + all.size() + " tabs; name one with --tab");
        var tab = tabSel == null ? all.get(0) : Tabs.select(all, tabSel).get(0);
        var existing = Para.of(tab.getDocumentTab()).stream().map(Para::plain).filter(s -> !s.isBlank()).count();
        if (existing > 0) Out.die("write: tab " + Tabs.title(tab) + " already has " + existing + " paragraph(s) of text; write only fills an empty tab (use insert/replace to change text)");
        var tabId = Tabs.id(tab);

        var text = new StringBuilder();
        var starts = new ArrayList<Integer>();
        for (var b : blocks) { starts.add(1 + text.length()); text.append(b.seg().plain()).append('\n'); }
        var reqs = new ArrayList<Request>();
        reqs.add(new Request().setInsertText(new InsertTextRequest().setText(text.toString()).setLocation(new Location().setIndex(1).setTabId(tabId))));
        for (var i = 0; i < blocks.size(); i++) {
            var from = starts.get(i);
            var to = from + blocks.get(i).seg().plain().length() + 1;
            reqs.add(new Request().setUpdateParagraphStyle(new UpdateParagraphStyleRequest().setRange(range(tabId, from, to))
                    .setParagraphStyle(new ParagraphStyle().setNamedStyleType(blocks.get(i).style())).setFields("namedStyleType")));
        }
        // Consecutive items of one kind become ONE list: a single createParagraphBullets over their whole range.
        for (var i = 0; i < blocks.size(); ) {
            var kind = blocks.get(i).list();
            if (kind == null) { i++; continue; }
            var j = i;
            while (j + 1 < blocks.size() && kind.equals(blocks.get(j + 1).list())) j++;
            var end = starts.get(j) + blocks.get(j).seg().plain().length();
            reqs.add(new Request().setCreateParagraphBullets(new CreateParagraphBulletsRequest().setRange(range(tabId, starts.get(i), end))
                    .setBulletPreset(kind.equals("number") ? "NUMBERED_DECIMAL_ALPHA_ROMAN" : "BULLET_DISC_CIRCLE_SQUARE")));
            i = j + 1;
        }
        // Text styles LAST: bullets and paragraph styles set after them wipe run formatting (PRP 07).
        for (var i = 0; i < blocks.size(); i++) {
            var s = blocks.get(i).seg();
            for (var span : s.bold()) reqs.add(style(tabId, starts.get(i) + span[0], starts.get(i) + span[1], new TextStyle().setBold(true), "bold"));
            for (var span : s.italic()) reqs.add(style(tabId, starts.get(i) + span[0], starts.get(i) + span[1], new TextStyle().setItalic(true), "italic"));
        }

        for (var b : blocks) Out.data((b.list() != null ? "  • " : b.style().startsWith("HEADING") ? b.style() + ": " : "  ") + b.seg().plain());
        if (!apply) { Out.data("DRY RUN — " + blocks.size() + " paragraph(s) into empty tab " + Tabs.title(tab) + ", " + reqs.size() + " request(s). Re-run with --apply."); return; }
        var batch = new BatchUpdateDocumentRequest().setRequests(reqs).setWriteControl(new WriteControl().setRequiredRevisionId(doc.getRevisionId()));
        Journal.around(id, "write", blocks.size() + " paragraph(s) into tab " + Tabs.title(tab), () -> Auth.docs().documents().batchUpdate(id, batch).execute());
        Out.success(blocks.size() + " paragraph(s) written into tab " + Tabs.title(tab));
    }

    static List<Block> parse(String md) {
        var out = new ArrayList<Block>();
        for (var raw : md.replace("\r", "").split("\n")) {
            var line = raw.strip();
            if (line.isEmpty()) continue;
            var h = 0;
            while (h < line.length() && h < 6 && line.charAt(h) == '#') h++;
            if (h > 0 && line.length() > h && line.charAt(h) == ' ') { out.add(new Block("HEADING_" + h, null, Segments.parse(line.substring(h + 1).strip()))); continue; }
            if (line.startsWith("- ") || line.startsWith("* ")) { out.add(new Block("NORMAL_TEXT", "bullet", Segments.parse(line.substring(2).strip()))); continue; }
            var m = java.util.regex.Pattern.compile("^\\d+[.)] ").matcher(line);
            if (m.find()) { out.add(new Block("NORMAL_TEXT", "number", Segments.parse(line.substring(m.end()).strip()))); continue; }
            out.add(new Block("NORMAL_TEXT", null, Segments.parse(line)));
        }
        return out;
    }

    private static Range range(String tabId, int from, int to) { return new Range().setTabId(tabId).setStartIndex(from).setEndIndex(to); }

    private static Request style(String tabId, int from, int to, TextStyle t, String fields) {
        return new Request().setUpdateTextStyle(new UpdateTextStyleRequest().setRange(range(tabId, from, to)).setTextStyle(t).setFields(fields));
    }
}
