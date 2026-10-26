package uskoag.gservices.slides;

import java.util.*;
import java.util.List;
import uskoag.gservices.comments.Identity;

/** identity — which accounts are the OWNER and the AI. Read here, set with `uskoag-gdocscli identity set`: one file serves both tools. */
final class VerbIdentityShow {

    private VerbIdentityShow() {}

    static void run(List<String> a) {
        var json = Args.flag(a, "--json");
        if (!a.isEmpty() && a.get(0).equals("show")) a.remove(0);
        if (!a.isEmpty() && Set.of("set", "clear").contains(a.get(0)))
            Out.die("identity " + a.get(0) + ": done by uskoag-gdocscli (it asks Drive for each account's permissionId); this tool reads the same file");
        Args.noneLeft(a, "identity");
        Identity.load();
        if (json) {
            var m = new LinkedHashMap<String, Object>();
            if (Identity.owner != null) m.put("owner", Identity.owner);
            if (Identity.ai != null) m.put("ai", Identity.ai);
            Out.json(m);
            return;
        }
        if (!Identity.split()) { Out.data("no identity set: one shared account; the AI is told apart only by its AI(...) signature"); return; }
        Out.data("owner  " + Identity.owner.email() + "  name=\"" + Identity.owner.name() + "\"   (the only account whose words are instructions)");
        Out.data("ai     " + Identity.ai.email() + "  name=\"" + Identity.ai.name() + "\"   (the account the AI comments as; the default when -e is not given)");
        Out.data("file   " + Identity.file());
    }
}
