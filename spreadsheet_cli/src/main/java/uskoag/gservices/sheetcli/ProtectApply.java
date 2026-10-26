package uskoag.gservices.sheetcli;

import com.google.api.services.sheets.v4.model.*;
import java.util.*;

/** Add a protection, or bring the existing one over the same cells into line; then read it back. */
final class ProtectApply {

    private ProtectApply() {}

    static void run(String id, String tab, ProtectedRange want, boolean apply, boolean json) throws Exception {
        var existing = (Protections.Found) null;
        for (var f : Protections.all(id))
            if (f.pr().getNamedRangeId() == null && A1.same(f.pr().getRange(), want.getRange())) { existing = f; break; }

        if (existing != null) {
            var diff = differences(existing.pr(), want);
            if (diff.isEmpty()) {
                Out.data("already protected exactly so; nothing to do:");
                Out.data(Protections.show(tab, existing.pr()));
                return;
            }
            want.setProtectedRangeId(existing.pr().getProtectedRangeId());
            Out.data((apply ? "UPDATING" : "WOULD UPDATE") + " protection #" + existing.pr().getProtectedRangeId() + ": " + String.join("; ", diff));
            Out.data(Protections.show(tab, want));
            if (!apply) { Out.note("dry run: nothing changed. Add --apply to commit."); return; }
            var req = new UpdateProtectedRangeRequest().setProtectedRange(want).setFields("range,description,warningOnly,editors,unprotectedRanges");
            batch(id, new Request().setUpdateProtectedRange(req));
            verify(id, want.getProtectedRangeId(), json);
            return;
        }
        Out.data((apply ? "ADDING" : "WOULD ADD") + " protection:");
        Out.data(Protections.show(tab, want));
        if (!apply) { Out.note("dry run: nothing changed. Add --apply to commit."); return; }
        var res = batch(id, new Request().setAddProtectedRange(new AddProtectedRangeRequest().setProtectedRange(want)));
        verify(id, res.getReplies().getFirst().getAddProtectedRange().getProtectedRange().getProtectedRangeId(), json);
    }

    private static List<String> differences(ProtectedRange have, ProtectedRange want) {
        var out = new ArrayList<String>();
        if (!Objects.equals(nz(have.getDescription()), nz(want.getDescription()))) out.add("description \"" + nz(have.getDescription()) + "\" -> \"" + nz(want.getDescription()) + "\"");
        if (Boolean.TRUE.equals(have.getWarningOnly()) != Boolean.TRUE.equals(want.getWarningOnly())) out.add("warning-only " + Boolean.TRUE.equals(have.getWarningOnly()) + " -> " + want.getWarningOnly());
        var hu = Protections.users(have);
        var wu = Protections.users(want);
        if (!Boolean.TRUE.equals(want.getWarningOnly()) && !hu.equals(wu)) {
            var add = new ArrayList<>(wu); add.removeAll(hu);
            var drop = new ArrayList<>(hu); drop.removeAll(wu);
            out.add("editors" + (add.isEmpty() ? "" : " +" + String.join(" +", add)) + (drop.isEmpty() ? "" : " -" + String.join(" -", drop)));
        }
        if (!Protections.except(have).equals(Protections.except(want)))
            out.add("unprotected ranges " + Protections.except(have) + " -> " + Protections.except(want));
        return out;
    }

    private static String nz(String s) { return s == null ? "" : s; }

    static BatchUpdateSpreadsheetResponse batch(String id, Request r) throws Exception {
        return Auth.sheetsWrite().spreadsheets().batchUpdate(id, new BatchUpdateSpreadsheetRequest().setRequests(List.of(r))).execute();
    }

    private static void verify(String id, int prId, boolean json) throws Exception {
        var f = Protections.byId(id, prId);
        if (f == null) Out.die("VERIFY FAILED: protection #" + prId + " is not on the spreadsheet after the write");
        if (json) { Out.json(Protections.json(f.tab(), f.pr())); return; }
        Out.data("VERIFIED, read back:");
        Out.data(Protections.show(f.tab(), f.pr()));
    }
}
