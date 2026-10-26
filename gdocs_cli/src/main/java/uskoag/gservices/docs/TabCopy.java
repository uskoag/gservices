package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.*;
import java.util.List;

/**
 * tab copy — a styled duplicate of a tab, placed right after it. The API has no request that copies a tab, so this is
 * `tab add` followed by `import` with the same document as the source and --from-tab: every paragraph, list, table,
 * picture and style comes across, and the import's own read-back is the verification. Dry run unless --apply.
 */
final class TabCopy {

    private TabCopy() {}

    static void run(List<String> a) throws Exception {
        var apply = Args.flag(a, "--apply");
        var srcSel = Args.val(a, "--tab", "-t");
        var title = Args.val(a, "--title");
        var id = DocRef.id(Args.req(a, "<doc>"));
        Args.noneLeft(a, "tab copy");
        if (srcSel == null || title == null) Out.die("tab copy needs --tab <source tab> and --title \"<title of the copy>\"");
        var doc = DocRef.get(id);
        var src = Tabs.select(Tabs.flat(doc), srcSel).get(0);
        var props = src.getTabProperties();
        Out.data("copy tab " + Tabs.title(src) + " (" + Tabs.id(src) + ") to a new tab \"" + title + "\" right after it");
        if (!apply) {
            new ImportPlan(src.getDocumentTab()).summary().forEach(Out::data);
            Out.data("DRY RUN — no tab added. Re-run with --apply.");
            return;
        }
        var add = new Request().setAddDocumentTab(new AddDocumentTabRequest().setTabProperties(new TabProperties().setTitle(title)
                .setParentTabId(props.getParentTabId()).setIndex((props.getIndex() == null ? 0 : props.getIndex()) + 1)));
        var res = Journal.around(id, "tab copy", "add \"" + title + "\" after " + Tabs.title(src), () -> Auth.docs().documents()
                .batchUpdate(id, new BatchUpdateDocumentRequest().setRequests(List.of(add)).setWriteControl(new WriteControl().setRequiredRevisionId(doc.getRevisionId()))).execute());
        var newId = res.getReplies().get(0).getAddDocumentTab().getTabProperties().getTabId();
        Out.data("added tab " + newId);
        VerbImport.run(new ArrayList<>(List.of(id, "--from", id, "--from-tab", Tabs.id(src), "--tab", newId, "--apply")));
        Out.data(newId + "\t" + title);
    }
}
