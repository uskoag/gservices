package uskoag.gservices.gdocsmon;

import java.util.*;

/**
 * The relay's long-poll: hands out notices and approved events for its attachment. An event goes SENT when
 * handed out and DELIVERED only when the relay acks it (i.e. it reached the session's inbox); a missing ack
 * puts it back. So a relay that dies mid-way loses nothing.
 */
final class Deliver {

    private Deliver() {}

    static Map<String, Object> pull(String attId, long waitMs) throws InterruptedException {
        var out = new LinkedHashMap<String, Object>();
        synchronized (State.class) {
            State.pulling++;
            try {
                var end = System.currentTimeMillis() + waitMs;
                while (true) {
                    var a = State.atts.get(attId);
                    if (a == null) throw new IllegalArgumentException("unknown attachment " + attId);
                    a.lastPull = System.currentTimeMillis();
                    out.put("status", a.status);
                    if (a.endReason != null) out.put("reason", a.endReason);
                    var msgs = new ArrayList<Map<String, String>>();
                    while (!a.outbox.isEmpty()) msgs.add(Map.of("id", "", "text", a.outbox.poll()));
                    if (a.live()) for (var e : State.events)
                        if (e.status.equals(Ev.APPROVED) && e.doc.equals(a.doc) && (e.inform || e.placeholder != null)) {
                            e.status = Ev.SENT;
                            e.sentAt = System.currentTimeMillis();
                            msgs.add(Map.of("id", e.id, "text", e.render()));
                        }
                    if (!msgs.isEmpty() || !a.status.equals(Att.ACTIVE) && !a.status.equals(Att.PENDING)) { out.put("messages", msgs); break; }
                    var left = end - System.currentTimeMillis();
                    if (left <= 0) { out.put("messages", msgs); break; }
                    State.class.wait(Math.min(left, 20_000));
                }
            } finally { State.pulling--; State.lastActive = System.currentTimeMillis(); }
        }
        State.changed();
        return out;
    }

    static boolean ack(String evId) {
        synchronized (State.class) {
            for (var e : State.events)
                if (e.id.equals(evId) && e.status.equals(Ev.SENT)) { e.status = Ev.DELIVERED; State.note("delivered " + e.id + " into the session"); State.changed(); return true; }
        }
        return false;
    }
}
