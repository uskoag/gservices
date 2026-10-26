package uskoag.gservices.gdocsmon;

import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

/** Where the daemon keeps its port file and log: ~/uskoag/gservices/gdocs-monitor. */
final class Home {

    static final Path DIR = Path.of(System.getProperty("user.home"), "uskoag", "gservices", "gdocs-monitor"),
            PORT = DIR.resolve("daemon.port"),
            LOG = DIR.resolve("monitor.log"),
            POLL_ERR = DIR.resolve("last-poll.stderr.txt");

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    private Home() {}

    static String stamp(long epochMs) { return STAMP.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())); }

    static String now() { return stamp(System.currentTimeMillis()); }

    static synchronized void log(String line) {
        try {
            Files.createDirectories(DIR);
            Files.writeString(LOG, now() + "  " + line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {}
    }

    /** The gdocscli jar the daemon polls with: UKAG_GDOCSCLI_JAR, else the sibling module's build next to this jar. */
    static Path cliJar() throws Exception {
        var env = System.getenv("UKAG_GDOCSCLI_JAR");
        if (env != null && !env.isBlank()) return Path.of(env);
        var me = Path.of(Home.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return me.getParent().getParent().getParent().resolve("gdocs_cli").resolve("target").resolve("uskoag-gdocscli.jar");
    }

    static Path ownJar() throws Exception { return Path.of(Home.class.getProtectionDomain().getCodeSource().getLocation().toURI()); }

    static Path javaw() { return Path.of(System.getProperty("java.home"), "bin", "javaw.exe"); }

    static Path java() { return Path.of(System.getProperty("java.home"), "bin", "java.exe"); }
}
