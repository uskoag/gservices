package uskoag.gservices.gdocsmon;

import java.util.*;

/** One watched document. `seen` holds thread/post keys already turned into events (or ignored), so a post fires once. */
final class Watch {

    String doc, account, trigger = "mention", lastError;
    int every = 60;
    long lastPoll, since;
    int polls;
    boolean busy;
    final Set<String> seen = new HashSet<>();

    String line() {
        var when = lastPoll == 0 ? "not polled yet" : "polled " + Home.stamp(lastPoll);
        return doc + "  as " + (account == null ? "the AI account (identity.json)" : account) + "  every " + every + "s  trigger=" + trigger + "  " + when + " (" + polls + ")"
                + (lastError == null ? "" : "  ERROR: " + Ev.clip(lastError, 120));
    }
}
