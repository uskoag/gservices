package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.ArrayList;
import java.util.List;

/** Links to a heading of the source, pointed at the same heading in the copy: made once the headings carry their new ids. */
final class ImportLinks {

    private ImportLinks() {}

    static boolean toHeading(TextStyle st) { return st.getLink() != null && st.getLink().getUrl() == null; }

    static List<Request> make(ImportPlan plan, ImportDest d, List<Para> dp) {
        var out = new ArrayList<Request>();
        for (var i = 0; i < plan.flat.size(); i++) for (var r : plan.flat.get(i).runs) {
            if (!toHeading(r.style())) continue;
            var link = r.style().getLink();
            var sid = link.getHeadingId() != null ? link.getHeadingId() : link.getHeading().getId();
            var idx = plan.headingFlat.get(sid);
            String h = null;
            for (var k = 0; idx != null && h == null && k < 4 && idx + k < dp.size(); k++) h = dp.get(idx + k).p().getParagraphStyle().getHeadingId();
            if (h == null) { plan.note("link to a heading that did not copy (dropped)"); continue; }
            var st = new TextStyle().setLink(new Link().setHeading(new HeadingLink().setId(h).setTabId(d.tabId)));
            var at = dp.get(i).start();
            out.add(new Request().setUpdateTextStyle(new UpdateTextStyleRequest().setRange(d.range(at + r.from(), at + r.to())).setTextStyle(st).setFields("link")));
        }
        return out;
    }
}
