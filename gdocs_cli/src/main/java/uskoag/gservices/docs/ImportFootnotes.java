package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Stage F: the body of each footnote just created. A new footnote starts with a placeholder body, which is cleared first. */
final class ImportFootnotes {

    private ImportFootnotes() {}

    static List<Request> make(ImportPlan plan, ImportDest d, Map<String, String> ids) {
        var out = new ArrayList<Request>();
        for (var e : ids.entrySet()) {
            var paras = plan.footnotes.get(e.getKey());
            var fn = d.tab.getDocumentTab().getFootnotes().get(e.getValue());
            var lastEnd = fn.getContent().getLast().getEndIndex();
            if (lastEnd - 1 > 0) out.add(new Request().setDeleteContentRange(new DeleteContentRangeRequest().setRange(range(d, e.getValue(), 0, lastEnd - 1))));
            out.add(new Request().setInsertText(new InsertTextRequest().setText(String.join("\n", paras.stream().map(p -> p.text).toList()))
                    .setLocation(new Location().setTabId(d.tabId).setSegmentId(e.getValue()).setIndex(0))));
            var off = 0;
            for (var i = 0; i < paras.size(); i++) {
                var sp = paras.get(i);
                var limit = sp.text.length() + (i < paras.size() - 1 ? 1 : 0);
                for (var r : sp.runs) {
                    var to = Math.min(r.to(), limit);
                    if (r.from() >= to || r.style().isEmpty()) continue;
                    var st = r.style().getLink() != null && r.style().getLink().getUrl() == null ? Eff.without(r.style(), "link") : r.style();
                    out.add(new Request().setUpdateTextStyle(new UpdateTextStyleRequest().setRange(range(d, e.getValue(), off + r.from(), off + to)).setTextStyle(st).setFields(Eff.mask(st))));
                }
                off += sp.text.length() + 1;
            }
        }
        return out;
    }

    private static Range range(ImportDest d, String footnoteId, int from, int to) {
        return new Range().setTabId(d.tabId).setSegmentId(footnoteId).setStartIndex(from).setEndIndex(to);
    }

    static List<String> verify(ImportPlan plan, ImportDest d, Map<String, String> ids) {
        var bad = new ArrayList<String>();
        for (var e : ids.entrySet()) {
            var want = String.join("\n", plan.footnotes.get(e.getKey()).stream().map(p -> Para.norm(p.text)).toList());
            var fn = d.tab.getDocumentTab().getFootnotes() == null ? null : d.tab.getDocumentTab().getFootnotes().get(e.getValue());
            var got = fn == null ? null : String.join("\n", Para.of(fn.getContent()).stream().map(Para::plain).toList());
            if (!want.equals(got)) bad.add("footnote " + e.getValue() + " text: expected \"" + want + "\" read back \"" + got + "\"");
        }
        return bad;
    }
}
