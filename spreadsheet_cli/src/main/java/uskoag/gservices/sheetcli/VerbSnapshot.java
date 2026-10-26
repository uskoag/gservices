package uskoag.gservices.sheetcli;

import java.nio.file.*;
import java.util.List;

/** snapshot <id> [--label x] [--out file] — save every tab's values and formulas now; snapshot --list <id>. */
final class VerbSnapshot {

    private VerbSnapshot() {}

    static void run(List<String> a) throws Exception {
        var list = Args.flag(a, "--list");
        var label = Args.val(a, "--label");
        var out = Args.val(a, "--out");
        var id = Args.sheetId(Args.req(a, "<spreadsheetId>"));
        Args.noneLeft(a, "snapshot");
        if (list) {
            var all = Snapshots.list(id);
            for (var p : all) {
                var g = Snapshots.load(p);
                Out.data(Times.show(g.time()) + "  " + g.tabs().size() + " tab(s)  " + p);
            }
            Out.note("(" + all.size() + " snapshot(s) in " + Snapshots.dir(id) + ")");
            return;
        }
        var g = GridLoad.live(id, null, "current");
        var file = Snapshots.save(g, label, out == null ? null : Path.of(out));
        var cells = 0;
        for (var t : g.tabs()) cells += t.values().stream().mapToInt(r -> (int) r.stream().filter(c -> !c.isEmpty()).count()).sum();
        Out.data(file.toString());
        Out.note("snapshot of \"" + g.title() + "\" at " + Times.show(g.time()) + ": " + g.tabs().size() + " tab(s), " + cells
                + " non-empty cell(s). Compare later: uskoag-gsheetscli diff " + id + "   (from the latest snapshot to now)");
    }
}
