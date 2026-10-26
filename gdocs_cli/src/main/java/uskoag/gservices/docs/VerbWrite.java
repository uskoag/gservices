package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * write — fill an EMPTY tab from simple markdown: # headings (1-6), lists, paragraphs, **bold**, *italic*,
 * [links](url or tab:<sel>). Lists: "- " / "* " bullets, "1. " numbers, indentation (a tab, or 2-4 spaces) for
 * nesting, and "(a)" / "a." / "(i)" items under a numbered item for lettered sub-points, all ONE list with levels.
 * For a document just made with `uskoag-gdrivecli new doc`. It refuses a tab that already has text: changing
 * existing text is what the anchored edit verbs are for.
 */
final class VerbWrite {

    private VerbWrite() {}

    record Block(String style, String list, String md, int level, String marker) {
        Segments seg() { return Segments.parse(md); }
    }

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
        Links.tabs = all;
        if (tabSel == null && all.size() > 1) Out.die("write: the document has " + all.size() + " tabs; name one with --tab");
        var tab = tabSel == null ? all.get(0) : Tabs.select(all, tabSel).get(0);
        var existing = Para.of(tab.getDocumentTab()).stream().map(Para::plain).filter(s -> !s.isBlank()).count();
        if (existing > 0) Out.die("write: tab " + Tabs.title(tab) + " already has " + existing + " paragraph(s) of text; write only fills an empty tab (use insert/replace to change text)");
        var tabId = Tabs.id(tab);

        var segs = blocks.stream().map(Block::seg).toList();
        var text = new StringBuilder();
        var pre = new ArrayList<Integer>();
        var gone = new ArrayList<Integer>();
        var cum = 0;
        for (var i = 0; i < blocks.size(); i++) {
            pre.add(1 + text.length());
            gone.add(cum);
            text.append("\t".repeat(blocks.get(i).level())).append(segs.get(i).plain()).append('\n');
            cum += blocks.get(i).level();
        }
        var reqs = new ArrayList<Request>();
        reqs.add(new Request().setInsertText(new InsertTextRequest().setText(text.toString()).setLocation(new Location().setIndex(1).setTabId(tabId))));
        for (var i = 0; i < blocks.size(); i++)
            reqs.add(new Request().setUpdateParagraphStyle(new UpdateParagraphStyleRequest().setRange(range(tabId, pre.get(i), pre.get(i) + width(blocks.get(i), segs.get(i))))
                    .setParagraphStyle(new ParagraphStyle().setNamedStyleType(blocks.get(i).style())).setFields("namedStyleType")));
        // Consecutive list items become ONE list however deeply nested: a single createParagraphBullets over
        // their whole range, whose leading tabs are consumed as nesting levels.
        var runs = new ListRuns();
        var kind = (String) null;
        for (var i = 0; i < blocks.size(); i++) {
            var b = blocks.get(i);
            if (b.list() == null) { kind = null; continue; }
            if (kind == null || b.level() == 0) kind = b.list();
            runs.add(pre.get(i), pre.get(i) + width(b, segs.get(i)), 0, kind.equals("number") ? ListPresets.NUMBER : ListPresets.BULLET, gone.get(i));
        }
        reqs.addAll(runs.requests(tabId));
        // Text styles LAST, at positions with the tabs already gone: bullets and paragraph styles set after them wipe run formatting (PRP 07).
        for (var i = 0; i < blocks.size(); i++) {
            var s = segs.get(i);
            var at = pre.get(i) - gone.get(i);
            for (var span : s.bold()) reqs.add(style(tabId, at + span[0], at + span[1], new TextStyle().setBold(true), "bold"));
            for (var span : s.italic()) reqs.add(style(tabId, at + span[0], at + span[1], new TextStyle().setItalic(true), "italic"));
            for (var span : s.links()) reqs.add(style(tabId, at + span.from(), at + span.to(), new TextStyle().setLink(Links.resolve(span.target())), "link"));
        }

        show(blocks, segs);
        if (!apply) { Out.data("DRY RUN — " + blocks.size() + " paragraph(s) into empty tab " + Tabs.title(tab) + ", " + reqs.size() + " request(s). Re-run with --apply."); return; }
        var batch = new BatchUpdateDocumentRequest().setRequests(reqs).setWriteControl(new WriteControl().setRequiredRevisionId(doc.getRevisionId()));
        Journal.around(id, "write", blocks.size() + " paragraph(s) into tab " + Tabs.title(tab), () -> Auth.docs().documents().batchUpdate(id, batch).execute());
        Out.success(blocks.size() + " paragraph(s) written into tab " + Tabs.title(tab));
        verify(id, tabId, blocks, segs);
    }

    private static int width(Block b, Segments s) { return b.level() + s.plain().length() + 1; }

    private static void show(List<Block> blocks, List<Segments> segs) {
        var kind = (String) null;
        for (var i = 0; i < blocks.size(); i++) {
            var b = blocks.get(i);
            var s = segs.get(i);
            if (b.list() == null) kind = null;
            else {
                if (kind == null && b.marker() != null && b.marker().matches("\\d+") && !b.marker().equals("1"))
                    Out.data("  ⚠ a numbered list always starts at 1: the typed \"" + b.marker() + "\" is dropped from \"" + s.plain() + "\"");
                if (kind != null && !kind.equals(b.list()) && b.level() > 0) Out.data("  ⚠ a list has one kind: the nested " + b.list() + " item \"" + s.plain() + "\" takes the " + kind + " style of its list");
                if (kind == null || b.level() == 0) kind = b.list();
            }
            Out.data((b.list() != null ? "  " + "    ".repeat(b.level()) + (b.list().equals("number") ? "# " : "• ") : b.style().startsWith("HEADING") ? b.style() + ": " : "  ") + s.plain());
            for (var l : s.links()) Out.data("      link \"" + s.plain().substring(l.from(), l.to()) + "\" → " + Links.describe(Links.resolve(l.target())));
        }
    }

    /** Read the tab back and check every list paragraph is in a list at the level it was written at. */
    private static void verify(String id, String tabId, List<Block> blocks, List<Segments> segs) throws Exception {
        var expects = new ArrayList<Expect.Bullet>();
        var kind = (String) null;
        for (var i = 0; i < blocks.size(); i++) {
            var b = blocks.get(i);
            if (b.list() == null) { kind = null; expects.add(new Expect.Bullet(tabId, segs.get(i).plain(), false, null, null)); continue; }
            if (kind == null || b.level() == 0) kind = b.list();
            expects.add(new Expect.Bullet(tabId, segs.get(i).plain(), true, b.level(), kind.equals("number")));
        }
        var diffs = Expect.verifyBullets(expects, DocRef.get(id));
        if (diffs.isEmpty()) { Out.data("VERIFIED: all " + expects.size() + " paragraph(s) read back with the written list membership, nesting level and kind"); return; }
        Out.data("VERIFY FAILED: " + diffs.size() + " of " + expects.size() + " check(s) differ from what was written:");
        diffs.forEach(d -> Out.data("  " + d));
        System.exit(2);
    }

    static List<Block> parse(String md) {
        var out = new ArrayList<Block>();
        var ctx = new MdList();
        for (var raw : md.replace("\r", "").split("\n")) {
            if (raw.isBlank()) continue;
            var line = raw.stripTrailing();
            var s = line.strip();
            var h = 0;
            while (h < s.length() && h < 6 && s.charAt(h) == '#') h++;
            if (h > 0 && s.length() > h && s.charAt(h) == ' ' && MdList.indent(line) == 0) { ctx.reset(); out.add(new Block("HEADING_" + h, null, s.substring(h + 1).strip(), 0, null)); continue; }
            var it = ctx.item(line);
            if (it != null) { out.add(new Block("NORMAL_TEXT", it.list(), it.text(), it.level(), it.marker())); continue; }
            if (ctx.open() && MdList.indent(line) > 0 && !out.isEmpty() && out.getLast().list() != null) {
                var last = out.removeLast();
                out.add(new Block(last.style(), last.list(), last.md() + " " + s, last.level(), last.marker()));
                continue;
            }
            ctx.reset();
            out.add(new Block("NORMAL_TEXT", null, s, 0, null));
        }
        return out;
    }

    private static Range range(String tabId, int from, int to) { return new Range().setTabId(tabId).setStartIndex(from).setEndIndex(to); }

    private static Request style(String tabId, int from, int to, TextStyle t, String fields) {
        return new Request().setUpdateTextStyle(new UpdateTextStyleRequest().setRange(range(tabId, from, to)).setTextStyle(t).setFields(fields));
    }
}
