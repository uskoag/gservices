package uskoag.gservices.slides;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Mutating arg-list helpers, matching uskoag-gsheetscli's parsing style. */
public final class Args {

    private Args() {}

    /** Remove every occurrence of any given flag; true if one was present. */
    static boolean flag(List<String> args, String... names) {
        var found = false;
        for (var n : names) while (args.remove(n)) found = true;
        return found;
    }

    /** Remove a `--flag value` pair and return the value, else null. */
    static String val(List<String> args, String... names) {
        for (var n : names) {
            var i = args.indexOf(n);
            if (i >= 0 && i + 1 < args.size()) { args.remove(i); return args.remove(i); }
        }
        return null;
    }

    /** Deliberately NOT an overload of val: val(args,"--email","-e") would bind here instead. */
    static String valOr(List<String> args, String name, String fallback) {
        var v = val(args, name);
        return v != null ? v : fallback;
    }

    static Integer intVal(List<String> args, String... names) {
        var v = val(args, names);
        return v == null ? null : Integer.valueOf(v.trim());
    }

    static Double dblVal(List<String> args, String... names) {
        var v = val(args, names);
        return v == null ? null : Double.valueOf(v.trim());
    }

    /** First remaining positional (not starting with --), removed from the list. */
    static String pos(List<String> args) {
        for (var i = 0; i < args.size(); i++)
            if (!args.get(i).startsWith("--")) return args.remove(i);
        return null;
    }

    static String req(List<String> args, String what) {
        var v = pos(args);
        if (v == null) Out.die("missing required argument: " + what);
        return v;
    }

    /**
     * Text either inline or from a file. A long Devanagari paragraph through PowerShell
     * quoting is the failure the file form exists to prevent.
     */
    static String text(List<String> args, String inlineFlag, String fileFlag) {
        var file = val(args, fileFlag);
        if (file != null) {
            try { return Files.readString(Path.of(file)); }
            catch (Exception e) { Out.die("cannot read " + fileFlag + " " + file + ": " + e.getMessage()); }
        }
        return val(args, inlineFlag);
    }

    static void noneLeft(List<String> args, String verb) {
        if (!args.isEmpty()) Out.die(verb + ": unrecognised argument(s): " + String.join(" ", args));
    }
}
