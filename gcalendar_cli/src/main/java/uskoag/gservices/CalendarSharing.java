package uskoag.gservices;

import com.google.api.services.calendar.model.AclRule;
import com.google.api.services.calendar.model.FreeBusyCalendar;
import com.google.api.services.calendar.model.FreeBusyRequest;
import com.google.api.services.calendar.model.FreeBusyRequestItem;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static uskoag.gservices.CalendarCli.*;

/** Sharing (ACL) and free/busy commands. */
final class CalendarSharing {

    private CalendarSharing() {
    }

    static void list(String calendarId) throws Exception {
        var rules = calendar.acl().list(calendarId).execute().getItems();
        if (rules == null) rules = List.of();
        if (json) {
            emitJson(rules.stream().map(CalendarSharing::ruleMap).toList());
            return;
        }
        for (var r : rules) {
            var scope = r.getScope();
            System.out.println(r.getId() + "\t" + r.getRole() + "\t"
                    + (scope == null ? "" : scope.getType() + ":" + nz(scope.getValue())));
        }
        logInfo(rules.size() + " sharing rule(s) on " + calendarId);
    }

    /** Sharing is destructive on the wallet side, same reasoning as Drive permissions.insert: granting
     *  visibility is the standing-risk direction and un-sharing later does not retract what was seen. */
    static void share(CalArgs a, String calendarId, String who) throws Exception {
        var type = a.has("domain") ? "domain" : a.has("public") ? "default" : "user";
        var scope = new AclRule.Scope().setType(type);
        if (!"default".equals(type)) scope.setValue(who);
        var role = a.get("role", "reader");
        var rule = new AclRule().setRole(role).setScope(scope);
        var created = calendar.acl().insert(calendarId, rule).execute();
        System.out.println("SUCCESS: shared calendar " + calendarId + " with " + type + ":" + nz(scope.getValue())
                + " as " + role + "  (ruleId=" + created.getId() + ")");
    }

    static void unshare(String calendarId, String ruleIdOrEmail) throws Exception {
        var ruleId = resolveRuleId(calendarId, ruleIdOrEmail);
        calendar.acl().delete(calendarId, ruleId).execute();
        System.out.println("SUCCESS: removed access rule " + ruleId + " from calendar " + calendarId);
    }

    /** Accepts either the bare ACL ruleId or the email it was granted to, so a caller need not have kept the ruleId. */
    private static String resolveRuleId(String calendarId, String ruleIdOrEmail) throws Exception {
        var rules = calendar.acl().list(calendarId).execute().getItems();
        if (rules != null) for (var r : rules) {
            if (ruleIdOrEmail.equals(r.getId())) return r.getId();
            if (r.getScope() != null && ruleIdOrEmail.equalsIgnoreCase(r.getScope().getValue())) return r.getId();
        }
        return ruleIdOrEmail;
    }

    static void freebusy(CalArgs a) throws Exception {
        var ids = CalArgs.splitCsv(a.get("calendar") != null ? a.get("calendar") : a.get("calendars"));
        if (ids.isEmpty()) ids = List.of("primary");
        var from = a.get("from");
        var to = a.get("to");
        if (from == null || to == null) {
            logError("freebusy requires --from and --to");
            System.exit(2);
        }
        var req = new FreeBusyRequest()
                .setTimeMin(CalTime.asTimestamp(from))
                .setTimeMax(CalTime.asTimestamp(to))
                .setItems(ids.stream().map(id -> new FreeBusyRequestItem().setId(id)).toList());
        var resp = calendar.freebusy().query(req).execute();

        @SuppressWarnings("unchecked")
        var calendars = (Map<String, FreeBusyCalendar>) resp.getCalendars();
        if (json) {
            var out = new LinkedHashMap<String, Object>();
            calendars.forEach((id, fb) -> out.put(id, fb.getBusy()));
            emitJson(out);
            return;
        }
        calendars.forEach((id, fb) -> {
            System.out.println(id + ":");
            var busy = fb.getBusy();
            if (busy == null || busy.isEmpty()) System.out.println("    (free the whole window)");
            else for (var period : busy) System.out.println("    busy " + period.getStart() + " -> " + period.getEnd());
        });
    }

    private static Map<String, Object> ruleMap(AclRule r) {
        var m = new LinkedHashMap<String, Object>();
        m.put("id", r.getId());
        m.put("role", r.getRole());
        m.put("scopeType", r.getScope() == null ? null : r.getScope().getType());
        m.put("scopeValue", r.getScope() == null ? null : r.getScope().getValue());
        return m;
    }
}
