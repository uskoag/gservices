package uskoag.gservices;

import com.google.api.client.util.DateTime;

/** Turns a plain --start/--end/--from/--to string into what the Calendar API wants, and back for display. */
final class CalTime {

    private CalTime() {
    }

    /** Bare "yyyy-MM-dd" is a date-only (all-day) value; anything with a "T" is a date-time. */
    static boolean isDateOnly(String s) {
        return s != null && !s.contains("T");
    }

    /**
     * {@link DateTime}'s own String constructor already tells the two shapes apart (no "T" -> date-only),
     * which is exactly the rule above — this is just the one call that does it.
     */
    static DateTime parse(String s) {
        return new DateTime(s);
    }

    static DateTime now() {
        return new DateTime(System.currentTimeMillis());
    }

    /**
     * For {@code timeMin}/{@code timeMax} on {@code events.list} specifically — unlike an event's own
     * {@code start}/{@code end}, which accepts a bare date for an all-day event, Google's list endpoint
     * always requires a full timestamp and 400s on a date-only value. Same input parsing as {@link
     * #parse}, just never date-only in what comes out.
     */
    static DateTime asTimestamp(String s) {
        var dt = new DateTime(s);
        return dt.isDateOnly() ? new DateTime(dt.getValue()) : dt;
    }

    static DateTime plusDays(DateTime from, long days) {
        return new DateTime(from.getValue() + days * 86_400_000L);
    }

    /** {@code yyyy-MM-dd} for a date-only value, full RFC 3339 with offset otherwise — never re-derived by hand. */
    static String display(DateTime dt) {
        return dt == null ? null : dt.toStringRfc3339();
    }
}
