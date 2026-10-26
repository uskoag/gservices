package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything between two anchors out, in one deleteContentRange. Between two paragraphs of the tab body that
 * includes whole tables, pictures and page breaks (the API removes a table only when the range covers all of it);
 * a range that starts or ends inside a table stays an error, and two paragraphs of one cell delete as before.
 * The closing paragraph mark of a body or cell cannot be deleted, so there the range shifts one back and takes
 * the preceding mark instead — the text still goes, the container stays valid.
 */
final class BuildDelete {

    private BuildDelete() {}

    static Planned build(int order, Edit.Delete d, Scope s) {
        if (d.toEnd() && d.to() != null) Out.die(d.label() + ": give --to or --to-end, not both");
        var from = s.anchor(d.label() + " from", d.from(), d.fromOcc());
        var body = s.tab().getDocumentTab().getBody().getContent();
        var fromTop = isTop(body, from.start());
        if (d.toEnd() && !fromTop) Out.die(d.label() + ": --to-end needs an anchor in the tab body, not one inside a table");
        var endAt = Para.idx(body.get(body.size() - 1).getEndIndex());
        var to = d.toEnd() ? s.paras().stream().filter(p -> p.end() == endAt).findFirst().orElseThrow()
                : d.to() == null ? from : s.anchor(d.label() + " to", d.to(), d.toOcc(), from);
        if (isTop(body, to.start()) != fromTop) Out.die(d.label() + ": the range starts or ends inside a table; both ends must be paragraphs of the tab body, or both in one table cell");
        var all = s.paras();
        var i = all.indexOf(from);
        var j = all.indexOf(to);
        if (j < i) Out.die(d.label() + ": \"to\" paragraph comes before \"from\" in tab " + s.name());
        var gone = fromTop ? body.stream().filter(e -> Para.idx(e.getStartIndex()) >= from.start() && Para.idx(e.getEndIndex()) <= to.end()).toList() : cellRange(d, all, i, j);
        var span = DeleteSpan.of(gone);
        var start = from.start();
        var end = to.end();
        var closes = fromTop ? !followedByParagraph(body, end) : !(j + 1 < all.size() && all.get(j + 1).start() == end);
        if (closes) {
            var prevIsPara = fromTop ? body.stream().anyMatch(e -> e.getParagraph() != null && Para.idx(e.getEndIndex()) == from.start())
                    : i > 0 && all.get(i - 1).end() == from.start();
            if (prevIsPara) { start -= 1; end -= 1; }
            else if (fromTop && end == endAt) end -= 1;
            else Out.die(d.label() + ": nothing but a table or section break lies on either side of this range; widen it to include a neighbouring paragraph");
        }
        var reqs = List.of(new Request().setDeleteContentRange(new DeleteContentRangeRequest().setRange(BuildInsert.range(s, start, end))));
        var report = new ArrayList<String>();
        report.add("delete " + span.summary() + " in " + s.name() + ", @" + from.start() + "-" + to.end());
        report.addAll(span.shown());
        return new Planned(order, s.tabId(), from.start(), reqs, report);
    }

    private static boolean isTop(List<StructuralElement> body, int paraStart) {
        return body.stream().anyMatch(e -> e.getParagraph() != null && Para.idx(e.getStartIndex()) == paraStart);
    }

    private static boolean followedByParagraph(List<StructuralElement> body, int end) {
        return body.stream().anyMatch(e -> e.getParagraph() != null && Para.idx(e.getStartIndex()) == end);
    }

    /** The paragraphs of one table cell (or footnote) between the anchors: they must touch, or the range spans cells. */
    private static List<StructuralElement> cellRange(Edit.Delete d, List<Para> all, int i, int j) {
        var gone = all.subList(i, j + 1);
        for (var k = 1; k < gone.size(); k++)
            if (gone.get(k).start() != gone.get(k - 1).end()) Out.die(d.label() + ": the range crosses a table cell boundary; delete within one cell");
        return gone.stream().map(p -> new StructuralElement().setStartIndex(p.start()).setEndIndex(p.end()).setParagraph(p.p())).toList();
    }
}
