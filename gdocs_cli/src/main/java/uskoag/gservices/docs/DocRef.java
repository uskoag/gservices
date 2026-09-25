package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.Document;
import java.util.regex.Pattern;

/** A document is addressed by its id or by any Docs URL carrying it. */
public final class DocRef {

    private static final Pattern
            URL_ID = Pattern.compile("/document/d/([A-Za-z0-9_-]+)"),
            BARE_ID = Pattern.compile("[A-Za-z0-9_-]{20,}");

    static String suggestions;

    private DocRef() {}

    static String id(String ref) {
        if (ref == null) Out.die("missing <doc> (document id or Docs URL)");
        if (BARE_ID.matcher(ref).matches()) return ref;
        var m = URL_ID.matcher(ref);
        if (m.find()) return m.group(1);
        Out.die("cannot extract a document id from: " + ref);
        return null;
    }

    /** Every tab's content comes back only when asked for; without the flag a multi-tab document reads as its first tab. */
    static Document get(String id) throws Exception {
        var req = Auth.docs().documents().get(id).setIncludeTabsContent(true);
        if (suggestions != null) req.setSuggestionsViewMode(switch (suggestions) {
            case "inline" -> "SUGGESTIONS_INLINE";
            case "accepted" -> "PREVIEW_SUGGESTIONS_ACCEPTED";
            case "original", "rejected" -> "PREVIEW_WITHOUT_SUGGESTIONS";
            default -> { Out.die("--suggestions must be inline, accepted or original"); yield null; }
        });
        Out.info("documents.get " + id);
        return req.execute();
    }
}
