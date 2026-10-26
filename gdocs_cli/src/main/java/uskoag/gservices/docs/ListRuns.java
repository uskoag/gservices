package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The bullet runs of one batch. Consecutive list paragraphs with the same preset become ONE list in a single
 * createParagraphBullets, whose leading tabs are consumed as nesting levels — which is why every later request
 * in the batch must use positions with those tabs already gone ({@code tabsBefore} is how many were).
 */
final class ListRuns {

    record Run(int from, int to, int likeIdx, String preset, int tabsBefore) {}

    private final List<Run> runs = new ArrayList<>();

    void add(int from, int to, int likeIdx, String preset, int tabsBefore) {
        var last = runs.isEmpty() ? null : runs.getLast();
        if (last != null && last.to() == from && last.likeIdx() == likeIdx && last.preset().equals(preset))
            runs.set(runs.size() - 1, new Run(last.from(), to, likeIdx, preset, last.tabsBefore()));
        else runs.add(new Run(from, to, likeIdx, preset, tabsBefore));
    }

    List<Request> requests(String tabId) {
        var out = new ArrayList<Request>();
        for (var r : runs) out.add(new Request().setCreateParagraphBullets(new CreateParagraphBulletsRequest()
                .setRange(new Range().setTabId(tabId).setStartIndex(r.from() - r.tabsBefore()).setEndIndex(r.to() - r.tabsBefore()))
                .setBulletPreset(r.preset())));
        return out;
    }

    /** Back to no indent of its own: a list gets its indent from its nesting level, and a leftover one adds a level. */
    static Request resetIndent(String tabId, int from, int to) {
        return new Request().setUpdateParagraphStyle(new UpdateParagraphStyleRequest().setParagraphStyle(new ParagraphStyle())
                .setRange(new Range().setTabId(tabId).setStartIndex(from).setEndIndex(to)).setFields("indentStart,indentFirstLine"));
    }
}
