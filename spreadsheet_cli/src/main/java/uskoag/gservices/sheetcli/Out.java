package uskoag.gservices.sheetcli;

import com.google.gson.*;

/** stdout carries the answer, stderr carries everything about getting it. */
final class Out {

    static boolean verbose, quiet;
    static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().serializeNulls().create(),
            COMPACT = new GsonBuilder().disableHtmlEscaping().create();

    private Out() {}

    static void data(String s) { System.out.println(s); }

    static void json(Object o) { System.out.println(GSON.toJson(o)); }

    static void info(String s) { if (verbose) System.err.println("[INFO] " + s); }

    static void note(String s) { if (!quiet) System.err.println(s); }

    static void die(String s) { throw new CliError(s); }
}
