package uskoag.gservices.docs;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.docs.v1.model.*;
import java.util.*;
import java.util.List;

/**
 * The Docs API's comments and suggestions (Developer Preview since 2026-07-07). The Java client has no
 * classes for them yet, so this is the one place that spells their wire names, over GenericJson.
 * Every other class works with the maps this returns.
 */
final class Preview {

    private Preview() {}

    /** Comments come back only with all three set; any other combination is a 400. */
    static Document get(String id) throws Exception {
        var req = Auth.docs().documents().get(id).setIncludeTabsContent(true).setSuggestionsViewMode("SUGGESTIONS_INLINE");
        req.set("commentsViewMode", "COMMENTS_VIEW_MODE_INCLUDED");
        Out.info("documents.get " + id + " (comments included)");
        try { return req.execute(); }
        catch (GoogleJsonResponseException e) { throw explain(e); }
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> list(Object o) {
        return o instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    static String str(Map<String, Object> m, String k) { return m.get(k) == null ? null : m.get(k).toString(); }

    static boolean yes(Map<String, Object> m, String k) { return Boolean.TRUE.equals(m.get(k)); }

    static List<Map<String, Object>> comments(Document d) { return list(d.get("comments")); }

    static List<Map<String, Object>> suggestions(Document d) { return list(d.get("suggestions")); }

    /** anchorId → ranges, for one tab. */
    static Map<String, Object> anchors(DocumentTab t) { return t == null ? Map.of() : map(t.get("commentAnchors")); }

    static void suggestMode(WriteControl w) { w.set("writeMode", "SUGGEST"); }

    static Request insertComment(String tabId, int start, int end, String content, String assignee) {
        var body = new LinkedHashMap<String, Object>();
        body.put("content", content);
        if (assignee != null) body.put("assigneeEmailAddress", assignee);
        body.put("range", Map.of("tabId", tabId, "startIndex", start, "endIndex", end));
        return (Request) new Request().set("insertComment", body);
    }

    /** A reply on a comment thread or a suggestion thread; the thread kind decides the key. */
    static Request reply(DocThread t, String content, String action, String assignee) {
        var post = new LinkedHashMap<String, Object>();
        if (content != null) post.put("content", content);
        if (action != null) post.put("commentAction", action);
        if (assignee != null) post.put("assigneeEmail", assignee);
        var body = new LinkedHashMap<String, Object>();
        body.put(t.idKey(), t.id());
        body.put("post", post);
        return (Request) new Request().set("addCommentReply", body);
    }

    static Request replyToSuggestion(String suggestionId, String content) {
        return (Request) new Request().set("addCommentReply", Map.of("suggestionId", suggestionId, "post", Map.of("content", content)));
    }

    static Request updatePost(DocThread t, String postId, String content) {
        return (Request) new Request().set("updateCommentPost", Map.of(t.idKey(), t.id(), "postId", postId, "content", content));
    }

    static Request deleteComment(String commentId) {
        return (Request) new Request().set("deleteComment", Map.of("commentId", commentId));
    }

    static Request deleteReply(DocThread t, String postId) {
        return (Request) new Request().set("deleteCommentReply", Map.of(t.idKey(), t.id(), "postId", postId));
    }

    static Request suggestion(String op, String suggestionId) {
        return (Request) new Request().set(op + "Suggestion", Map.of("suggestionId", suggestionId));
    }

    /** Send, and fail loudly when Google reports the comment part of the batch as not saved. */
    static BatchUpdateDocumentResponse send(String id, List<Request> reqs, WriteControl wc) throws Exception {
        var batch = new BatchUpdateDocumentRequest().setRequests(reqs);
        if (wc != null) batch.setWriteControl(wc);
        BatchUpdateDocumentResponse res;
        try { res = Auth.docs().documents().batchUpdate(id, batch).execute(); }
        catch (GoogleJsonResponseException e) { throw explain(e); }
        var state = res.get("commentUpdateState");
        if ("ALL_FAILED_UNKNOWN_REASON".equals(state)) Out.die("Google applied the batch but reports its comment updates as NOT saved (commentUpdateState=ALL_FAILED_UNKNOWN_REASON)");
        return res;
    }

    /** A refusal that names a preview field is almost always the project not being let into the preview; say that in one line. */
    static Exception explain(GoogleJsonResponseException e) {
        var msg = e.getDetails() != null && e.getDetails().getMessage() != null ? e.getDetails().getMessage() : e.getStatusMessage();
        var m = msg == null ? "" : msg;
        if (m.matches("(?is).*(commentsViewMode|comments_view_mode|writeMode|write_mode|insertComment|insert_comment|addCommentReply|add_comment_reply|updateCommentPost|deleteComment|acceptSuggestion|rejectSuggestion|deleteSuggestion|developer preview|unknown name|cannot find field).*"))
            return new IllegalStateException("Google refused a comments/suggestions request (" + e.getStatusCode() + "): " + m
                    + " — these are Developer Preview features; if the project is not yet enrolled, this is the expected refusal");
        return e;
    }

    /** Suggestion ids created by each request, in request order (1:1 with the batch). */
    static List<List<String>> createdSuggestionIds(BatchUpdateDocumentResponse res) {
        var out = new ArrayList<List<String>>();
        for (var r : list(res.get("suggestionResponses"))) {
            var ids = new ArrayList<String>();
            if (r.get("createdSuggestionIds") instanceof List<?> l) for (var x : l) ids.add(x.toString());
            out.add(ids);
        }
        return out;
    }
}
