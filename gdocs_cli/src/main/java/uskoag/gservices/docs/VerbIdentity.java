package uskoag.gservices.docs;

import uskoag.gservices.comments.Identity;
import java.nio.file.Files;
import java.util.*;

/**
 * identity — which account is the OWNER (the person the AI serves, whose words are instructions) and
 * which is the AI ACCOUNT (the one the AI edits and comments as). `set` asks Drive, as each account,
 * for its permissionId and display name, so both can be recognised in history even where Google hides
 * the email. Both accounts must be in the wallet with Drive read access.
 */
public final class VerbIdentity {

    private VerbIdentity() {}

    static void run(List<String> a) throws Exception {
        var sub = a.isEmpty() ? "show" : a.remove(0).toLowerCase();
        switch (sub) {
            case "show" -> show(Args.flag(a, "--json"));
            case "set" -> set(Args.val(a, "--owner"), Args.val(a, "--ai"), a);
            case "clear" -> {
                Files.deleteIfExists(Identity.file());
                Out.success("identity cleared; back to one shared account, the AI told apart by its signature");
            }
            default -> Out.die("identity: show | set --owner <email> --ai <email> | clear");
        }
    }

    private static void show(boolean json) {
        Identity.load();
        if (json) { Out.json(map()); return; }
        if (!Identity.split()) { Out.data("no identity set: one shared account; the AI is told apart only by its AI(...) signature"); return; }
        Out.data("owner  " + line(Identity.owner) + "   (the only account whose words are instructions)");
        Out.data("ai     " + line(Identity.ai) + "   (the account the AI edits and comments as; the default when -e is not given)");
        Out.data("file   " + Identity.file());
    }

    private static String line(Identity.Account x) { return x.email() + "  name=\"" + x.name() + "\"  permissionId=" + x.permissionId(); }

    private static void set(String owner, String ai, List<String> rest) throws Exception {
        Args.noneLeft(rest, "identity set");
        if (owner == null || ai == null) Out.die("identity set --owner <email> --ai <email>  (both, and they must differ)");
        if (owner.equalsIgnoreCase(ai)) Out.die("owner and AI account are the same; with one account use no identity (the signature marks the AI)");
        Identity.owner = lookup(owner);
        Identity.ai = lookup(ai);
        Files.createDirectories(Identity.file().getParent());
        Files.writeString(Identity.file(), Out.GSON.toJson(map()) + "\n");
        show(false);
        Out.success("identity saved. The AI account must be an EDITOR on each document it works on (the owner shares it).");
    }

    private static Identity.Account lookup(String email) throws Exception {
        Out.info("about.get as " + email);
        var u = Auth.driveAs(email).about().get().setFields("user(displayName,emailAddress,permissionId)").execute().getUser();
        if (u == null) Out.die("Drive did not say who " + email + " is");
        return new Identity.Account(u.getEmailAddress() == null ? email : u.getEmailAddress(), u.getPermissionId(), u.getDisplayName());
    }

    private static Map<String, Object> map() {
        var m = new LinkedHashMap<String, Object>();
        if (Identity.owner != null) m.put("owner", acc(Identity.owner));
        if (Identity.ai != null) m.put("ai", acc(Identity.ai));
        return m;
    }

    private static Map<String, Object> acc(Identity.Account x) {
        var m = new LinkedHashMap<String, Object>();
        m.put("email", x.email());
        m.put("permissionId", x.permissionId());
        m.put("name", x.name());
        return m;
    }
}
