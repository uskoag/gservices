package uskoag.gservices;

import com.google.api.services.calendar.model.CalendarListEntry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static uskoag.gservices.CalendarCli.*;

/** Calendar-level commands: whoami, the calendar list, create/delete/clear. */
final class CalendarCalendars {

    private CalendarCalendars() {
    }

    static void whoami(CalArgs a) throws Exception {
        var entry = calendar.calendarList().get("primary").execute();
        if (json) {
            emitJson(entryMap(entry));
        } else {
            System.out.println("Calendar: " + nz(entry.getSummary()) + "  (" + entry.getId() + ")");
            System.out.println("Timezone: " + nz(entry.getTimeZone()));
            System.out.println("Access:   " + nz(entry.getAccessRole()));
        }
    }

    static void list() throws Exception {
        var resp = calendar.calendarList().list().execute();
        var items = resp.getItems() == null ? List.<CalendarListEntry>of() : resp.getItems();
        if (json) {
            emitJson(items.stream().map(CalendarCalendars::entryMap).toList());
            return;
        }
        for (var e : items) {
            System.out.println(e.getId() + "\t" + nz(e.getSummary())
                    + (Boolean.TRUE.equals(e.getPrimary()) ? "  [primary]" : "")
                    + "  (" + nz(e.getAccessRole()) + ")");
        }
        logInfo(items.size() + " calendar(s)");
    }

    static void create(CalArgs a, String summary) throws Exception {
        var body = new com.google.api.services.calendar.model.Calendar().setSummary(summary);
        if (a.get("description") != null) body.setDescription(a.get("description"));
        if (a.get("timezone") != null) body.setTimeZone(a.get("timezone"));
        var created = calendar.calendars().insert(body).execute();
        if (json) {
            emitJson(Map.of("id", created.getId(), "summary", nz(created.getSummary())));
        } else {
            System.out.println("SUCCESS: created calendar " + created.getId() + "  (" + created.getSummary() + ")");
        }
    }

    static void delete(String calendarId) throws Exception {
        calendar.calendars().delete(calendarId).execute();
        System.out.println("SUCCESS: permanently deleted calendar " + calendarId + " and every event on it");
    }

    static void clear(String calendarId) throws Exception {
        calendar.calendars().clear(calendarId).execute();
        System.out.println("SUCCESS: erased every event on calendar " + calendarId);
    }

    private static Map<String, Object> entryMap(CalendarListEntry e) {
        var m = new LinkedHashMap<String, Object>();
        m.put("id", e.getId());
        m.put("summary", e.getSummary());
        m.put("primary", e.getPrimary());
        m.put("accessRole", e.getAccessRole());
        m.put("timeZone", e.getTimeZone());
        return m;
    }
}
