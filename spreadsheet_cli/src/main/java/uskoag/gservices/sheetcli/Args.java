package uskoag.gservices.sheetcli;

import java.util.List;

/** Flags are taken out of the list as they are read, so whatever is left over is an error worth naming. */
final class Args {

    private Args() {}

    static boolean flag(List<String> a, String... names) {
        var hit = false;
        for (var n : names) while (a.remove(n)) hit = true;
        return hit;
    }

    static String val(List<String> a, String... names) {
        for (var n : names) {
            var i = a.indexOf(n);
            if (i >= 0) {
                if (i + 1 >= a.size()) Out.die(n + " needs a value");
                a.remove(i);
                return a.remove(i);
            }
            for (var j = 0; j < a.size(); j++) if (a.get(j).startsWith(n + "=")) return a.remove(j).substring(n.length() + 1);
        }
        return null;
    }

    static String req(List<String> a, String what) {
        if (a.isEmpty() || a.getFirst().startsWith("--")) Out.die("missing " + what);
        return a.removeFirst();
    }

    static String opt(List<String> a) { return a.isEmpty() || a.getFirst().startsWith("--") ? null : a.removeFirst(); }

    static void noneLeft(List<String> a, String verb) { if (!a.isEmpty()) Out.die(verb + ": unexpected " + String.join(" ", a)); }

    /** A bare id or any Sheets URL. */
    static String sheetId(String s) {
        var m = java.util.regex.Pattern.compile("/spreadsheets/d/([A-Za-z0-9_-]+)").matcher(s);
        return m.find() ? m.group(1) : s;
    }
}
