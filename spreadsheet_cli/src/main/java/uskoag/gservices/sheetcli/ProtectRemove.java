package uskoag.gservices.sheetcli;

import com.google.api.services.sheets.v4.model.*;

/** protect --remove <id> <protectedRangeId>: lifts one protection; the cells and their contents are untouched. */
final class ProtectRemove {

    private ProtectRemove() {}

    static void run(String id, int prId, boolean apply) throws Exception {
        var f = Protections.byId(id, prId);
        if (f == null) Out.die("no protection #" + prId + "; protect --list " + id + " shows what there is");
        Out.data((apply ? "REMOVING" : "WOULD REMOVE") + " protection:");
        Out.data(Protections.show(f.tab(), f.pr()));
        if (!apply) { Out.note("dry run: nothing changed. Add --apply to commit."); return; }
        ProtectApply.batch(id, new Request().setDeleteProtectedRange(new DeleteProtectedRangeRequest().setProtectedRangeId(prId)));
        if (Protections.byId(id, prId) != null) Out.die("VERIFY FAILED: protection #" + prId + " is still there");
        Out.data("VERIFIED: protection #" + prId + " is gone");
    }
}
