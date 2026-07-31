package uskoag.gservices.slides;

import com.google.api.services.slides.v1.model.BatchUpdatePresentationRequest;
import com.google.api.services.slides.v1.model.BatchUpdatePresentationResponse;
import com.google.api.services.slides.v1.model.Request;
import java.util.List;

/**
 * Every mutation goes through one batchUpdate, so a multi-op verb costs one round trip
 * rather than one per op. --dry-run stops here, which is why it can be honest about
 * having sent nothing.
 */
public final class Api {

    static boolean dryRun = false;

    private Api() {}

    static BatchUpdatePresentationResponse flush(String presId, List<Request> reqs) throws Exception {
        if (reqs == null || reqs.isEmpty()) { Out.info("nothing to send"); return null; }
        if (dryRun) {
            Out.data("(dry run -- " + reqs.size() + " request(s) NOT sent)");
            for (var r : reqs) Out.data("  " + describe(r));
            return null;
        }
        Out.info("batchUpdate: " + reqs.size() + " request(s)");
        return Auth.slides().presentations()
                .batchUpdate(presId, new BatchUpdatePresentationRequest().setRequests(reqs))
                .execute();
    }

    /** The op name plus its target, enough for a --dry-run reader to verify intent. */
    static String describe(Request r) {
        var json = Out.GSON.toJson(r);
        return json.length() > 400 ? json.substring(0, 400) + "..." : json;
    }
}
