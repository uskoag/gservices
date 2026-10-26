package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.ArrayList;
import java.util.List;

import static uskoag.gservices.docs.BuildInsert.range;
import static uskoag.gservices.docs.Styles.on;

/** The text styles an inserted paragraph gets from the paragraph it is modelled on. */
final class RunStyles {

    private RunStyles() {}

    /**
     * Plain text copies the like-paragraph's last non-bold run, bold copies its first bold run, so a
     * "**Label:** text" bullet comes out exactly like its neighbours. Runs that are links are passed over when
     * there is anything else, and a link's look (underline, link blue) is never copied onto plain text; a
     * [text](target) span gets the link and that look itself, since fields "*" stops Google adding it.
     */
    static List<Request> of(Scope s, Segments sg, int start, Para like) {
        var likeRuns = like == null ? List.<TextRun>of() : like.runs();
        var unlinked = likeRuns.stream().filter(r -> r.getTextStyle() == null || r.getTextStyle().getLink() == null).toList();
        var runs = unlinked.isEmpty() ? likeRuns : unlinked;
        var plain = runs.reversed().stream().filter(r -> r.getTextStyle() == null || !on(r.getTextStyle().getBold())).findFirst()
                .or(() -> runs.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(runs.getLast())).map(TextRun::getTextStyle).orElse(null);
        var bold = runs.stream().filter(r -> r.getTextStyle() != null && on(r.getTextStyle().getBold())).findFirst().map(TextRun::getTextStyle).orElse(null);
        var out = new ArrayList<Request>();
        for (var r : sg.runs()) {
            var b = sg.boldAt(r[0]);
            var st = copy(b && bold != null ? bold : plain);
            if (b) st.setBold(true);
            if (sg.italicAt(r[0])) st.setItalic(true);
            if (sg.linkAt(r[0]) != null) st.setLink(Links.resolve(sg.linkAt(r[0]))).setUnderline(true).setForegroundColor(Links.BLUE);
            out.add(new Request().setUpdateTextStyle(new UpdateTextStyleRequest().setTextStyle(st).setFields("*")
                    .setRange(range(s, start + r[0], start + r[1]))));
        }
        return out;
    }

    private static TextStyle copy(TextStyle t) {
        if (t == null) return new TextStyle();
        var c = t.clone();
        if (t.getLink() != null) c.setUnderline(null).setForegroundColor(null);
        return c.setLink(null);
    }
}
