package uskoag.gservices.docs;

import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.docs.v1.model.*;
import java.nio.file.*;

/**
 * Offline: every paragraph of a `read --json` file (one tab or the whole document) with its list, level and the
 * label Docs shows, so a rendering can be checked against what the page numbers.
 *
 * mvn exec:java -Dexec.mainClass=uskoag.gservices.docs.ListDump -Dexec.classpathScope=test -Dexec.args="<file.json>"
 */
public class ListDump {

    public static void main(String[] args) throws Exception {
        for (var dt : tabs(Path.of(args[0]))) {
            var labels = new ListLabel(dt);
            for (var p : Para.of(dt)) {
                var b = p.p().getBullet();
                var t = p.preview();
                System.out.println(b == null ? "                    | " + t
                        : String.format("%-10s L%d %-6s %-4s| %s", b.getListId().substring(Math.max(0, b.getListId().length() - 10)), Styles.level(p.p()),
                        Styles.ordered(p.p(), dt) ? "num" : "bullet", labels.next(p.p()), t));
            }
        }
    }

    static java.util.List<DocumentTab> tabs(Path json) throws Exception {
        var text = Files.readString(json);
        var f = GsonFactory.getDefaultInstance();
        if (text.stripLeading().startsWith("[")) return java.util.Arrays.stream(f.fromString(text, Tab[].class)).map(Tab::getDocumentTab).toList();
        return Tabs.flat(f.fromString(text, Document.class)).stream().map(Tab::getDocumentTab).toList();
    }
}
