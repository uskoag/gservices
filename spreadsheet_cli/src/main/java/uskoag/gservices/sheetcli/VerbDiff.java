package uskoag.gservices.sheetcli;

import java.util.List;

/**
 * diff <id> [--from <src>] [--to <src>] [--sheet <tab>] [--formulas] [--from-time <t>] [--limit n] [--stat] [--json]
 * What changed between two versions: structure first (it is what breaks formulas), then cells by tab,
 * then who edited in between according to Drive Activity.
 */
final class VerbDiff {

    private VerbDiff() {}

    static void run(List<String> a) throws Exception {
        var json = Args.flag(a, "--json");
        var stat = Args.flag(a, "--stat");
        var formulas = Args.flag(a, "--formulas");
        var noWho = Args.flag(a, "--no-who");
        var from = Args.val(a, "--from");
        var to = Args.val(a, "--to");
        var tab = Args.val(a, "--sheet", "--tab");
        var fromTime = Args.val(a, "--from-time");
        var limit = Args.val(a, "--limit");
        var id = Args.sheetId(Args.req(a, "<spreadsheetId>"));
        Args.noneLeft(a, "diff");

        var older = Source.read(from == null ? "snapshot" : from, id, tab);
        var newer = Source.read(to == null ? "current" : to, id, tab);
        if (fromTime != null) older = new Grid(older.spreadsheetId(), older.title(), Times.parse(fromTime), older.source(), older.tabs());
        var d = GridDiff.of(older, newer, tab, formulas);
        var who = noWho || older.time() == 0 || newer.time() == 0 || older.time() >= newer.time() ? null : People.in(id, older.time(), newer.time());
        var cap = limit == null ? 200 : Integer.parseInt(limit);
        if (json) Out.json(DiffReport.json(d, who));
        else DiffReport.text(d, who, stat, cap);
        if (older.time() == 0 && !noWho)
            Out.note("(no time is known for " + older.source() + ", so nobody is named; give --from-time \"<when that version was saved>\" to ask Drive Activity)");
        if (Align.positionalFallback) Out.note("(a very large changed region was matched by position, not content: inserted rows or columns inside it may show as cell changes)");
    }
}
