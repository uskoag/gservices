package uskoag.gservices;

import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static uskoag.gservices.CalendarCli.*;

/** Event commands: list, get, create, update, delete, respond (RSVP). */
final class CalendarEvents {

    private CalendarEvents() {
    }

    private static String calendarId(CalArgs a) {
        return a.get("calendar", "primary");
    }

    /**
     * Bounded by design: {@code singleEvents=false} (the default) returns one row per recurring series —
     * the master event carrying its {@code recurrence} rule — never every future occurrence. {@code
     * --expand} switches to individual instances, each tagged with its {@code recurringEventId} so it
     * still reads as one series rather than unrelated rows.
     */
    static void list(CalArgs a) throws Exception {
        var calId = calendarId(a);
        var from = a.get("from") != null ? CalTime.asTimestamp(a.get("from")) : CalTime.now();
        var to = a.get("to") != null ? CalTime.asTimestamp(a.get("to")) : CalTime.plusDays(from, a.getLong("days", 30));
        var expand = a.has("expand");

        var req = calendar.events().list(calId)
                .setTimeMin(from).setTimeMax(to)
                .setSingleEvents(expand)
                .setMaxResults((int) a.getLong("max", 50))
                .setShowDeleted(a.has("show-deleted"));
        if (expand) req.setOrderBy("startTime");
        if (a.get("query") != null) req.setQ(a.get("query"));

        var items = req.execute().getItems();
        if (items == null) items = List.of();

        if (json) {
            emitJson(items.stream().map(CalendarEvents::eventMap).toList());
            return;
        }
        for (var e : items) {
            System.out.println(e.getId() + "\t" + when(e) + "\t" + nz(e.getSummary()));
            if (e.getRecurringEventId() != null) System.out.println("    (instance of series " + e.getRecurringEventId() + ")");
            else if (e.getRecurrence() != null && !e.getRecurrence().isEmpty()) System.out.println("    [recurring: " + String.join("; ", e.getRecurrence()) + "]");
        }
        logInfo(items.size() + " event(s) on " + calId + " between " + CalTime.display(from) + " and " + CalTime.display(to));
    }

    static void get(CalArgs a, String eventId) throws Exception {
        var e = calendar.events().get(calendarId(a), eventId).execute();
        if (json) {
            emitJson(eventMap(e));
            return;
        }
        System.out.println("id:          " + e.getId());
        System.out.println("summary:     " + nz(e.getSummary()));
        System.out.println("when:        " + when(e));
        System.out.println("location:    " + nz(e.getLocation()));
        System.out.println("status:      " + nz(e.getStatus()));
        if (e.getRecurrence() != null) System.out.println("recurrence:  " + String.join("; ", e.getRecurrence()));
        if (e.getRecurringEventId() != null) System.out.println("series:      " + e.getRecurringEventId());
        if (e.getDescription() != null) System.out.println("description: " + e.getDescription());
        var attendees = e.getAttendees();
        if (attendees != null) for (var at : attendees) {
            System.out.println("attendee:    " + at.getEmail() + "  (" + nz(at.getResponseStatus())
                    + (Boolean.TRUE.equals(at.getSelf()) ? ", you" : "") + ")");
        }
    }

    static void create(CalArgs a) throws Exception {
        var summary = a.get("summary");
        var start = a.get("start");
        var end = a.get("end");
        if (summary == null || start == null || end == null) {
            logError("create requires --summary, --start and --end");
            System.exit(2);
        }
        var body = new Event().setSummary(summary)
                .setStart(eventDateTime(start, a.get("timezone")))
                .setEnd(eventDateTime(end, a.get("timezone")));
        if (a.get("location") != null) body.setLocation(a.get("location"));
        if (a.get("description") != null) body.setDescription(a.get("description"));
        if (a.get("recurrence") != null) body.setRecurrence(List.of(a.get("recurrence").split(";")));
        var attendeeEmails = CalArgs.splitCsv(a.get("attendees"));
        if (!attendeeEmails.isEmpty()) body.setAttendees(attendees(attendeeEmails));

        var created = calendar.events().insert(calendarId(a), body)
                .setSendUpdates(sendUpdates(a))
                .execute();
        if (json) {
            emitJson(eventMap(created));
        } else {
            System.out.println("SUCCESS: created event " + created.getId());
            System.out.println("when: " + when(created));
            if (!attendeeEmails.isEmpty()) System.out.println("invited: " + String.join(", ", attendeeEmails));
        }
    }

    static void update(CalArgs a, String eventId) throws Exception {
        var calId = calendarId(a);
        var existing = calendar.events().get(calId, eventId).execute();

        if (a.get("summary") != null) existing.setSummary(a.get("summary"));
        if (a.get("start") != null) existing.setStart(eventDateTime(a.get("start"), a.get("timezone")));
        if (a.get("end") != null) existing.setEnd(eventDateTime(a.get("end"), a.get("timezone")));
        if (a.get("location") != null) existing.setLocation(a.get("location"));
        if (a.get("description") != null) existing.setDescription(a.get("description"));

        var toAdd = CalArgs.splitCsv(a.get("add-attendees"));
        var toRemove = CalArgs.splitCsv(a.get("remove-attendees"));
        if (!toAdd.isEmpty() || !toRemove.isEmpty()) existing.setAttendees(mergeAttendees(existing.getAttendees(), toAdd, toRemove));

        var updated = calendar.events().patch(calId, eventId, existing)
                .setSendUpdates(sendUpdates(a))
                .execute();
        if (json) {
            emitJson(eventMap(updated));
        } else {
            System.out.println("SUCCESS: updated event " + updated.getId());
            if (!toAdd.isEmpty()) System.out.println("invited: " + String.join(", ", toAdd));
        }
    }

    static void delete(CalArgs a, String eventId) throws Exception {
        calendar.events().delete(calendarId(a), eventId).setSendUpdates(sendUpdates(a)).execute();
        System.out.println("SUCCESS: permanently deleted event " + eventId);
    }

    /** Own RSVP: patch just the caller's own attendee entry, found by {@code self} first and email otherwise. */
    static void respond(CalArgs a, String eventId, String status) throws Exception {
        var s = status.toLowerCase();
        if (!List.of("accepted", "declined", "tentative", "needsaction").contains(s)) {
            logError("status must be one of: accepted, declined, tentative");
            System.exit(2);
        }
        var calId = calendarId(a);
        var existing = calendar.events().get(calId, eventId).execute();
        var attendees = existing.getAttendees();
        if (attendees == null) {
            logError("this event has no attendees to respond as");
            System.exit(1);
        }
        var email = a.get("email");
        var found = false;
        for (var at : attendees) {
            if (Boolean.TRUE.equals(at.getSelf()) || (email != null && email.equalsIgnoreCase(at.getEmail()))) {
                at.setResponseStatus(s);
                found = true;
            }
        }
        if (!found) {
            logError("could not find your own attendee entry on this event");
            System.exit(1);
        }
        existing.setAttendees(attendees);
        calendar.events().patch(calId, eventId, existing).setSendUpdates(sendUpdates(a)).execute();
        System.out.println("SUCCESS: responded " + s + " to event " + eventId);
    }

    private static List<EventAttendee> mergeAttendees(List<EventAttendee> current, List<String> add, List<String> remove) {
        var out = new ArrayList<EventAttendee>(current == null ? List.of() : current);
        out.removeIf(at -> remove.stream().anyMatch(r -> r.equalsIgnoreCase(at.getEmail())));
        for (var email : add) {
            if (out.stream().noneMatch(at -> email.equalsIgnoreCase(at.getEmail()))) {
                out.add(new EventAttendee().setEmail(email));
            }
        }
        return out;
    }

    private static List<EventAttendee> attendees(List<String> emails) {
        return emails.stream().map(e -> new EventAttendee().setEmail(e)).toList();
    }

    private static EventDateTime eventDateTime(String value, String timezone) {
        var dt = new EventDateTime();
        if (CalTime.isDateOnly(value)) dt.setDate(CalTime.parse(value));
        else dt.setDateTime(CalTime.parse(value));
        if (timezone != null) dt.setTimeZone(timezone);
        return dt;
    }

    /** Google emails attendees off this; {@code --no-notify} is the escape hatch for a quiet edit. */
    private static String sendUpdates(CalArgs a) {
        if (a.has("no-notify")) return "none";
        return a.get("send-updates", "all");
    }

    private static String edtDisplay(EventDateTime dt) {
        if (dt == null) return null;
        return dt.getDate() != null ? CalTime.display(dt.getDate()) : CalTime.display(dt.getDateTime());
    }

    private static String when(Event e) {
        return nz(edtDisplay(e.getStart())) + " -> " + nz(edtDisplay(e.getEnd()));
    }

    private static Map<String, Object> eventMap(Event e) {
        var m = new LinkedHashMap<String, Object>();
        m.put("id", e.getId());
        m.put("summary", e.getSummary());
        m.put("start", edtDisplay(e.getStart()));
        m.put("end", edtDisplay(e.getEnd()));
        m.put("location", e.getLocation());
        m.put("status", e.getStatus());
        m.put("recurringEventId", e.getRecurringEventId());
        m.put("recurrence", e.getRecurrence());
        if (e.getAttendees() != null) {
            m.put("attendees", e.getAttendees().stream()
                    .map(at -> Map.of("email", nz(at.getEmail()), "responseStatus", nz(at.getResponseStatus())))
                    .toList());
        }
        return m;
    }
}
