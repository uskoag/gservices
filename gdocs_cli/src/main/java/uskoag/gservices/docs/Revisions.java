package uskoag.gservices.docs;

import uskoag.gservices.comments.Identity;
import com.google.api.services.drive.model.Revision;
import java.util.*;

/**
 * A document's saved versions, as Drive keeps them. Oldest first; position n (1-based) is what `history`
 * prints and what every --from/--to/--revision accepts as #n.
 */
public final class Revisions {

    static final String FIELDS = "nextPageToken,revisions(id,modifiedTime,keepForever,lastModifyingUser(displayName,emailAddress,permissionId),exportLinks)";

    private Revisions() {}

    static List<Revision> list(String docId) throws Exception {
        var all = new ArrayList<Revision>();
        String page = null;
        do {
            Out.info("revisions.list " + docId + (page == null ? "" : " page " + (all.size() / 1000 + 1)));
            var res = Auth.drive().revisions().list(docId).setFields(FIELDS).setPageSize(1000).setPageToken(page).execute();
            if (res.getRevisions() != null) all.addAll(res.getRevisions());
            page = res.getNextPageToken();
        } while (page != null);
        all.sort(Comparator.comparingLong(r -> r.getModifiedTime().getValue()));
        return all;
    }

    static String who(Revision r) {
        var u = r.getLastModifyingUser();
        if (u == null) return "(unknown)";
        var name = u.getDisplayName() == null ? "" : u.getDisplayName();
        var who = u.getEmailAddress() == null ? name : name + " <" + u.getEmailAddress() + ">";
        var role = role(r);
        return role.equals("other") ? who : who + " " + Identity.label(role);
    }

    static String role(Revision r) {
        var u = r.getLastModifyingUser();
        return u == null ? "other" : Identity.role(u.getEmailAddress(), u.getPermissionId(), u.getDisplayName());
    }

    static String email(Revision r) {
        var u = r.getLastModifyingUser();
        return u == null || u.getEmailAddress() == null ? "" : u.getEmailAddress();
    }

    static long millis(Revision r) { return r.getModifiedTime().getValue(); }

    /** --by matches an email or a display name, case-insensitive substring: "admin@", "Jane". */
    static boolean by(Revision r, String who) {
        if (who == null) return true;
        var w = who.toLowerCase();
        return who(r).toLowerCase().contains(w);
    }
}
