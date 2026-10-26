package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.ArrayList;
import java.util.List;

/**
 * New paragraphs after an anchor. The text goes in just before the anchor's paragraph mark, prefixed
 * with a newline, so each new paragraph is split off the anchor and inherits its paragraph style and
 * list membership — a new bullet in the same list at the same indent. A paragraph given its own
 * {@code like} is then restyled to match that one: paragraph style, list or no list, and text style.
 * A paragraph given a {@code level} (or a list kind or preset) starts a list of its own with the neighbouring
 * list paragraphs: its text carries that many leading tabs, which createParagraphBullets turns into the
 * nesting level — so every position used after that request is one with the tabs already gone.
 */
final class BuildInsert {

    private BuildInsert() {}

    static Planned build(int order, Edit.Insert ins, Scope s) {
        if (ins.paras().isEmpty()) Out.die(ins.label() + ": nothing to insert (--text)");
        var anchor = s.anchor(ins.label() + " --after", ins.after(), ins.afterOcc());
        var segs = ins.paras().stream().map(np -> Segments.parse(np.text())).toList();
        var tabs = ins.paras().stream().map(np -> np.level() == null ? 0 : np.level()).toList();
        var lines = new ArrayList<String>();
        for (var i = 0; i < segs.size(); i++) lines.add("\t".repeat(tabs.get(i)) + segs.get(i).plain());
        var at = anchor.end() - 1;
        var reqs = new ArrayList<Request>();
        reqs.add(new Request().setInsertText(new InsertTextRequest().setText("\n" + String.join("\n", lines)).setLocation(loc(s, at))));

        var report = new ArrayList<String>();
        report.add("insert " + segs.size() + " paragraph(s) in " + s.name() + " after @" + anchor.start() + ": \"" + anchor.preview() + "\"");
        var dt = s.tab().getDocumentTab();
        var inherited = anchor.p().getBullet() != null;
        var runs = new ListRuns();
        var unbullet = new ArrayList<Request>();
        var paraStyles = new ArrayList<Request>();
        var textStyles = new ArrayList<Request>();
        var expects = new ArrayList<Expect>();
        var bulletExpects = new ArrayList<Expect.Bullet>();
        var aligns = new ArrayList<AlignCheck.Want>();
        var pre = anchor.end();
        var gone = 0;
        for (var i = 0; i < segs.size(); i++) {
            var np = ins.paras().get(i);
            var sg = segs.get(i);
            var fresh = np.list() != null || np.level() != null || np.preset() != null;
            if (np.like() != null && np.style() != null) Out.die(ins.label() + ": a paragraph takes \"like\" or \"style\", not both");
            if (np.style() != null && fresh) Out.die(ins.label() + ": \"style\" gives a fresh named style with no list at all; drop \"style\" to use \"list\", \"level\" or \"preset\"");
            var named = np.style() == null ? null : named(ins.label(), np.style());
            var like = np.like() == null ? anchor : s.anchor(ins.label() + " like", np.like());
            var start = pre - gone;
            var end = start + sg.plain().length();
            var range = range(s, start, end + 1);
            var raw = range(s, pre, pre + tabs.get(i) + sg.plain().length() + 1);
            var likeHasBullet = like.p().getBullet() != null;
            var wanted = named == null && (likeHasBullet || fresh);
            var kindMatches = np.list() == null || "number".equals(np.list()) == Styles.ordered(anchor.p(), dt);
            var joins = inherited && wanted && likeHasBullet && np.level() == null && np.preset() == null && kindMatches
                    && anchor.p().getBullet().getListId().equals(like.p().getBullet().getListId());
            var preset = wanted ? ListPresets.choose(np.list(), np.preset(), like, dt) : null;
            if (inherited && !joins) {
                unbullet.add(new Request().setDeleteParagraphBullets(new DeleteParagraphBulletsRequest().setRange(raw)));
                unbullet.add(ListRuns.resetIndent(s.tabId(), raw.getStartIndex(), raw.getEndIndex()));
            }
            if (wanted && !joins) runs.add(raw.getStartIndex(), raw.getEndIndex(), s.paras().indexOf(like), preset, gone);
            if (named != null) paraStyles.add(namedStyle(named, range));
            else if (np.like() != null) paraStyles.add(paraStyle(like, range, !fresh));
            if ("tight".equals(np.spacing())) paraStyles.add(tightSpacing(range));
            if (np.fmt().any()) { paraStyles.add(np.fmt().request(range)); aligns.add(AlignCheck.Want.text(s.tabId(), sg.plain(), np.fmt())); }
            var ts = RunStyles.of(s, sg, start, named != null ? null : like);
            textStyles.addAll(ts);
            var list = !wanted ? "no list" : joins ? "joins the anchor's list" : preset + " list, one list with the adjacent list paragraphs (joins a list of the same preset directly above)";
            report.add("  + [" + (wanted ? "bullet" : "paragraph") + (np.level() == null ? "" : ", level " + np.level()) + ", " + list
                    + (named != null ? ", style " + named : ", like \"" + like.preview() + "\"") + (np.spacing() != null ? ", spacing " + np.spacing() : "") + (np.fmt().any() ? ", " + np.fmt().describe() : "") + "] " + np.text());
            if (fresh && np.like() == null) warnHeading(report, anchor);
            bulletExpects.add(new Expect.Bullet(s.tabId(), sg.plain(), wanted, wanted ? np.level() : null, wanted && fresh ? ListPresets.numbered(preset) : null));
            for (var t : ts) {
                var r = t.getUpdateTextStyle().getRange();
                report.add("      chars " + (r.getStartIndex() - start) + "-" + (r.getEndIndex() - start) + ": " + Expect.describe(t.getUpdateTextStyle().getTextStyle()));
                expects.add(new Expect(s.tabId(), sg.plain(), r.getStartIndex() - start, t.getUpdateTextStyle().getTextStyle()));
            }
            pre += tabs.get(i) + sg.plain().length() + 1;
            gone += tabs.get(i);
        }
        // Order matters: creating bullets and setting a named paragraph style both reset run styling,
        // so text styles go last or they are silently wiped (seen live, 2026-09-25).
        reqs.addAll(unbullet);
        reqs.addAll(runs.requests(s.tabId()));
        reqs.addAll(paraStyles);
        reqs.addAll(textStyles);
        return new Planned(order, s.tabId(), at, reqs, report, expects, bulletExpects, aligns);
    }

    /** "like" defaulted to the anchor, so a new list paragraph also inherits the ANCHOR's paragraph style — fine
     * after a normal paragraph, surprising after a heading (seen live, 2026-09-28: heading-styled bullets). Not
     * auto-corrected; surfaced in the dry run, where every other mismatch is caught. */
    private static void warnHeading(List<String> report, Para anchor) {
        var st = anchor.p().getParagraphStyle() == null ? null : anchor.p().getParagraphStyle().getNamedStyleType();
        if (st != null && !st.equals("NORMAL_TEXT"))
            report.add("      ⚠ no \"like\" given — this list paragraph inherits the anchor's " + st
                    + " paragraph style; add \"like\":\"<a normal body paragraph>\" if that is not wanted");
    }

    /** "spacing":"tight" — a narrow field mask so it never disturbs namedStyleType/alignment/indents,
     * whatever else the paragraph got from style/like/inheritance; queued into paraStyles right after
     * any namedStyle/paraStyle request for the same range, so it wins on the two fields it touches. */
    private static Request tightSpacing(Range range) {
        var zero = new Dimension().setMagnitude(0d).setUnit("PT");
        return new Request().setUpdateParagraphStyle(new UpdateParagraphStyleRequest()
                .setParagraphStyle(new ParagraphStyle().setSpaceAbove(zero).setSpaceBelow(zero))
                .setRange(range).setFields("spaceAbove,spaceBelow"));
    }

    /** Indents are left out for a list paragraph with its own level: the list sets them, and a copied one would flatten the nesting. */
    private static Request paraStyle(Para like, Range range, boolean withIndents) {
        var src = like.p().getParagraphStyle() == null ? new ParagraphStyle() : like.p().getParagraphStyle();
        var ps = new ParagraphStyle().setNamedStyleType(src.getNamedStyleType()).setAlignment(src.getAlignment())
                .setLineSpacing(src.getLineSpacing()).setSpaceAbove(src.getSpaceAbove()).setSpaceBelow(src.getSpaceBelow());
        if (withIndents) ps.setIndentStart(src.getIndentStart()).setIndentFirstLine(src.getIndentFirstLine()).setIndentEnd(src.getIndentEnd());
        return new Request().setUpdateParagraphStyle(new UpdateParagraphStyleRequest().setParagraphStyle(ps).setRange(range)
                .setFields("namedStyleType,alignment,lineSpacing,spaceAbove,spaceBelow" + (withIndents ? ",indentStart,indentFirstLine,indentEnd" : "")));
    }

    /** A fresh named style: every other paragraph property is cleared, so it comes from the named style alone. */
    private static Request namedStyle(String named, Range range) {
        return new Request().setUpdateParagraphStyle(new UpdateParagraphStyleRequest().setParagraphStyle(new ParagraphStyle().setNamedStyleType(named)).setRange(range)
                .setFields("namedStyleType,alignment,lineSpacing,spaceAbove,spaceBelow,indentStart,indentFirstLine,indentEnd"));
    }

    static String named(String label, String style) {
        var k = style.strip().toLowerCase().replaceAll("[\s_-]", "");
        if (k.matches("(heading|h)[1-6]")) return "HEADING_" + k.charAt(k.length() - 1);
        return switch (k) {
            case "normal", "normaltext", "body" -> "NORMAL_TEXT";
            case "title" -> "TITLE";
            case "subtitle" -> "SUBTITLE";
            default -> { Out.die(label + ": unknown style \"" + style + "\" (normal, heading1..heading6, title, subtitle)"); yield null; }
        };
    }

    static Location loc(Scope s, int i) { return new Location().setIndex(i).setTabId(s.tabId()); }

    static Range range(Scope s, int from, int to) { return new Range().setStartIndex(from).setEndIndex(to).setTabId(s.tabId()); }
}
