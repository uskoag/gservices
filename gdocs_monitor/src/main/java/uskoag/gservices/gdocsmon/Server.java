package uskoag.gservices.gdocsmon;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * Loopback HTTP for the CLI verbs. Every request carries the token from the port file, so a web page in the
 * browser cannot drive it. There is deliberately no approve/deny/allow endpoint: those live in the tray only.
 */
final class Server {

    static HttpServer http;
    static String token;
    static Runnable exit = () -> {};

    private Server() {}

    static void start() throws Exception {
        token = HexFormat.of().formatHex(SecureRandom.getInstanceStrong().generateSeed(16));
        http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        http.setExecutor(Executors.newCachedThreadPool(r -> { var t = new Thread(r, "gdocsmon-http"); t.setDaemon(true); return t; }));
        route("/status", Server::status);
        route("/watch", Server::watch);
        route("/unwatch", b -> Map.of("removed", State.unwatch(req(b, "doc"))));
        route("/notify", Server::notifyUser);
        route("/attach", Server::attach);
        route("/pull", b -> Deliver.pull(req(b, "id"), (b.has("wait") ? b.get("wait").getAsLong() : 300) * 1000));
        route("/ack", b -> Map.of("acked", Deliver.ack(req(b, "id"))));
        route("/detach", b -> { var a = State.atts.get(req(b, "id")); if (a != null) State.detach(a, "detached by the session"); return Map.of("detached", a != null); });
        route("/stop", b -> { new Thread(() -> { sleep(300); exit.run(); }).start(); return Map.of("stopping", true); });
        http.start();
        Files.createDirectories(Home.DIR);
        Files.writeString(Home.PORT, http.getAddress().getPort() + " " + token + " " + ProcessHandle.current().pid());
        State.note("daemon listening on 127.0.0.1:" + http.getAddress().getPort() + " pid " + ProcessHandle.current().pid());
    }

    interface Handler { Object handle(JsonObject body) throws Exception; }

    private static void route(String path, Handler h) {
        http.createContext(path, ex -> {
            var code = 200;
            Object res;
            try {
                if (!token.equals(ex.getRequestHeaders().getFirst("X-Token"))) { code = 403; res = Map.of("error", "bad token"); }
                else {
                    State.touch();
                    var raw = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    res = h.handle(raw.isBlank() ? new JsonObject() : JsonParser.parseString(raw).getAsJsonObject());
                }
            } catch (IllegalArgumentException e) { code = 400; res = Map.of("error", e.getMessage()); }
            catch (Exception e) { code = 500; res = Map.of("error", String.valueOf(e)); }
            var bytes = new Gson().toJson(res).getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(code, bytes.length);
            try (var os = ex.getResponseBody()) { os.write(bytes); }
        });
    }

    private static Object status(JsonObject b) {
        var m = new LinkedHashMap<String, Object>();
        m.put("pid", ProcessHandle.current().pid());
        m.put("attachments", State.attList().stream().map(Att::line).toList());
        m.put("watches", State.watchList().stream().map(Watch::line).toList());
        m.put("pending", State.byStatus(Ev.PENDING).stream().map(Ev::line).toList());
        m.put("approved", State.byStatus(Ev.APPROVED).stream().map(Ev::line).toList());
        m.put("relaysPolling", State.pulling);
        return m;
    }

    private static Object watch(JsonObject b) {
        var w = new Watch();
        w.doc = req(b, "doc");
        w.account = b.has("account") ? b.get("account").getAsString() : null;
        if (b.has("every")) w.every = Math.max(15, b.get("every").getAsInt());
        if (b.has("trigger")) w.trigger = b.get("trigger").getAsString();
        if (!Set.of("mention", "all").contains(w.trigger)) throw new IllegalArgumentException("--trigger is mention or all");
        State.watch(w);
        Poller.pollNow(State.watches.get(w.doc));
        return Map.of("watching", w.doc, "every", w.every, "trigger", w.trigger);
    }

    private static Object notifyUser(JsonObject b) {
        var text = req(b, "text");
        var doc = b.has("doc") ? b.get("doc").getAsString() : null;
        State.note("CLAUDE" + (doc == null ? "" : " (" + doc + ")") + ": " + text);
        Tray.message("Claude", text);
        State.changed();
        return Map.of("shown", true);
    }

    private static Object attach(JsonObject b) {
        var a = new Att();
        a.doc = req(b, "doc");
        a.session = req(b, "session");
        a.cwd = b.has("cwd") ? b.get("cwd").getAsString() : null;
        a.account = b.has("account") ? b.get("account").getAsString() : null;
        var got = State.requestAttach(a);
        return Map.of("id", got.id, "status", got.status);
    }

    private static String req(JsonObject b, String k) {
        if (!b.has(k) || b.get(k).getAsString().isBlank()) throw new IllegalArgumentException(k + " is required");
        return b.get(k).getAsString();
    }

    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }
}
