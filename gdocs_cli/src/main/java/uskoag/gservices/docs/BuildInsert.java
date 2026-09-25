package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.ArrayList;
import java.util.List;

import static uskoag.gservices.docs.Styles.on;

/**
 * New paragraphs after an anchor. The text goes in just before the anchor's paragraph mark, prefixed
 * with a newline, so each new paragraph is split off the anchor and inherits its paragraph style and
 * list membership — a new bullet in the same list at the same indent. A paragraph given its own
 * {@code like} is then restyled to match that one: paragraph style, list or no list, and text style.
 */
final class BuildInsert {

    private BuildInsert() {}

    static Planned build(int order, Edit.Insert ins, Scope s) {
        if (ins.paras().isEmpty()) Out.die(ins.label() + ": nothing to insert (--text)");
        var anchor = s.anchor(ins.label() + " --after", ins.after());
        var segs = ins.paras().stream().map(np -> Segments.parse(np.text())).toList();
        var at = anchor.end() - 1;
        var text = "\n" + String.join("\n", segs.stream().map(Segments::plain).toList());
        var reqs = new ArrayList<Request>();
        reqs.add(new Request().setInsertText(new InsertTextRequest().setText(text).setLocation(loc(s, at))));

        var report = new ArrayList<String>();
        report.add("insert " + segs.size() + " paragraph(s) in " + s.name() + " after @" + anchor.start() + ": \"" + anchor.preview() + "\"");
        var start = anchor.end();
        var bulletRuns = new ArrayList<int[]>();
        var unbullet = new ArrayList<Request>();
        var paraStyles = new ArrayList<Request>();
        var textStyles = new ArrayList<Request>();
        var expects = new ArrayList<Expect>();
        for (var i = 0; i < segs.size(); i++) {
            var np = ins.paras().get(i);
            var like = np.like() == null ? anchor : s.anchor(ins.label() + " like", np.like());
            var sg = segs.get(i);
            var end = start + sg.plain().length();
            var range = range(s, start, end + 1);
            var inherited = anchor.p().getBullet() != null;
            var wanted = like.p().getBullet() != null;
            var sameList = inherited && wanted && anchor.p().getBullet().getListId().equals(like.p().getBullet().getListId());
            if (inherited && !sameList) unbullet.add(new Request().setDeleteParagraphBullets(new DeleteParagraphBulletsRequest().setRange(range)));
            if (wanted && !sameList) bulletRuns.add(new int[]{start, end + 1, s.paras().indexOf(like)});
            if (np.like() != null) paraStyles.add(paraStyle(like, range));
            var ts = textStyles(s, sg, start, like);
            textStyles.addAll(ts);
            var list = !wanted ? "no list" : sameList ? "joins the anchor's list" : "new " + ListPresets.of(like, s.tab().getDocumentTab()) + " list, shared with adjacent bullets copying the same one";
            report.add("  + [" + (wanted ? "bullet" : "paragraph") + ", " + list + ", like \"" + like.preview() + "\"] " + np.text());
            for (var t : ts) {
                var r = t.getUpdateTextStyle().getRange();
                report.add("      chars " + (r.getStartIndex() - start) + "-" + (r.getEndIndex() - start) + ": " + Expect.describe(t.getUpdateTextStyle().getTextStyle()));
                expects.add(new Expect(s.tabId(), sg.plain(), r.getStartIndex() - start, t.getUpdateTextStyle().getTextStyle()));
            }
            start = end + 1;
        }
        // Order matters: creating bullets and setting a named paragraph style both reset run styling,
        // so text styles go last or they are silently wiped (seen live, 2026-09-25).
        reqs.addAll(unbullet);
        reqs.addAll(bullets(s, bulletRuns));
        reqs.addAll(paraStyles);
        reqs.addAll(textStyles);
        return new Planned(order, s.tabId(), at, reqs, report, expects);
    }

    /** Consecutive new bullets copying the same list become one list, not a list each. */
    private static List<Request> bullets(Scope s, List<int[]> runs) {
        var out = new ArrayList<Request>();
        for (var i = 0; i < runs.size(); i++) {
            var from = runs.get(i)[0];
            var to = runs.get(i)[1];
            var likeIdx = runs.get(i)[2];
            while (i + 1 < runs.size() && runs.get(i + 1)[0] == to && runs.get(i + 1)[2] == likeIdx) to = runs.get(++i)[1];
            var like = s.paras().get(likeIdx);
            out.add(new Request().setCreateParagraphBullets(new CreateParagraphBulletsRequest()
                    .setRange(range(s, from, to)).setBulletPreset(ListPresets.of(like, s.tab().getDocumentTab()))));
        }
        return out;
    }

    private static Request paraStyle(Para like, Range range) {
        var src = like.p().getParagraphStyle() == null ? new ParagraphStyle() : like.p().getParagraphStyle();
        var ps = new ParagraphStyle().setNamedStyleType(src.getNamedStyleType()).setAlignment(src.getAlignment())
                .setLineSpacing(src.getLineSpacing()).setSpaceAbove(src.getSpaceAbove()).setSpaceBelow(src.getSpaceBelow())
                .setIndentStart(src.getIndentStart()).setIndentFirstLine(src.getIndentFirstLine()).setIndentEnd(src.getIndentEnd());
        return new Request().setUpdateParagraphStyle(new UpdateParagraphStyleRequest().setParagraphStyle(ps).setRange(range)
                .setFields("namedStyleType,alignment,lineSpacing,spaceAbove,spaceBelow,indentStart,indentFirstLine,indentEnd"));
    }

    /**
     * Plain text copies the like-paragraph's last non-bold run, bold copies its first bold run, so a
     * "**Label:** text" bullet comes out exactly like its neighbours. Links are never copied.
     */
    private static List<Request> textStyles(Scope s, Segments sg, int start, Para like) {
        var runs = like.runs();
        var plain = runs.reversed().stream().filter(r -> r.getTextStyle() == null || !on(r.getTextStyle().getBold())).findFirst()
                .or(() -> runs.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(runs.getLast())).map(TextRun::getTextStyle).orElse(null);
        var bold = runs.stream().filter(r -> r.getTextStyle() != null && on(r.getTextStyle().getBold())).findFirst().map(TextRun::getTextStyle).orElse(null);
        var out = new ArrayList<Request>();
        for (var r : sg.runs()) {
            var b = sg.boldAt(r[0]);
            var st = copy(b && bold != null ? bold : plain);
            if (b) st.setBold(true);
            if (sg.italicAt(r[0])) st.setItalic(true);
            out.add(new Request().setUpdateTextStyle(new UpdateTextStyleRequest().setTextStyle(st).setFields("*")
                    .setRange(range(s, start + r[0], start + r[1]))));
        }
        return out;
    }

    private static TextStyle copy(TextStyle t) {
        if (t == null) return new TextStyle();
        return t.clone().setLink(null);
    }

    static Location loc(Scope s, int i) { return new Location().setIndex(i).setTabId(s.tabId()); }

    static Range range(Scope s, int from, int to) { return new Range().setStartIndex(from).setEndIndex(to).setTabId(s.tabId()); }
}
