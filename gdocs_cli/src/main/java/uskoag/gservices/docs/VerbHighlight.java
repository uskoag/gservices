package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.*;
import java.util.List;

/**
 * highlight — set a background (and optionally text) colour on text already in a tab, found the way `link`
 * finds it. --para colours the whole paragraph each hit sits in. Only those colour fields are written.
 */
final class VerbHighlight {

    private VerbHighlight() {}

    static void run(List<String> a) throws Exception {
        var apply = Args.flag(a, "--apply");
        var every = Args.flag(a, "--all");
        var para = Args.flag(a, "--para");
        var tabSel = Args.val(a, "--tab", "-t");
        var find = Args.val(a, "--find");
        var in = Args.val(a, "--in");
        var bg = Args.val(a, "--bg");
        var fg = Args.val(a, "--color");
        var id = DocRef.id(Args.req(a, "<doc>"));
        Args.noneLeft(a, "highlight");
        if (find == null || find.isEmpty()) Out.die("highlight needs --find \"<exact text>\"");
        if (tabSel == null) Out.die("highlight needs --tab <sel>");
        if (bg == null && fg == null) Out.die("highlight needs --bg #RRGGBB and/or --color #RRGGBB");

        var doc = DocRef.get(id);
        Links.tabs = Tabs.flat(doc);
        var tabs = Tabs.select(Links.tabs, tabSel);
        if (tabs.size() != 1) Out.die("--tab must name exactly one tab");
        var s = new Scope(tabs.get(0), Para.of(tabs.get(0).getDocumentTab()));
        var within = in == null ? null : s.anchor("highlight --in", in);
        var hits = s.occurrences(find, within);
        if (hits.isEmpty()) Out.die("\"" + find + "\" does not occur in tab " + s.name());
        if (hits.size() > 1 && !every) Out.die("\"" + find + "\" occurs " + hits.size() + " times; pass --all");

        var style = new TextStyle(); var fields = new ArrayList<String>();
        if (bg != null) { style.setBackgroundColor(color(bg)); fields.add("backgroundColor"); }
        if (fg != null) { style.setForegroundColor(color(fg)); fields.add("foregroundColor"); }
        var reqs = new ArrayList<Request>();
        for (var at : hits) {
            var p = s.paraAt(at);
            int from = para ? p.start() : at, to = para ? p.end() - 1 : at + find.length();
            Out.data("highlight " + BuildReplace.around(p.plain(), from - p.start(), Math.min(to - p.start(), p.plain().length())));
            reqs.add(new Request().setUpdateTextStyle(new UpdateTextStyleRequest().setTextStyle(style).setFields(String.join(",", fields))
                    .setRange(new Range().setStartIndex(from).setEndIndex(to).setTabId(s.tabId()))));
        }
        var batch = new BatchUpdateDocumentRequest().setRequests(reqs).setWriteControl(new WriteControl().setRequiredRevisionId(doc.getRevisionId()));
        if (!apply) { Out.data("DRY RUN — " + hits.size() + " span(s). Nothing sent; re-run with --apply."); return; }
        Journal.around(id, "highlight", "highlight \"" + find + "\" in " + s.name(), () -> Auth.docs().documents().batchUpdate(id, batch).execute());
        Out.success(hits.size() + " span(s) highlighted");
    }

    private static OptionalColor color(String hex) {
        var h = hex.startsWith("#") ? hex.substring(1) : hex;
        var rgb = new RgbColor().setRed(Integer.parseInt(h.substring(0, 2), 16) / 255f)
                .setGreen(Integer.parseInt(h.substring(2, 4), 16) / 255f).setBlue(Integer.parseInt(h.substring(4, 6), 16) / 255f);
        return new OptionalColor().setColor(new Color().setRgbColor(rgb));
    }
}
