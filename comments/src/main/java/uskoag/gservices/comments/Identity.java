package uskoag.gservices.comments;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.function.Supplier;

/**
 * The two accounts in play: the OWNER, the person the AI serves and the only one whose words are
 * instructions; and the AI ACCOUNT, the account the AI edits and comments as. With them distinct,
 * Google's own history and comment authorship separate the AI's work from people's, and nothing has
 * to be inferred from signatures. Stored in ~/uskoag/gservices/gdocs/identity.json by
 * `uskoag-gdocscli identity set`, which also records each account's Drive permissionId and display
 * name, because Drive hides the email of accounts from another domain but still gives the permissionId.
 * One file for every tool that reads it: the two accounts are the same whatever the file type.
 *
 * <p>Unset, the tools behave as before: one account, the AI told apart by its "AI(...)" signature.
 */
public final class Identity {

    public record Account(String email, String permissionId, String name) {
        boolean is(String email, String permissionId, String name) {
            if (permissionId != null && permissionId.equals(this.permissionId)) return true;
            if (email != null && email.equalsIgnoreCase(this.email)) return true;
            return name != null && this.name != null && name.equalsIgnoreCase(this.name);
        }
    }

    public static Account owner, ai;

    /** The account this run reads and writes as; each CLI points it at its own Auth.email. */
    public static Supplier<String> reading = () -> null;

    private static boolean loaded;

    private Identity() {}

    public static Path file() { return Path.of(System.getProperty("user.home"), "uskoag", "gservices", "gdocs", "identity.json"); }

    public static void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.exists(file())) return;
            var o = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
            owner = account(o, "owner");
            ai = account(o, "ai");
        } catch (Exception e) {
            System.err.println("[ERROR] identity.json unreadable (" + e.getMessage() + "); running without owner/AI accounts");
        }
    }

    private static Account account(JsonObject o, String k) {
        if (!o.has(k) || !o.get(k).isJsonObject()) return null;
        var a = o.getAsJsonObject(k);
        return new Account(s(a, "email"), s(a, "permissionId"), s(a, "name"));
    }

    private static String s(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null; }

    public static boolean split() { load(); return owner != null && ai != null; }

    /** Which account this run acts as: -e if given, else the AI account when one is set. */
    public static String acting(String explicit) {
        load();
        if (explicit != null && !explicit.isBlank()) return explicit;
        return ai == null ? null : ai.email();
    }

    public static boolean actingAsAi() {
        load();
        var r = reading.get();
        return ai != null && ai.email().equalsIgnoreCase(r == null ? "" : r);
    }

    /** "ai", "owner" or "other" for an author as Drive or the Docs/Slides APIs name them. */
    public static String role(String email, String permissionId, String name) {
        load();
        if (ai != null && ai.is(email, permissionId, name)) return "ai";
        if (owner != null && owner.is(email, permissionId, name)) return "owner";
        return "other";
    }

    /** A comment post: the signature still marks the AI when one account is shared; `me` is relative to the account reading. */
    public static String rolePost(String displayName, boolean me, boolean signed) {
        load();
        if (signed) return "ai";
        var r = reading.get();
        if (me && r != null) {
            if (ai != null && ai.email().equalsIgnoreCase(r)) return "ai";
            if (owner == null || owner.email().equalsIgnoreCase(r)) return "owner";
        }
        return role(null, null, displayName);
    }

    public static String label(String role) {
        return switch (role) {
            case "ai" -> "[AI" + (owner == null ? "" : ", serving " + owner.email()) + "]";
            case "owner" -> "[owner]";
            default -> "";
        };
    }
}
