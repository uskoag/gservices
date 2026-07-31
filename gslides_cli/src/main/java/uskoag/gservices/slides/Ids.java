package uskoag.gservices.slides;

import java.util.regex.Pattern;

/**
 * Caller-assigned object ids are the whole point of --id, so a bad one must fail here
 * with something actionable rather than as an opaque API 400. The API's rule: first
 * character [a-zA-Z0-9_], then any of [a-zA-Z0-9_-:], total length 5 to 50. Note the
 * absence of '.' -- dotted names like slide3.title look natural and are rejected.
 */
public final class Ids {

    private static final Pattern OK = Pattern.compile("[a-zA-Z0-9_][a-zA-Z0-9_:-]*");

    private Ids() {}

    static String validate(String id) {
        if (id == null) return null;
        if (!OK.matcher(id).matches() || id.length() < 5 || id.length() > 50)
            Out.die("--id \"" + id + "\" is not a valid Slides object id: 5-50 chars, first one of"
                    + " [a-zA-Z0-9_], rest [a-zA-Z0-9_-:]. No dots -- try \"" + suggest(id) + "\".");
        return id;
    }

    static String suggest(String id) {
        var s = id == null ? "" : id.replaceAll("[^a-zA-Z0-9_:-]", "_").replaceAll("^[^a-zA-Z0-9_]+", "");
        while (s.length() < 5) s = s + "_";
        return s.length() > 50 ? s.substring(0, 50) : s;
    }
}
