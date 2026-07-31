package uskoag.gservices;

import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.LabelColor;
import com.google.api.services.gmail.model.ListLabelsResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static uskoag.gservices.GmailCli.*;

/**
 * Label-definition commands: create, update (rename / recolor / visibility) and delete. Applying labels
 * to messages/threads lives in {@link GmailLabelApply}. Targets resolve by exact id or case-insensitive
 * name; system labels (INBOX, UNREAD, …) cannot be deleted.
 */
final class GmailLabels {

    private GmailLabels() {}

    static void create(GmailCli.Args a, String name) throws IOException {
        Label existing = lookup(labelIndex(), name);
        if (existing != null) {
            logError("label already exists: " + existing.getName() + " (" + existing.getId() + ")");
            System.exit(1);
        }
        Label l = new Label().setName(name);
        applyVisibility(a, l);
        LabelColor c = buildColor(a, null);
        if (c != null) l.setColor(c);
        emitLabel("created", gmail.users().labels().create(USER, l).execute());
    }

    static void update(GmailCli.Args a, String target) throws IOException {
        Label existing = require(target);
        Label patch = new Label();
        boolean any = false;
        String newName = a.get("name");
        if (newName != null && !newName.isBlank()) { patch.setName(newName); any = true; }
        any |= applyVisibility(a, patch);
        LabelColor c = buildColor(a, existing);
        if (c != null) { patch.setColor(c); any = true; }
        if (!any) { logError("nothing to update (use --name / --hide|--show / --text-color & --bg-color)"); System.exit(2); }
        emitLabel("updated", gmail.users().labels().patch(USER, existing.getId(), patch).execute());
    }

    static void delete(GmailCli.Args a, String target) throws IOException {
        Label existing = require(target);
        if ("system".equalsIgnoreCase(existing.getType())) {
            logError("refusing to delete the system label: " + existing.getName());
            System.exit(2);
        }
        gmail.users().labels().delete(USER, existing.getId()).execute();
        if (json) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("action", "deleted");
            m.put("id", existing.getId());
            m.put("name", existing.getName());
            emitJson(m);
        } else {
            System.out.println("SUCCESS: deleted label " + existing.getName() + " (" + existing.getId() + ")");
        }
    }

    // ---- shared helpers (used by GmailLabelApply too) -----------------------

    /** name(lowercased) → Label and id → Label, from one labels.list call. */
    static Map<String, Label> labelIndex() throws IOException {
        ListLabelsResponse resp = gmail.users().labels().list(USER).execute();
        Map<String, Label> idx = new LinkedHashMap<>();
        if (resp.getLabels() != null) for (Label l : resp.getLabels()) {
            idx.put(l.getId(), l);
            if (l.getName() != null) idx.put(l.getName().toLowerCase(), l);
        }
        return idx;
    }

    /** Looks up a label by exact id or case-insensitive name; null if absent. */
    static Label lookup(Map<String, Label> idx, String target) {
        Label l = idx.get(target);
        return l != null ? l : idx.get(target.toLowerCase());
    }

    /** Resolves a single target to its full resource (with color); exits 1 if not found. */
    private static Label require(String target) throws IOException {
        Label l = lookup(labelIndex(), target);
        if (l == null) { logError("no such label: " + target + "  (run 'labels' to list)"); System.exit(1); }
        // labels.list omits color/visibility for some labels; fetch the full resource as an accurate base.
        return gmail.users().labels().get(USER, l.getId()).execute();
    }

    /** Applies --hide/--show and explicit --*-visibility flags; returns true if it set anything. */
    private static boolean applyVisibility(GmailCli.Args a, Label l) {
        String llv = a.get("label-list-visibility");
        String mlv = a.get("message-list-visibility");
        if (a.has("hide")) { if (llv == null) llv = "labelHide"; if (mlv == null) mlv = "hide"; }
        if (a.has("show")) { if (llv == null) llv = "labelShow"; if (mlv == null) mlv = "show"; }
        boolean any = false;
        if (llv != null) { l.setLabelListVisibility(normLlv(llv)); any = true; }
        if (mlv != null) { l.setMessageListVisibility(normMlv(mlv)); any = true; }
        return any;
    }

    private static String normLlv(String v) {
        return switch (v.toLowerCase()) {
            case "show", "labelshow" -> "labelShow";
            case "hide", "labelhide" -> "labelHide";
            case "unread", "showifunread", "labelshowifunread" -> "labelShowIfUnread";
            default -> v;
        };
    }

    private static String normMlv(String v) {
        return switch (v.toLowerCase()) {
            case "hide" -> "hide";
            default -> "show";
        };
    }

    /**
     * Builds a LabelColor from --text-color/--bg-color. Gmail requires BOTH; on update the missing side
     * is filled from {@code existing}. Returns null when neither flag was given.
     */
    private static LabelColor buildColor(GmailCli.Args a, Label existing) {
        String tc = a.get("text-color");
        String bc = a.get("bg-color");
        if (tc == null && bc == null) return null;
        LabelColor ex = existing != null ? existing.getColor() : null;
        if (tc == null) tc = ex != null ? ex.getTextColor() : null;
        if (bc == null) bc = ex != null ? ex.getBackgroundColor() : null;
        if (tc == null || bc == null) {
            logError("setting a label color needs both --text-color <hex> and --bg-color <hex> (Gmail palette only)");
            System.exit(2);
        }
        return new LabelColor().setTextColor(tc).setBackgroundColor(bc);
    }

    private static void emitLabel(String action, Label l) {
        if (json) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("action", action);
            m.put("id", l.getId());
            m.put("name", l.getName());
            m.put("type", l.getType());
            m.put("labelListVisibility", l.getLabelListVisibility());
            m.put("messageListVisibility", l.getMessageListVisibility());
            if (l.getColor() != null) {
                m.put("textColor", l.getColor().getTextColor());
                m.put("backgroundColor", l.getColor().getBackgroundColor());
            }
            emitJson(m);
        } else {
            System.out.println("SUCCESS: " + action + " label");
            System.out.println("id:   " + l.getId());
            System.out.println("name: " + l.getName());
            if (l.getColor() != null)
                System.out.println("color: text=" + l.getColor().getTextColor() + " bg=" + l.getColor().getBackgroundColor());
        }
    }
}
