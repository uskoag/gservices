package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.List;

/** A native table of contents out, in one deleteContentRange over its element's range; the plan-file form of `toc delete`. */
final class BuildToc {

    private BuildToc() {}

    static Planned build(int order, Edit.TocDelete d, Scope s) {
        var tocs = VerbToc.of(s.tab());
        if (tocs.isEmpty()) Out.die(d.label() + ": no table of contents in tab " + s.name());
        if (tocs.size() > 1 && d.index() == null) Out.die(d.label() + ": tab " + s.name() + " has " + tocs.size() + " tables of contents; give \"index\" (1-based)");
        var n = d.index() == null ? 1 : d.index();
        if (n < 1 || n > tocs.size()) Out.die(d.label() + ": index " + n + " is out of range 1-" + tocs.size());
        var t = tocs.get(n - 1);
        var req = new Request().setDeleteContentRange(new DeleteContentRangeRequest().setRange(BuildInsert.range(s, t.start(), t.end())));
        return new Planned(order, s.tabId(), t.start(), List.of(req), List.of("delete table of contents #" + n + " (" + t.entries().size() + " entries) in " + s.name() + ", @" + t.start() + "-" + t.end()));
    }
}
