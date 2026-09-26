package uskoag.gservices.docs;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Times in and out. Output always carries its zone ("2026-09-25 21:15:04 EAT"), because the people
 * editing a document are rarely in one place. Input accepts ISO instants and the same form we print.
 */
public final class Times {

    /** Three-letter zones the office actually writes; anything else must be ISO or a region id. */
    private static final Map<String, String> ABBR = Map.of(
            "EAT", "Africa/Nairobi", "IST", "Asia/Kolkata", "UTC", "UTC", "GMT", "UTC",
            "PST", "America/Los_Angeles", "PDT", "America/Los_Angeles", "CET", "Europe/Paris");

    static ZoneId zone = ZoneId.systemDefault();

    private static final DateTimeFormatter OUT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss zzz");

    private Times() {}

    static String show(long millis) { return OUT.format(Instant.ofEpochMilli(millis).atZone(zone)); }

    static String iso(long millis) { return Instant.ofEpochMilli(millis).atZone(zone).toOffsetDateTime().toString(); }

    /** "2026-09-25T18:15:00Z", "2026-09-25 21:15 EAT", "2026-09-25 21:15", "2026-09-25" (start of that day). */
    static long parse(String s) {
        var t = s.strip();
        try { return OffsetDateTime.parse(t).toInstant().toEpochMilli(); } catch (Exception ignored) {}
        try { return Instant.parse(t).toEpochMilli(); } catch (Exception ignored) {}
        var z = zone;
        var sp = t.lastIndexOf(' ');
        if (sp > 0 && Character.isLetter(t.charAt(sp + 1))) {
            var name = t.substring(sp + 1);
            z = ZoneId.of(ABBR.getOrDefault(name.toUpperCase(), name));
            t = t.substring(0, sp).strip();
        }
        t = t.replace(' ', 'T');
        try {
            if (t.length() == 10) return LocalDate.parse(t).atStartOfDay(z).toInstant().toEpochMilli();
            if (t.length() == 16) t += ":00";
            return LocalDateTime.parse(t).atZone(z).toInstant().toEpochMilli();
        } catch (Exception e) {
            Out.die("cannot read the time \"" + s + "\"; use 2026-09-25 21:15 EAT, 2026-09-25, or ISO 2026-09-25T18:15:00Z");
            return 0;
        }
    }
}
