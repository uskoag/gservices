package uskoag.gservices.docs;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.docs.v1.model.*;
import java.util.List;

/** Batches the replay's requests; a failure says which stage and which batch it stopped in. */
final class ImportSend {

    static final int SIZE = 250;

    static String stage = "start";
    static int batches;

    private ImportSend() {}

    static void send(String docId, String name, List<Request> reqs, String requiredRevision) throws Exception {
        stage = name;
        var n = (reqs.size() + SIZE - 1) / SIZE;
        for (var i = 0; i < n; i++) {
            var part = reqs.subList(i * SIZE, Math.min(reqs.size(), (i + 1) * SIZE));
            var batch = new BatchUpdateDocumentRequest().setRequests(part);
            if (i == 0 && requiredRevision != null) batch.setWriteControl(new WriteControl().setRequiredRevisionId(requiredRevision));
            stage = name + " (batch " + (i + 1) + "/" + n + ", " + part.size() + " request(s))";
            try { post(docId, batch); }
            catch (GoogleJsonResponseException e) {
                var msg = e.getDetails() != null && e.getDetails().getMessage() != null ? e.getDetails().getMessage() : e.getStatusMessage();
                throw new IllegalStateException("stage " + stage + ": API " + e.getStatusCode() + ": " + msg, e);
            }
            batches++;
            Out.info("stage " + stage + " done at " + java.time.LocalTime.now().withNano(0));
        }
    }

    /** Same, returning every reply in request order: createFootnote hands the new footnote id back only there. */
    static List<Response> sendReplies(String docId, String name, List<Request> reqs) throws Exception {
        var out = new java.util.ArrayList<Response>();
        stage = name;
        for (var i = 0; i < reqs.size(); i += SIZE) {
            var batch = new BatchUpdateDocumentRequest().setRequests(reqs.subList(i, Math.min(reqs.size(), i + SIZE)));
            stage = name + " (requests " + (i + 1) + "-" + Math.min(reqs.size(), i + SIZE) + " of " + reqs.size() + ")";
            try { out.addAll(post(docId, batch).getReplies()); }
            catch (GoogleJsonResponseException e) { throw new IllegalStateException("stage " + stage + ": API " + e.getStatusCode() + ": " + e.getMessage(), e); }
            batches++;
        }
        return out;
    }

    private static BatchUpdateDocumentResponse post(String docId, BatchUpdateDocumentRequest batch) throws Exception {
        for (var attempt = 1; ; attempt++) {
            try { return Auth.docs().documents().batchUpdate(docId, batch).execute(); }
            catch (GoogleJsonResponseException e) {
                if (e.getStatusCode() != 429 || attempt >= 4) throw e;
                Out.info("rate limited, waiting 30 s (attempt " + attempt + ")");
                Thread.sleep(30_000);
            }
        }
    }
}
