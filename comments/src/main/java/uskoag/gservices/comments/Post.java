package uskoag.gservices.comments;

import java.util.*;

import static uskoag.gservices.comments.Wire.*;

/** One post of a comment (or suggestion) thread, as the Docs and Slides APIs both return it. */
public record Post(String postId, String author, boolean me, String time, String content, String action, boolean deleted, boolean head) {

    public boolean signed() { return AiSign.isSigned(content); }

    /** ai | owner | other: the AI account or signature, the owner account, anyone else. */
    public String role() { return Identity.rolePost(author, me, signed()); }

    public boolean actionOnly() { return action != null && (content == null || content.isBlank()); }

    /** The head post, then the replies. */
    public static List<Post> of(Map<String, Object> thread) {
        var out = new ArrayList<Post>();
        var head = map(thread.get("headPost"));
        if (!head.isEmpty()) out.add(parse(head, true));
        for (var r : list(thread.get("replies"))) out.add(parse(r, false));
        return out;
    }

    static Post parse(Map<String, Object> p, boolean head) {
        var author = map(p.get("author"));
        var content = str(p, "content");
        if (content == null && p.get("contentHtml") != null) content = org.jsoup.Jsoup.parse(p.get("contentHtml").toString()).text();
        var action = str(p, "commentAction");
        if (action == null) action = str(p, "suggestionAction");
        if (action != null && (action.startsWith("NO_") || action.endsWith("UNSPECIFIED"))) action = null;
        var name = yes(author, "anonymous") ? "(anonymous)" : Objects.requireNonNullElse(str(author, "displayName"), "?");
        return new Post(str(p, "postId"), name, yes(author, "me"), str(p, "createTime"), content, action, yes(p, "deleted"), head);
    }

    /**
     * A human has spoken and the AI has not answered: the thread is open and its last post with text is
     * not the AI's. The AI is its own account when identity.json sets one, else its signature.
     */
    public static boolean awaiting(String status, List<Post> posts) {
        if (!"OPEN".equals(status)) return false;
        for (var i = posts.size() - 1; i >= 0; i--) {
            var p = posts.get(i);
            // A suggestion thread's first post is Google's own and has no text; it is nobody speaking.
            if (p.deleted() || p.actionOnly() || p.content() == null || p.content().isBlank()) continue;
            return !p.role().equals("ai");
        }
        return false;
    }

    public static Post find(List<Post> posts, String postId) {
        for (var p : posts) if (postId.equals(p.postId())) return p;
        return null;
    }
}
