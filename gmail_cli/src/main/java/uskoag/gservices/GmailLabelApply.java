package uskoag.gservices;

import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.HttpHeaders;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.ModifyMessageRequest;
import com.google.api.services.gmail.model.ModifyThreadRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static uskoag.gservices.GmailCli.*;

/**
 * Applies labels to existing messages or whole threads (conversations): {@code label}/{@code unlabel}
 * (one direction) and {@code modify} (--add and/or --remove together). Ids come from positionals,
 * --ids-file or --ids-stdin and are processed via chunked {@code gmail.batch()} (≤100 modify
 * sub-requests/round trip); a bad id is a per-item error, not a fatal exit (the process still exits 0),
 * mirroring the read/search batch commands. Threads are targeted with --threads.
 */
final class GmailLabelApply {

    private static final int CHUNK = 100;

    private GmailLabelApply() {}

    /** {@code label}/{@code unlabel}: labels already parsed; ids are positionals from index 2 (+file/stdin). */
    static void apply(GmailCli.Args a, List<String> addTokens, List<String> removeTokens) throws IOException {
        run(a, addTokens, removeTokens, collectTokens(positionalArgs(a, 2), a.get("ids-file"), a.has("ids-stdin")));
    }

    /** {@code modify}: --add/--remove flags; ids are positionals from index 1 (+file/stdin). */
    static void modify(GmailCli.Args a) throws IOException {
        run(a, splitCsv(a.get("add")), splitCsv(a.get("remove")),
            collectTokens(positionalArgs(a, 1), a.get("ids-file"), a.has("ids-stdin")));
    }

    private static void run(GmailCli.Args a, List<String> addTokens, List<String> removeTokens, List<String> ids) throws IOException {
        if (addTokens.isEmpty() && removeTokens.isEmpty()) {
            logError("nothing to do: specify label(s) to add and/or remove"); System.exit(2);
        }
        if (ids.isEmpty()) {
            logError("no target ids (give them as positionals, --ids-file <path>, or --ids-stdin)"); System.exit(2);
        }
        boolean threads = a.has("threads");
        Map<String, Label> idx = GmailLabels.labelIndex();
        List<String> addIds = resolveIds(addTokens, idx, a.has("create-missing"));
        List<String> removeIds = resolveIds(removeTokens, idx, false);

        List<GmailCli.BatchItem<Object>> items = new ArrayList<>(ids.size());
        for (String id : ids) items.add(new GmailCli.BatchItem<>(id));

        if (threads) modifyThreads(items, addIds, removeIds);
        else modifyMessages(items, addIds, removeIds);

        report(a, items, addIds, removeIds, threads);
    }

    /** Maps label tokens (names or ids) to ids; auto-creates unknown ones when {@code createMissing}, else exits 1. */
    private static List<String> resolveIds(List<String> tokens, Map<String, Label> idx, boolean createMissing) throws IOException {
        List<String> ids = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (String t : tokens) {
            Label l = GmailLabels.lookup(idx, t);
            if (l != null) { ids.add(l.getId()); continue; }
            if (createMissing) {
                Label made = gmail.users().labels().create(USER, new Label().setName(t)).execute();
                idx.put(made.getId(), made);
                if (made.getName() != null) idx.put(made.getName().toLowerCase(), made);
                ids.add(made.getId());
                logInfo("created label: " + made.getName() + " (" + made.getId() + ")");
            } else unknown.add(t);
        }
        if (!unknown.isEmpty()) {
            logError("unknown label(s): " + String.join(", ", unknown) + "  (add --create-missing to auto-create, or run 'labels')");
            System.exit(1);
        }
        return ids;
    }

    private static void modifyMessages(List<GmailCli.BatchItem<Object>> items, List<String> addIds, List<String> removeIds) throws IOException {
        ModifyMessageRequest req = new ModifyMessageRequest();
        if (!addIds.isEmpty()) req.setAddLabelIds(addIds);
        if (!removeIds.isEmpty()) req.setRemoveLabelIds(removeIds);
        for (int start = 0; start < items.size(); start += CHUNK) {
            int end = Math.min(start + CHUNK, items.size());
            BatchRequest batch = gmail.batch();
            for (int i = start; i < end; i++) {
                final GmailCli.BatchItem<Object> item = items.get(i);
                gmail.users().messages().modify(USER, item.id, req).queue(batch, new JsonBatchCallback<Message>() {
                    @Override public void onSuccess(Message m, HttpHeaders h) { item.value = m; }
                    @Override public void onFailure(GoogleJsonError e, HttpHeaders h) { item.error = errMsg(e); }
                });
            }
            logInfo("batch messages.modify: " + (end - start) + " request(s)");
            batch.execute();
        }
    }

    private static void modifyThreads(List<GmailCli.BatchItem<Object>> items, List<String> addIds, List<String> removeIds) throws IOException {
        ModifyThreadRequest req = new ModifyThreadRequest();
        if (!addIds.isEmpty()) req.setAddLabelIds(addIds);
        if (!removeIds.isEmpty()) req.setRemoveLabelIds(removeIds);
        for (int start = 0; start < items.size(); start += CHUNK) {
            int end = Math.min(start + CHUNK, items.size());
            BatchRequest batch = gmail.batch();
            for (int i = start; i < end; i++) {
                final GmailCli.BatchItem<Object> item = items.get(i);
                gmail.users().threads().modify(USER, item.id, req).queue(batch, new JsonBatchCallback<com.google.api.services.gmail.model.Thread>() {
                    @Override public void onSuccess(com.google.api.services.gmail.model.Thread t, HttpHeaders h) { item.value = t; }
                    @Override public void onFailure(GoogleJsonError e, HttpHeaders h) { item.error = errMsg(e); }
                });
            }
            logInfo("batch threads.modify: " + (end - start) + " request(s)");
            batch.execute();
        }
    }

    private static void report(GmailCli.Args a, List<GmailCli.BatchItem<Object>> items,
                               List<String> addIds, List<String> removeIds, boolean threads) {
        int ok = 0;
        for (var it : items) if (it.error == null) ok++;
        int failed = items.size() - ok;
        String noun = threads ? "thread(s)" : "message(s)";

        if (json) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("target", threads ? "threads" : "messages");
            out.put("addLabelIds", addIds);
            out.put("removeLabelIds", removeIds);
            out.put("count", items.size());
            out.put("ok", ok);
            out.put("failed", failed);
            List<Map<String, Object>> results = new ArrayList<>(items.size());
            for (var it : items) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("id", it.id);
                e.put("ok", it.error == null);
                if (it.error != null) e.put("error", it.error);
                results.add(e);
            }
            out.put("items", results);
            emitJson(out);
        } else {
            String tag = "(+[" + String.join(",", addIds) + "] -[" + String.join(",", removeIds) + "])";
            System.out.println((ok > 0 ? "SUCCESS: modified " : "modified ") + ok + " " + noun + "  " + tag);
            if (failed > 0) {
                System.out.println("failed: " + failed);
                for (var it : items) if (it.error != null) System.out.println("  - id=" + it.id + "  ERROR: " + it.error);
            }
        }

        boolean scopey = items.stream().anyMatch(it -> it.error != null && isScopeError(it.error));
        if (scopey) {
            String em = a.get("email");
            logError("Some writes were denied (insufficient permission). If this login predates label-write,");
            logError("re-grant once:  uskoag-gmailcli reauth -e " + (em == null ? "<email>" : em));
        }
        // Partial failures keep the batch convention (exit 0); a total permission failure is an auth
        // problem, not per-item data, so surface it as non-zero for scripts.
        if (scopey && ok == 0) System.exit(1);
    }

    private static boolean isScopeError(String err) {
        String e = err.toLowerCase();
        return e.contains("insufficient") || e.contains("permission") || e.contains("scope");
    }
}
