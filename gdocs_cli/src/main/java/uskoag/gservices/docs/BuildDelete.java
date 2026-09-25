package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Whole paragraphs out, anchored at both ends. Every paragraph that goes is listed in the dry run.
 * The last paragraph of a body or cell cannot lose its own paragraph mark, so there the range shifts
 * one back and takes the preceding mark instead — the text still goes, the container stays valid.
 */
final class BuildDelete {

    private BuildDelete() {}

    static Planned build(int order, Edit.Delete d, Scope s) {
        var from = s.anchor(d.label() + " from", d.from());
        var to = d.to() == null ? from : s.anchor(d.label() + " to", d.to());
        var i = s.paras().indexOf(from);
        var j = s.paras().indexOf(to);
        if (j < i) Out.die(d.label() + ": \"to\" paragraph comes before \"from\" in tab " + s.name());
        var gone = s.paras().subList(i, j + 1);
        for (var k = 1; k < gone.size(); k++)
            if (gone.get(k).start() != gone.get(k - 1).end()) Out.die(d.label() + ": the range crosses a table or other block; delete within one container");
        var start = from.start();
        var end = to.end();
        var next = j + 1 < s.paras().size() ? s.paras().get(j + 1) : null;
        if (next == null || next.start() != end) { start -= 1; end -= 1; }
        var reqs = List.of(new Request().setDeleteContentRange(new DeleteContentRangeRequest()
                .setRange(BuildInsert.range(s, start, end))));
        var report = new ArrayList<String>();
        report.add("delete " + gone.size() + " paragraph(s) in " + s.name() + ", @" + from.start() + "-" + to.end());
        for (var p : gone) report.add("  - \"" + p.preview() + "\"");
        return new Planned(order, s.tabId(), from.start(), reqs, report);
    }
}
