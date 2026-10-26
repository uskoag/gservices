package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.ArrayList;
import java.util.List;

/**
 * align — paragraph alignment and keep-with-next over one paragraph or a range of them, pictures-only paragraphs
 * included. The range runs from the paragraph named by --para (or the one holding --image) through the one
 * named by --to (the first match after it), or to the end of the tab; tables in between are covered, cell
 * paragraphs and all. One updateParagraphStyle, read back and compared. Dry run unless --apply.
 */
final class VerbAlign {

    private VerbAlign() {}

    static void run(List<String> a) throws Exception {
        var apply = Args.flag(a, "--apply");
        var showRequests = Args.flag(a, "--requests");
        var fmt = ParaFmt.of(a, true);
        var tabSel = Args.val(a, "--tab", "-t");
        var para = Args.val(a, "--para", "--from");
        var image = Args.val(a, "--image");
        var occ = Args.intVal(a, "--occurrence");
        var to = Args.val(a, "--to");
        var toOcc = Args.intVal(a, "--to-occurrence");
        var toEnd = Args.flag(a, "--to-end");
        var id = DocRef.id(Args.req(a, "<doc>"));
        Args.noneLeft(a, "align");
        if (!fmt.any()) Out.die("align needs --center|--left|--right|--justify and/or --keep-with-next / --keep-lines-together");
        if (para == null && image == null) Out.die("align needs --para \"<paragraph start>\" (or --image <objectId>)");
        if (para != null && image != null) Out.die("give --para or --image, not both");
        if (to != null && toEnd) Out.die("give --to or --to-end, not both");

        DocRef.suggestions = "inline";
        var doc = DocRef.get(id);
        var all = Tabs.flat(doc);
        if (tabSel == null && all.size() > 1) Out.die("the document has " + all.size() + " tabs; name one with --tab");
        var tabs = tabSel == null ? all : Tabs.select(all, tabSel);
        if (tabs.size() != 1) Out.die("--tab must name exactly one tab");
        var s = new Scope(tabs.get(0), Para.of(tabs.get(0).getDocumentTab()));
        var first = s.anchor("align --para", image != null ? Scope.IMAGE + image : para, occ);
        var last = toEnd ? s.paras().getLast() : to == null ? first : s.anchor("align --to", to, toOcc, first);
        var hit = s.paras().stream().filter(p -> p.start() >= first.start() && p.start() <= last.start()).toList();
        var range = new Range().setStartIndex(first.start()).setEndIndex(last.end()).setTabId(s.tabId());

        Out.data("align " + hit.size() + " paragraph(s) in " + s.name() + " @" + first.start() + "-" + last.end() + ": " + fmt.describe());
        for (var i = 0; i < hit.size(); i++)
            if (hit.size() <= 12 || i < 5 || i >= hit.size() - 2) Out.data("  @" + hit.get(i).start() + " \"" + hit.get(i).preview() + "\"");
            else if (i == 5) Out.data("  … " + (hit.size() - 7) + " more");
        var batch = new BatchUpdateDocumentRequest().setRequests(List.of(fmt.request(range)))
                .setWriteControl(new WriteControl().setRequiredRevisionId(doc.getRevisionId()));
        if (showRequests) Out.data(com.google.api.client.json.gson.GsonFactory.getDefaultInstance().toPrettyString(batch));
        if (!apply) { Out.data("DRY RUN — nothing sent; re-run with --apply."); return; }

        Journal.around(id, "align", "align " + fmt.describe() + " in " + s.name() + " @" + first.start() + "-" + last.end(),
                () -> Auth.docs().documents().batchUpdate(id, batch).execute());
        Out.success(hit.size() + " paragraph(s): " + fmt.describe());
        var wants = new ArrayList<AlignCheck.Want>();
        for (var p : hit) wants.add(AlignCheck.Want.at(s.tabId(), p.start(), fmt));
        var diffs = AlignCheck.verify(wants, DocRef.get(id));
        if (diffs.isEmpty()) { Out.data("VERIFIED: all " + wants.size() + " paragraph(s) read back as " + fmt.describe()); return; }
        Out.data("VERIFY FAILED: " + diffs.size() + " of " + wants.size() + " paragraph(s) differ:");
        diffs.forEach(d -> Out.data("  " + d));
        System.exit(2);
    }
}
