package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/**
 * tab add / rename / move / delete — the tab tree itself, which no other verb touches. One batchUpdate pinned to the
 * revision just read, dry run unless --apply, then read back and every tab compared with what was asked.
 */
public final class VerbTab {

    private VerbTab() {}

    static void run(List<String> a) throws Exception {
        if (a.isEmpty()) Out.die("tab needs a sub-verb: add, rename, move or delete");
        var sub = a.remove(0).toLowerCase();
        var apply = Args.flag(a, "--apply");
        var json = Args.flag(a, "--json");
        var showRequests = Args.flag(a, "--requests");
        var id = DocRef.id(Args.req(a, "<doc>"));
        var doc = DocRef.get(id);
        var all = Tabs.flat(doc);
        var wants = switch (sub) {
            case "add" -> TabPlan.add(all, a);
            case "rename" -> TabPlan.rename(all, a);
            case "move" -> TabPlan.move(all, a);
            case "delete" -> TabDelete.plan(all, a);
            default -> { Out.die("unknown tab sub-verb: " + sub + " (add, rename, move, delete)"); yield List.<TabWant>of(); }
        };
        Args.noneLeft(a, "tab " + sub);
        var lines = wants.stream().map(TabWant::describe).toList();
        lines.forEach(Out::data);
        TabPlan.warnDuplicates(all, wants);

        var batch = new BatchUpdateDocumentRequest().setRequests(wants.stream().map(TabWant::request).toList())
                .setWriteControl(new WriteControl().setRequiredRevisionId(doc.getRevisionId()));
        if (showRequests) Out.data(com.google.api.client.json.gson.GsonFactory.getDefaultInstance().toPrettyString(batch));
        if (!apply) {
            Out.data("DRY RUN — " + wants.size() + " tab change(s) against revision " + doc.getRevisionId() + ". Nothing sent; re-run with --apply to commit.");
            return;
        }
        var res = Journal.around(id, "tab " + sub, String.join(" | ", lines), () -> Auth.docs().documents().batchUpdate(id, batch).execute());
        var replies = Optional.ofNullable(res.getReplies()).orElse(List.of());
        var ids = new ArrayList<String>();
        for (var i = 0; i < wants.size(); i++) {
            var r = i < replies.size() ? replies.get(i) : null;
            var created = r == null || r.getAddDocumentTab() == null ? null : r.getAddDocumentTab().getTabProperties().getTabId();
            ids.add(TabWant.tabId(wants.get(i), created));
        }
        Out.success(wants.size() + " tab change(s) applied in one batch");
        verify(id, wants, ids, json);
    }

    private static void verify(String id, List<TabWant> wants, List<String> ids, boolean json) throws Exception {
        var after = Tabs.flat(DocRef.get(id));
        var byId = new HashMap<String, Tab>();
        after.forEach(t -> byId.put(Tabs.id(t), t));
        var failed = 0;
        var rows = new ArrayList<Map<String, Object>>();
        for (var i = 0; i < wants.size(); i++) {
            var tabId = ids.get(i);
            var t = tabId == null ? null : byId.get(tabId);
            var gone = wants.get(i) instanceof TabWant.Delete;
            var problem = gone ? (t == null ? null : "tab still present after apply") : t == null ? "tab not found after apply" : TabWant.mismatch(wants.get(i), t, after);
            if (problem != null) failed++;
            var row = new LinkedHashMap<String, Object>();
            row.put("tabId", tabId);
            if (t != null) {
                row.put("title", Tabs.title(t));
                row.put("parentTabId", Tabs.parentId(t));
                row.put("n", after.indexOf(t) + 1);
                row.put("path", Tabs.path(t, after));
            }
            row.put("verified", problem == null);
            if (problem != null) row.put("problem", problem);
            rows.add(row);
            if (gone) row.put("deleted", t == null);
            var where = gone && t == null ? "deleted" : row.get("n") + "\t" + row.get("path");
            if (!json) Out.data((problem == null ? "VERIFIED\t" : "VERIFY FAILED\t") + tabId + "\t" + where + (problem == null ? "" : "  — " + problem));
        }
        if (json) Out.json(Map.of("documentId", id, "tabs", rows));
        if (failed > 0) System.exit(2);
    }

    static List<String> titlesFile(String file) {
        try {
            return Files.readAllLines(Path.of(file)).stream().map(l -> l.replace("﻿", "").strip()).filter(l -> !l.isEmpty()).toList();
        } catch (Exception e) {
            Out.die("cannot read --titles-file " + file + ": " + e.getMessage());
            return null;
        }
    }
}
