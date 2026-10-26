package uskoag.gservices.sheetcli;

import com.google.api.services.sheets.v4.model.*;
import java.util.*;

/** The protected ranges a spreadsheet has now, each with the title of its tab, and how one is shown. */
final class Protections {

    record Found(String tab, ProtectedRange pr) {}

    private Protections() {}

    static List<Found> all(String id) throws Exception {
        var ss = Auth.sheetsRead().spreadsheets().get(id).setFields("sheets(properties(sheetId,title),protectedRanges)").execute();
        var out = new ArrayList<Found>();
        for (var s : ss.getSheets())
            if (s.getProtectedRanges() != null)
                for (var pr : s.getProtectedRanges()) out.add(new Found(s.getProperties().getTitle(), pr));
        return out;
    }

    static Found byId(String id, int prId) throws Exception {
        for (var f : all(id)) if (f.pr().getProtectedRangeId() == prId) return f;
        return null;
    }

    static Map<String, Integer> tabs(String id) throws Exception {
        var ss = Auth.sheetsRead().spreadsheets().get(id).setFields("sheets.properties(sheetId,title)").execute();
        var m = new LinkedHashMap<String, Integer>();
        for (var s : ss.getSheets()) m.put(s.getProperties().getTitle(), s.getProperties().getSheetId());
        return m;
    }

    static List<String> users(ProtectedRange pr) {
        var e = pr.getEditors();
        return e == null || e.getUsers() == null ? List.of() : e.getUsers().stream().map(String::toLowerCase).sorted().toList();
    }

    static List<String> except(ProtectedRange pr) {
        var u = pr.getUnprotectedRanges();
        return u == null ? List.of() : u.stream().map(A1::of).sorted().toList();
    }

    static String show(String tab, ProtectedRange pr) {
        var sb = new StringBuilder();
        sb.append(String.format("#%-11s [%s] %s", pr.getProtectedRangeId() == null ? "(new)" : pr.getProtectedRangeId(), tab,
                pr.getNamedRangeId() != null ? "named range " + pr.getNamedRangeId() : A1.of(pr.getRange())));
        if (!except(pr).isEmpty()) sb.append("  except ").append(String.join(", ", except(pr)));
        if (Boolean.TRUE.equals(pr.getWarningOnly())) sb.append("  WARNING ONLY (anyone can still edit, after a prompt)");
        sb.append("\n             editors: ").append(Boolean.TRUE.equals(pr.getWarningOnly()) ? "(not enforced)" : users(pr).isEmpty() ? "(owner only)" : String.join(", ", users(pr)));
        if (pr.getEditors() != null && Boolean.TRUE.equals(pr.getEditors().getDomainUsersCanEdit())) sb.append(", everyone in the domain");
        if (pr.getDescription() != null && !pr.getDescription().isBlank()) sb.append("\n             description: \"").append(pr.getDescription()).append('"');
        if (Boolean.FALSE.equals(pr.getRequestingUserCanEdit())) sb.append("\n             (this account cannot edit it)");
        return sb.toString();
    }

    static Map<String, Object> json(String tab, ProtectedRange pr) {
        var m = new LinkedHashMap<String, Object>();
        m.put("protectedRangeId", pr.getProtectedRangeId());
        m.put("tab", tab);
        m.put("range", pr.getNamedRangeId() != null ? "named:" + pr.getNamedRangeId() : A1.of(pr.getRange()));
        m.put("unprotectedRanges", except(pr));
        m.put("warningOnly", Boolean.TRUE.equals(pr.getWarningOnly()));
        m.put("editorsEnforced", !Boolean.TRUE.equals(pr.getWarningOnly()));
        m.put("editors", users(pr));
        m.put("description", pr.getDescription());
        m.put("requestingUserCanEdit", pr.getRequestingUserCanEdit());
        return m;
    }

    /** The accounts that own the file: they can always edit, and are written into the editors so nobody is surprised. */
    static List<String> owners(String id) {
        try {
            var f = Auth.drive().files().get(id).setFields("owners(emailAddress)").setSupportsAllDrives(true).execute();
            return f.getOwners() == null ? List.of() : f.getOwners().stream().map(o -> o.getEmailAddress()).filter(Objects::nonNull).map(String::toLowerCase).toList();
        } catch (Exception e) {
            Out.info("owners of " + id + ": " + e.getMessage());
            return List.of();
        }
    }
}
