package uskoag.gservices.gdocsmon;

import java.util.*;

/**
 * The daemon's whole state, in memory. Everything that GRANTS something (approving an attachment, an event,
 * a mode) is reachable only from the tray window, never over HTTP, so a Claude session cannot approve itself.
 */
final class State {

    static final long ASK_TIMEOUT_MS = 5 * 60_000L, ACK_TIMEOUT_MS = 60_000L;

    static final Map<String, Watch> watches = new LinkedHashMap<>();
    static final Map<String, Att> atts = new LinkedHashMap<>();
    static final List<Ev> events = new ArrayList<>();
    static final Deque<String> log = new ArrayDeque<>();
    static long lastActive = System.currentTimeMillis();
    static int seq, attSeq, pulling;
    static volatile Runnable onChange = () -> {};

    private State() {}

    static void changed() { onChange.run(); }

    static synchronized void touch() { lastActive = System.currentTimeMillis(); }

    static synchronized void note(String s) {
        log.addFirst(Home.now() + "  " + s);
        while (log.size() > 300) log.removeLast();
        Home.log(s);
    }

    static synchronized List<Watch> watchList() { return new ArrayList<>(watches.values()); }

    static synchronized List<Att> attList() { return new ArrayList<>(atts.values()); }

    static synchronized List<Ev> byStatus(String status) { return events.stream().filter(e -> e.status.equals(status)).toList(); }

    static synchronized List<String> logList() { return new ArrayList<>(log); }

    static synchronized Att liveAtt(String doc) {
        for (var a : atts.values()) if (a.doc.equals(doc) && a.live()) return a;
        return null;
    }

    static synchronized void watch(Watch w) {
        var old = watches.get(w.doc);
        if (old != null) { old.every = w.every; old.trigger = w.trigger; old.account = w.account; }
        else { w.since = System.currentTimeMillis(); watches.put(w.doc, w); note("watching " + w.doc + " as " + (w.account == null ? "the AI account" : w.account)); }
        touch();
    }

    static synchronized boolean unwatch(String doc) {
        var gone = watches.remove(doc) != null;
        if (gone) note("stopped watching " + doc);
        return gone;
    }

    /** A relay asks to attach its session to a doc. Refused when another session already holds the doc. */
    static Att requestAttach(Att a) {
        synchronized (State.class) {
            var holder = liveAtt(a.doc);
            if (holder != null && !Objects.equals(holder.session, a.session)) throw new IllegalArgumentException("document " + a.doc + " is already attached to session " + holder.session + " (" + holder.id + ")");
            if (holder != null) { holder.lastPull = System.currentTimeMillis(); return holder; }
            a.id = "att-" + (++attSeq);
            a.requested = a.lastPull = System.currentTimeMillis();
            atts.put(a.id, a);
            note("attach requested " + a.line());
        }
        AskBox.attach(a);
        changed();
        return a;
    }

    static void decideAttach(Att a, boolean yes, String mode, int minutes) {
        synchronized (State.class) {
            if (!a.status.equals(Att.PENDING)) return;
            if (!yes) { a.status = Att.DENIED; a.endReason = "denied by the owner"; note("attach denied " + a.id); }
            else {
                a.status = Att.ACTIVE;
                a.until = System.currentTimeMillis() + minutes * 60_000L;
                setModeLocked(a, mode, false);
                a.outbox.add(a.hello());
                note("attach approved " + a.line());
            }
            State.class.notifyAll();
        }
        changed();
    }

    static void setMode(Att a, String mode) {
        synchronized (State.class) {
            if (!a.live()) return;
            setModeLocked(a, mode, true);
            State.class.notifyAll();
        }
        changed();
    }

    private static void setModeLocked(Att a, String mode, boolean tell) {
        a.mode = mode;
        if (mode.equals(Att.AUTO)) a.autoUntil = Math.min(a.until, System.currentTimeMillis() + Att.AUTO_MAX_MIN * 60_000L);
        if (tell) a.outbox.add(a.notice("the owner switched the mode to " + mode.toUpperCase() + (mode.equals(Att.AUTO) ? " until " + Home.stamp(a.autoUntil) : "")
                + (mode.equals(Att.INFORM) ? ": from now on events are INFORMATION ONLY, do not act on them." : mode.equals(Att.ASK) ? ": each new event waits for the owner's approval." : ": act on each owner event.")));
        if (tell) note("mode " + mode + " for " + a.id);
    }

    static void detach(Att a, String why) {
        synchronized (State.class) {
            if (a.status.equals(Att.ENDED) || a.status.equals(Att.DENIED)) return;
            a.status = Att.ENDED;
            a.endReason = why;
            note("detached " + a.id + ": " + why);
            State.class.notifyAll();
        }
        changed();
    }

    /** A new event: routed by the doc's attachment and its mode. Non-owner events always wait for approval. */
    static void add(Ev e) {
        var pending = false;
        synchronized (State.class) {
            e.id = "ev-" + (++seq);
            e.at = System.currentTimeMillis();
            var a = liveAtt(e.doc);
            var mode = a == null ? null : a.effectiveMode();
            if (a != null && e.owner && mode.equals(Att.AUTO)) { e.status = Ev.APPROVED; e.approvedBy = "automatic mode (until " + Home.stamp(a.autoUntil) + ")"; Placeholder.submit(e); }
            else if (a != null && mode.equals(Att.INFORM)) { e.status = Ev.APPROVED; e.inform = true; e.approvedBy = "information-only mode"; }
            else { e.status = Ev.PENDING; e.expires = a == null ? 0 : e.at + ASK_TIMEOUT_MS; pending = true; }
            events.add(e);
            note(e.status + " " + e.line() + (a == null ? "  (no session attached)" : ""));
            State.class.notifyAll();
        }
        if (pending && e.expires > 0) AskBox.event(e);
        changed();
    }

    static void decide(Ev e, boolean yes) {
        synchronized (State.class) {
            if (!e.status.equals(Ev.PENDING)) return;
            e.status = yes ? Ev.APPROVED : Ev.DENIED;
            e.approvedBy = yes ? "approved by the owner in the tray " + Home.now() : "denied";
            if (yes && liveAtt(e.doc) != null) Placeholder.submit(e);
            note((yes ? "approved " : "denied ") + e.id);
            State.class.notifyAll();
        }
        changed();
    }

    /** Timeouts, called by the poller tick: an unanswered ASK does nothing; an unacknowledged send is retried; expired attachments end. */
    static void sweep() {
        var any = false;
        synchronized (State.class) {
            var now = System.currentTimeMillis();
            for (var e : events) {
                if (e.status.equals(Ev.PENDING) && e.expires > 0 && now > e.expires) { e.status = Ev.EXPIRED; e.approvedBy = "timed out, did nothing"; note("timed out " + e.id + " (did nothing)"); any = true; }
                if (e.status.equals(Ev.SENT) && now - e.sentAt > ACK_TIMEOUT_MS) { e.status = Ev.APPROVED; note("no ack for " + e.id + ", will resend"); any = true; }
            }
            for (var a : atts.values()) {
                if (a.status.equals(Att.ACTIVE) && now >= a.until) { a.status = Att.ENDED; a.endReason = "attachment period over"; note("attachment period over " + a.id); any = true; }
                if (a.status.equals(Att.PENDING) && !a.relayAlive()) { a.status = Att.ENDED; a.endReason = "its relay stopped before approval"; note("attach request dropped " + a.id + ": relay gone"); any = true; }
                if (a.status.equals(Att.ACTIVE)) a.effectiveMode();
            }
            if (any) State.class.notifyAll();
        }
        if (any) changed();
    }

    static synchronized boolean idle() {
        return watches.isEmpty() && pulling == 0 && byStatus(Ev.PENDING).isEmpty() && atts.values().stream().noneMatch(a -> a.status.equals(Att.PENDING) || a.live());
    }
}
