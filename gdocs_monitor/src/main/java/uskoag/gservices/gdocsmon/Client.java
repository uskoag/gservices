package uskoag.gservices.gdocsmon;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;

/** The CLI side: finds the daemon through the port file, starts one when none answers. */
final class Client {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    private Client() {}

    static JsonObject call(String path, JsonObject body, long timeoutSec) throws Exception {
        var parts = Files.readString(Home.PORT).strip().split(" ");
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + parts[0] + path))
                .timeout(Duration.ofSeconds(timeoutSec)).header("X-Token", parts[1])
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        var res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        var json = JsonParser.parseString(res.body()).getAsJsonObject();
        if (res.statusCode() != 200) throw new IllegalStateException("daemon: " + Rules.str(json, "error"));
        return json;
    }

    static boolean alive() {
        try { return Files.exists(Home.PORT) && call("/status", new JsonObject(), 5).has("pid"); }
        catch (Exception e) { return false; }
    }

    /**
     * Started through WMI (Win32_Process.Create) so the daemon is not a child of this process: Claude Code
     * ends a background command's whole process tree, and a daemon that dies with its first `watch` is no daemon.
     */
    static void ensure() throws Exception {
        if (alive()) return;
        Files.deleteIfExists(Home.PORT);
        var cmdline = "\"" + Home.javaw() + "\" --enable-native-access=ALL-UNNAMED -Dfile.encoding=UTF-8 -jar \"" + Home.ownJar() + "\" daemon";
        var ps = "Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{CommandLine='" + cmdline.replace("'", "''") + "'} | Out-Null";
        var p = new ProcessBuilder(List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", ps)).redirectErrorStream(true).start();
        p.getInputStream().transferTo(System.err);
        p.waitFor();
        System.err.println("starting the gdocs monitor daemon (tray)...");
        for (var i = 0; i < 60; i++) {
            Thread.sleep(500);
            if (alive()) return;
        }
        throw new IllegalStateException("the daemon did not come up within 30s; see " + Home.LOG);
    }
}
