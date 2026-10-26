package uskoag.gservices.sheetcli;

import com.google.api.services.drive.model.Revision;
import java.util.*;

/** A spreadsheet's saved versions, as Drive keeps them. Oldest first; position n (1-based) is what `history` prints as #n. */
final class Revisions {

    static final String FIELDS = "nextPageToken,revisions(id,modifiedTime,keepForever,lastModifyingUser(displayName,emailAddress,permissionId))";
    private static final Map<String, List<Revision>> CACHE = new HashMap<>();

    private Revisions() {}

    static List<Revision> list(String id) throws Exception {
        if (CACHE.containsKey(id)) return CACHE.get(id);
        var all = new ArrayList<Revision>();
        String page = null;
        do {
            Out.info("revisions.list " + id);
            var res = Auth.drive().revisions().list(id).setFields(FIELDS).setPageSize(1000).setPageToken(page).execute();
            if (res.getRevisions() != null) all.addAll(res.getRevisions());
            page = res.getNextPageToken();
        } while (page != null);
        all.sort(Comparator.comparingLong(Revisions::millis));
        CACHE.put(id, all);
        return all;
    }

    static long millis(Revision r) { return r.getModifiedTime().getValue(); }

    static String who(Revision r) {
        var u = r.getLastModifyingUser();
        if (u == null) return "(unknown)";
        var name = u.getDisplayName() == null ? "" : u.getDisplayName();
        return u.getEmailAddress() == null ? name : name + " <" + u.getEmailAddress() + ">";
    }

    static String email(Revision r) {
        var u = r.getLastModifyingUser();
        return u == null || u.getEmailAddress() == null ? "" : u.getEmailAddress();
    }

    /** --by matches an email or a display name, case-insensitive substring. */
    static boolean by(Revision r, String who) { return who == null || who(r).toLowerCase().contains(who.toLowerCase()); }
}
