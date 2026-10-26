package uskoag.gservices.slides;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.slides.v1.model.*;
import java.util.*;
import java.util.List;

import static uskoag.gservices.comments.Wire.*;

/**
 * The Slides API's comments (Developer Preview). The Java client has no classes for them yet, so this
 * is the one place that spells their wire names, over GenericJson. Slides has comments only: there is
 * no suggestion mode in Slides, in the editor or in the API.
 */
final class CommentApi {

    private CommentApi() {}

    /** No field mask: a mask that names preview fields is refused by clients that do not know them. */
    static Presentation get(String presId) throws Exception {
        var req = Auth.slides().presentations().get(presId);
        req.set("commentsViewMode", "COMMENTS_VIEW_MODE_INCLUDED");
        Out.info("presentations.get " + presId + " (comments included)");
        try { return req.execute(); }
        catch (GoogleJsonResponseException e) { throw explain(e); }
    }

    static List<Map<String, Object>> comments(Presentation p) { return list(p.get("comments")); }

    static List<Map<String, Object>> anchors(Page p) { return p == null ? List.of() : list(p.get("commentAnchors")); }

    static Map<String, Object> onObject(String objectId) { return Map.of("objectId", objectId); }

    static Map<String, Object> onText(String objectId, int start, int end) {
        return Map.of("shapeTextAnchor", Map.of("objectId", objectId, "textRange", fixed(start, end)));
    }

    static Map<String, Object> onTable(String objectId) { return Map.of("tableAnchor", Map.of("objectId", objectId)); }

    static Map<String, Object> onCell(String objectId, int row, int col, Integer start, Integer end) {
        return Map.of("tableCellTextAnchor", Map.of("objectId", objectId,
                "cellLocation", Map.of("rowIndex", row, "columnIndex", col),
                "textRange", start == null ? Map.of("type", "ALL") : fixed(start, end)));
    }

    private static Map<String, Object> fixed(int start, int end) {
        return Map.of("type", "FIXED_RANGE", "startIndex", start, "endIndex", end);
    }

    static Request insert(Map<String, Object> anchor, String content, String assignee) {
        var body = new LinkedHashMap<String, Object>(anchor);
        body.put("content", content);
        if (assignee != null) body.put("assigneeEmailAddress", assignee);
        return (Request) new Request().set("insertComment", body);
    }

    static Request reply(String commentId, String content, String action, String assignee) {
        var post = new LinkedHashMap<String, Object>();
        if (content != null) post.put("content", content);
        if (action != null) post.put("commentAction", action);
        if (assignee != null) post.put("assigneeEmail", assignee);
        return (Request) new Request().set("addCommentReply", Map.of("commentId", commentId, "post", post));
    }

    static Request updatePost(String commentId, String postId, String content) {
        return (Request) new Request().set("updateCommentPost", Map.of("commentId", commentId, "postId", postId, "content", content));
    }

    static Request deleteComment(String commentId) {
        return (Request) new Request().set("deleteComment", Map.of("commentId", commentId));
    }

    static Request deleteReply(String commentId, String postId) {
        return (Request) new Request().set("deleteCommentReply", Map.of("commentId", commentId, "postId", postId));
    }

    /** Send, and fail loudly when Google reports the comment part of the batch as not saved. */
    static BatchUpdatePresentationResponse send(String presId, List<Request> reqs, String revisionId) throws Exception {
        var batch = new BatchUpdatePresentationRequest().setRequests(reqs);
        if (revisionId != null) batch.setWriteControl(new WriteControl().setRequiredRevisionId(revisionId));
        Out.info("batchUpdate: " + reqs.size() + " comment request(s)");
        BatchUpdatePresentationResponse res;
        try { res = Auth.slides().presentations().batchUpdate(presId, batch).execute(); }
        catch (GoogleJsonResponseException e) { throw explain(e); }
        if ("ALL_FAILED_UNKNOWN_REASON".equals(res.get("commentUpdateState")))
            Out.die("Google answered the batch but reports its comment updates as NOT saved (commentUpdateState=ALL_FAILED_UNKNOWN_REASON)");
        return res;
    }

    /** What the batch created: new thread ids and new post ids, in request order. */
    static List<String> created(BatchUpdatePresentationResponse res) {
        var made = new ArrayList<String>();
        for (var r : list(res.get("replies"))) {
            var ic = map(r.get("insertComment"));
            if (!ic.isEmpty()) made.add("comment " + str(map(ic.get("commentThread")), "commentId"));
            var ar = map(r.get("addCommentReply"));
            if (!ar.isEmpty()) made.add("post " + str(map(ar.get("post")), "postId"));
        }
        return made;
    }

    /** A refusal that names a preview field is almost always the project not being let into the preview; say that in one line. */
    static Exception explain(GoogleJsonResponseException e) {
        var msg = e.getDetails() != null && e.getDetails().getMessage() != null ? e.getDetails().getMessage() : e.getStatusMessage();
        var m = msg == null ? "" : msg;
        if (m.matches("(?is).*(commentsViewMode|comments_view_mode|insertComment|insert_comment|addCommentReply|add_comment_reply|updateCommentPost|deleteComment|developer preview|unknown name|cannot find field).*"))
            return new IllegalStateException("Google refused a comments request (" + e.getStatusCode() + "): " + m
                    + " — Slides comments are a Developer Preview feature; if the project is not yet enrolled, this is the expected refusal");
        return e;
    }
}
