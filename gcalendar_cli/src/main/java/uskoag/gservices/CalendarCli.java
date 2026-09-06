package uskoag.gservices;

import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.gson.GsonBuilder;

import xyz.jphil.windows_console_set_unicode_output.WindowsConsoleSetUnicodeOutput;
import xyz.jphil.windows_console_set_unicode_output.WindowsConsoleSetUnicodeOutput.EnableResult;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Command-line Google Calendar companion for Claude Code: calendars, events, invites and sharing,
 * brokered through uskoag-wallet exactly like uskoag-gmailcli and uskoag-gslides. See
 * CLAUDE_CODE_USAGE.md for the full command reference.
 */
public class CalendarCli {

    private static final String APP_NAME = "CalendarCli-v1.0";
    private static final String PROFILE = "calendar";
    private static final Path CREDS_BASE =
            Paths.get(System.getProperty("user.home"), "uskoag", "gservices", "gcalendar_cli");

    static boolean verbose = false;
    static boolean json = false;
    static Calendar calendar;

    public static void main(String[] args) {
        enableUnicodeOutput();
        uskoag.gservices.Caller.record("uskoag-gcalendarcli", args);

        CalArgs a;
        try {
            a = new CalArgs(args);
        } catch (IllegalArgumentException e) {
            logError(e.getMessage());
            System.exit(2);
            return;
        }

        verbose = a.has("verbose");
        json = a.has("json");

        var pos = a.positionals;
        if (pos.isEmpty()) {
            printUsage();
            System.exit(1);
            return;
        }
        var command = pos.get(0).toLowerCase();

        try {
            switch (command) {
                case "help", "--help", "-h" -> { printUsage(); return; }
                case "auth", "login", "profile", "whoami" -> { connect(a); CalendarCalendars.whoami(a); }

                case "calendars" -> { connect(a); CalendarCalendars.list(); }
                case "calendar-create" -> {
                    requirePositional(pos, 1, "calendar-create requires a <summary>");
                    connect(a);
                    CalendarCalendars.create(a, pos.get(1));
                }
                case "calendar-delete" -> {
                    requirePositional(pos, 1, "calendar-delete requires a <calendarId>");
                    connect(a);
                    CalendarCalendars.delete(pos.get(1));
                }
                case "calendar-clear" -> {
                    requirePositional(pos, 1, "calendar-clear requires a <calendarId>");
                    connect(a);
                    CalendarCalendars.clear(pos.get(1));
                }

                case "list" -> { connect(a); CalendarEvents.list(a); }
                case "get" -> {
                    requirePositional(pos, 1, "get requires an <eventId>");
                    connect(a);
                    CalendarEvents.get(a, pos.get(1));
                }
                case "create" -> { connect(a); CalendarEvents.create(a); }
                case "update" -> {
                    requirePositional(pos, 1, "update requires an <eventId>");
                    connect(a);
                    CalendarEvents.update(a, pos.get(1));
                }
                case "delete" -> {
                    requirePositional(pos, 1, "delete requires an <eventId>");
                    connect(a);
                    CalendarEvents.delete(a, pos.get(1));
                }
                case "respond", "rsvp" -> {
                    requirePositional(pos, 2, "respond requires an <eventId> and accepted|declined|tentative");
                    connect(a);
                    CalendarEvents.respond(a, pos.get(1), pos.get(2));
                }

                case "acl" -> {
                    requirePositional(pos, 1, "acl requires a <calendarId>");
                    connect(a);
                    CalendarSharing.list(pos.get(1));
                }
                case "share" -> {
                    requirePositional(pos, 2, "share requires a <calendarId> and an <email>");
                    connect(a);
                    CalendarSharing.share(a, pos.get(1), pos.get(2));
                }
                case "unshare" -> {
                    requirePositional(pos, 2, "unshare requires a <calendarId> and a <ruleId|email>");
                    connect(a);
                    CalendarSharing.unshare(pos.get(1), pos.get(2));
                }
                case "freebusy" -> { connect(a); CalendarSharing.freebusy(a); }

                default -> {
                    logError("Unknown command: " + command);
                    printUsage();
                    System.exit(1);
                }
            }
        } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException ge) {
            handleApiError(ge);
            System.exit(1);
        } catch (Exception e) {
            logError(e.getMessage() == null ? e.toString() : e.getMessage());
            if (verbose) e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void enableUnicodeOutput() {
        var r = WindowsConsoleSetUnicodeOutput.enable();
        if (r instanceof EnableResult.Success || r instanceof EnableResult.AlreadyEnabled) return;
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
    }

    private static void connect(CalArgs a) throws Exception {
        var email = a.get("email");
        if (email == null || email.isBlank()) {
            logError("Missing required --email/-e <email>");
            System.exit(2);
        }
        if (Credentials.discovered().isEmpty()) {
            var credsFile = CREDS_BASE.resolve(email).resolve("credentials.json");
            if (!Files.exists(credsFile)) {
                Files.createDirectories(credsFile.getParent());
                logError("No wallet found and no OAuth client credentials at " + credsFile);
                logError("Start uskoag-wallet (recommended), or place a Desktop-app OAuth client JSON there.");
                System.exit(3);
            }
        }
        var spec = AccessSpec.of("calendar", PROFILE, APP_NAME, email, List.of(CalendarScopes.CALENDAR))
                .legacyRoot(CREDS_BASE.toString());
        var access = Credentials.access(spec, () -> AppKeyPrompt.ask(APP_NAME));
        calendar = CalendarService.calendar(access, APP_NAME);
        logInfo("Authenticated via " + access.sourceName() + ".");
    }

    private static void handleApiError(com.google.api.client.googleapis.json.GoogleJsonResponseException ge) {
        var code = ge.getStatusCode();
        var detail = ge.getDetails() != null && ge.getDetails().getMessage() != null
                ? ge.getDetails().getMessage() : ge.getStatusMessage();
        logError("Calendar API error " + code + (detail != null ? ": " + detail : ""));
        if (verbose) ge.printStackTrace(System.err);
    }

    private static void requirePositional(List<String> pos, int index, String msg) {
        if (pos.size() <= index) {
            logError(msg);
            System.exit(2);
        }
    }

    static void emitJson(Object o) {
        System.out.println(new GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create().toJson(o));
    }

    static String nz(Object o) {
        return o == null ? "" : o.toString();
    }

    static void logInfo(String message) {
        if (verbose) System.err.println("[INFO] " + message);
    }

    static void logError(String message) {
        System.err.println("[ERROR] " + message);
    }

    private static void printUsage() {
        var u = """
                uskoag-gcalendarcli - Google Calendar: calendars, events, invites, sharing

                Usage:
                  uskoag-gcalendarcli <command> [args] --email <you@x.com> [--json] [-v]

                Commands:
                  auth | profile                        Run/verify OAuth, print your primary calendar
                  calendars                              List your calendars (owned + subscribed)
                  calendar-create <summary>               Create a secondary calendar   [--description] [--timezone]
                  calendar-delete <calendarId>            Permanently delete a calendar and every event on it
                  calendar-clear <calendarId>             Erase every event on a calendar (keeps the calendar)

                  list                                    List events in a bounded window
                        [--calendar <id>] [--from <date>] [--to <date>] [--days <n>] [--query <text>]
                        [--max <n>] [--expand] [--show-deleted]
                        A recurring series is ONE row (its recurrence rule shown) unless --expand,
                        which lists individual instances and tags each with its recurringEventId.
                  get <eventId>                           Full event details              [--calendar <id>]
                  create                                  Create an event
                        --summary <text> --start <date|datetime> --end <date|datetime>
                        [--calendar <id>] [--all-day] [--timezone <tz>] [--location <text>]
                        [--description <text>] [--attendees <a@x,b@y>] [--recurrence "<RRULE>;..."]
                        [--send-updates all|externalOnly|none] [--no-notify]
                  update <eventId>                        Patch an event                  [--calendar <id>]
                        [--summary] [--start] [--end] [--location] [--description]
                        [--add-attendees <a,b>] [--remove-attendees <a,b>] [--no-notify]
                  delete <eventId>                        Permanently delete an event      [--calendar <id>] [--no-notify]
                  respond <eventId> <accepted|declined|tentative>   Your own RSVP           [--calendar <id>]

                  acl <calendarId>                        List who a calendar is shared with
                  share <calendarId> <email>               Share a calendar   [--role reader|writer|owner|freeBusyReader]
                                                           [--domain (value is a domain, not an email)] [--public]
                  unshare <calendarId> <ruleId|email>      Remove someone's access
                  freebusy --calendars <a,b> --from <date> --to <date>   Check free/busy across calendars

                Global flags:
                  --email, -e <email>     Google account (required)
                  --calendar, -c <id>     Calendar id (default: primary)
                  --json                  Machine-readable JSON output
                  --verbose, -v           Log progress to stderr

                Dates: "2026-09-10" (all-day) or "2026-09-10T14:00:00+03:00" (timed, RFC 3339).
                """;
        System.err.println(u);
    }
}
