package uskoag.gservices.sheetcli;

import java.nio.file.*;

/**
 * What --from and --to accept, read into a Grid:
 *   current            the spreadsheet as it is now (default --to)
 *   snapshot | latest  the newest local snapshot of it (default --from)
 *   <file>.json        a snapshot file
 *   sheet:<id|url>     another spreadsheet, typically a copy of an old version (Version history > Make a copy)
 * A Drive revision (#n, a revision number, a time) is refused: see REVISIONS_BLOCKED.
 */
final class Source {

    static final String REVISIONS_BLOCKED = "reading an earlier saved version is not available yet: Google serves a spreadsheet revision only from "
            + "docs.google.com/spreadsheets/export?id=..&revision=N&exportFormat=xlsx, and the wallet's docsexport front treats that path as "
            + "irreversible (\"a non-export request on the docs export host\"); it accepts the Docs path only. Until the wallet reads it as a read: "
            + "in Sheets open File > Version history, pick the version, \"Make a copy\", and pass --from sheet:<id of the copy>; "
            + "or take a snapshot now and diff against it later";

    private Source() {}

    static Grid read(String spec, String id, String onlyTab) throws Exception {
        var s = spec.strip();
        if (s.equals("current") || s.equals("now")) return GridLoad.live(id, onlyTab, "current");
        if (s.equals("snapshot") || s.equals("latest")) return Snapshots.load(Snapshots.latest(id));
        if (s.startsWith("sheet:")) {
            var other = Args.sheetId(s.substring(6));
            var g = GridLoad.live(other, onlyTab, "spreadsheet " + other);
            return new Grid(other, g.title(), 0, "spreadsheet \"" + g.title() + "\" (" + other + ")", g.tabs());
        }
        if (s.endsWith(".json")) return Snapshots.load(Path.of(s));
        var inDir = Snapshots.dir(id).resolve(s.endsWith(".json") ? s : s + ".json");
        if (Files.exists(inDir)) return Snapshots.load(inDir);
        if (s.startsWith("#") || s.matches("\\d+") || s.matches("\\d{4}-\\d{2}-\\d{2}.*") || s.startsWith("r")) Out.die(REVISIONS_BLOCKED);
        Out.die("--from/--to: \"" + spec + "\" is not current, snapshot, a snapshot file, or sheet:<id>");
        return null;
    }
}
