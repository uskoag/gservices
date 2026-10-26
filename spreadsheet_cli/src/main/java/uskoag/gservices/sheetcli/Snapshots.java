package uskoag.gservices.sheetcli;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Snapshots: the whole spreadsheet (values and formulas) saved locally as JSON, one file per moment,
 * under ~/uskoag/gservices/spreadsheet_cli/snapshots/<id>/<UTC stamp>[-label].json.
 */
final class Snapshots {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss'Z'").withZone(ZoneOffset.UTC);

    private Snapshots() {}

    static Path dir(String id) { return Auth.LEGACY.resolve("snapshots").resolve(id); }

    static Path save(Grid g, String label, Path out) throws Exception {
        var file = out != null ? out : dir(g.spreadsheetId()).resolve(STAMP.format(Instant.ofEpochMilli(g.time()))
                + (label == null ? "" : "-" + label.replaceAll("[^A-Za-z0-9_.-]+", "_")) + ".json");
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, Out.COMPACT.toJson(g), StandardCharsets.UTF_8);
        return file;
    }

    static Grid load(Path file) throws Exception {
        if (!Files.exists(file)) Out.die("no snapshot file " + file);
        var g = Out.COMPACT.fromJson(Files.readString(file, StandardCharsets.UTF_8), Grid.class);
        return new Grid(g.spreadsheetId(), g.title(), g.time(), "snapshot " + file.getFileName(), g.tabs());
    }

    /** Oldest first. */
    static List<Path> list(String id) throws Exception {
        var d = dir(id);
        if (!Files.isDirectory(d)) return List.of();
        try (var s = Files.list(d)) { return s.filter(p -> p.toString().endsWith(".json")).sorted().toList(); }
    }

    static Path latest(String id) throws Exception {
        var all = list(id);
        if (all.isEmpty()) Out.die("no snapshot of " + id + " yet. Take one now (uskoag-gsheetscli snapshot " + id
                + "), or compare against another spreadsheet: --from sheet:<id> (in Sheets: File > Version history > a version > Make a copy)");
        return all.getLast();
    }
}
