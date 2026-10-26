package uskoag.gservices.sheetcli;

import com.google.api.services.sheets.v4.model.*;
import java.util.*;
import uskoag.gservices.CellRange;

/**
 * protect <id> <tab> [<range>] [--editors a,b] [--warning-only] [--description d] [--unprotected-ranges "B2:B,D2:D"] [--apply]
 * protect --list <id> [--json]      protect --remove <id> <protectedRangeId> [--apply]
 * Dry run unless --apply. The same command run twice leaves one protection: an existing one over the same
 * tab and range is updated in place, or left alone when it already says exactly this.
 */
final class VerbProtect {

    private VerbProtect() {}

    static void run(List<String> a) throws Exception {
        var json = Args.flag(a, "--json");
        var apply = Args.flag(a, "--apply");
        if (Args.flag(a, "--list")) { list(Args.sheetId(Args.req(a, "<spreadsheetId>")), json); Args.noneLeft(a, "protect --list"); return; }
        if (Args.flag(a, "--remove")) {
            var id = Args.sheetId(Args.req(a, "<spreadsheetId>"));
            var pr = Args.req(a, "<protectedRangeId>");
            Args.noneLeft(a, "protect --remove");
            ProtectRemove.run(id, Integer.parseInt(pr.replace("#", "")), apply);
            return;
        }
        var editors = Args.val(a, "--editors");
        var warning = Args.flag(a, "--warning-only");
        var description = Args.val(a, "--description");
        var except = Args.val(a, "--unprotected-ranges", "--except");
        var id = Args.sheetId(Args.req(a, "<spreadsheetId>"));
        var tab = Args.req(a, "<tab>");
        var range = Args.opt(a);
        Args.noneLeft(a, "protect");
        if (warning && editors != null) Out.die("--warning-only lets anyone edit after a prompt, so it takes no --editors");
        if (except != null && range != null) Out.die("--unprotected-ranges goes with a whole tab: drop the range, or protect the range alone");
        if (Auth.email == null) Out.die("protect needs -e <account>: it is always written in as an editor, so it cannot lock itself out");

        var tabs = Protections.tabs(id);
        var sheetId = tabs.get(tab);
        if (sheetId == null) Out.die("no tab \"" + tab + "\"; tabs: " + String.join(", ", tabs.keySet()));
        var want = new ProtectedRange()
                .setRange(range == null ? new GridRange().setSheetId(sheetId) : CellRange.toGridRange(sheetId, range))
                .setDescription(description == null ? "" : description)
                .setWarningOnly(warning);
        if (!warning) want.setEditors(new Editors().setUsers(editorList(id, editors)));
        if (except != null) {
            var ex = new ArrayList<GridRange>();
            for (var r : except.split("[,;]")) if (!r.isBlank()) ex.add(CellRange.toGridRange(sheetId, r.strip()));
            want.setUnprotectedRanges(ex);
        }
        ProtectApply.run(id, tab, want, apply, json);
    }

    /** Whoever was named, plus the account running this, plus the file's owners: never locks out the people who must keep access. */
    private static List<String> editorList(String id, String editors) {
        var set = new TreeSet<String>();
        if (editors != null) for (var e : editors.split("[,;\\s]+")) if (!e.isBlank()) set.add(e.strip().toLowerCase());
        set.add(Auth.email.toLowerCase());
        set.addAll(Protections.owners(id));
        return new ArrayList<>(set);
    }

    private static void list(String id, boolean json) throws Exception {
        var all = Protections.all(id);
        if (json) { Out.json(all.stream().map(f -> Protections.json(f.tab(), f.pr())).toList()); return; }
        for (var f : all) Out.data(Protections.show(f.tab(), f.pr()));
        Out.note("(" + all.size() + " protection(s))");
    }
}
