package uskoag.gservices;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parses global + command flags in any position; everything else is a positional. Mirrors GmailCli's Args. */
final class CalArgs {

    private static final Map<String, String> VALUE_FLAGS = Map.ofEntries(
            Map.entry("--email", "email"), Map.entry("-e", "email"),
            Map.entry("--calendar", "calendar"), Map.entry("-c", "calendar"),
            Map.entry("--from", "from"),
            Map.entry("--to", "to"),
            Map.entry("--days", "days"),
            Map.entry("--query", "query"), Map.entry("-q", "query"),
            Map.entry("--max", "max"), Map.entry("-n", "max"),
            Map.entry("--summary", "summary"), Map.entry("-s", "summary"),
            Map.entry("--start", "start"),
            Map.entry("--end", "end"),
            Map.entry("--location", "location"),
            Map.entry("--description", "description"),
            Map.entry("--attendees", "attendees"),
            Map.entry("--add-attendees", "add-attendees"),
            Map.entry("--remove-attendees", "remove-attendees"),
            Map.entry("--recurrence", "recurrence"),
            Map.entry("--timezone", "timezone"), Map.entry("--tz", "timezone"),
            Map.entry("--role", "role"),
            Map.entry("--send-updates", "send-updates")
    );

    private static final Map<String, String> BOOL_FLAGS = Map.ofEntries(
            Map.entry("--json", "json"),
            Map.entry("--verbose", "verbose"), Map.entry("-v", "verbose"),
            Map.entry("--all-day", "all-day"),
            Map.entry("--no-notify", "no-notify"),
            Map.entry("--expand", "expand"),
            Map.entry("--show-deleted", "show-deleted"),
            Map.entry("--domain", "domain"),
            Map.entry("--public", "public")
    );

    final Map<String, String> values = new LinkedHashMap<>();
    final List<String> positionals = new ArrayList<>();

    CalArgs(String[] argv) {
        for (var i = 0; i < argv.length; i++) {
            var tok = argv[i];
            if (VALUE_FLAGS.containsKey(tok)) {
                if (i + 1 >= argv.length) throw new IllegalArgumentException("Missing value for " + tok);
                values.put(VALUE_FLAGS.get(tok), argv[++i]);
            } else if (BOOL_FLAGS.containsKey(tok)) {
                values.put(BOOL_FLAGS.get(tok), "true");
            } else if (tok.startsWith("-") && tok.length() > 1 && !isNegativeNumber(tok)) {
                throw new IllegalArgumentException("Unknown flag: " + tok);
            } else {
                positionals.add(tok);
            }
        }
    }

    private static boolean isNegativeNumber(String s) {
        return s.matches("-\\d+");
    }

    String get(String name) {
        return values.get(name);
    }

    String get(String name, String def) {
        var v = values.get(name);
        return v == null ? def : v;
    }

    boolean has(String name) {
        return "true".equals(values.get(name));
    }

    long getLong(String name, long def) {
        var v = values.get(name);
        if (v == null) return def;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Comma or newline separated, trimmed, blanks dropped. */
    static List<String> splitCsv(String s) {
        if (s == null || s.isBlank()) return List.of();
        var out = new ArrayList<String>();
        for (var t : s.split("[,\\n]")) {
            var v = t.trim();
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }
}
