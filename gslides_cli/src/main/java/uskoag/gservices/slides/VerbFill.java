package uskoag.gservices.slides;

import com.google.api.services.slides.v1.model.ReplaceAllTextRequest;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.SubstringMatchCriteria;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Whole-deck replaceAllText, which is what turns any deck into a template: one master,
 * many outputs, per case or per language. Reports the occurrence count per key, so a key
 * that matched nothing is visible rather than silently ignored.
 */
public final class VerbFill {

    private VerbFill() {}

    static void run(List<String> a) throws Exception {
        var mapFile = Args.val(a, "--map");
        var caseSensitive = !Args.flag(a, "--ignore-case");
        var deck = Deck.presId(Args.req(a, "<deck>"));

        var pairs = new LinkedHashMap<String, String>();
        if (mapFile != null) fromJson(mapFile, pairs);
        for (var rest = new ArrayList<>(a); !rest.isEmpty(); ) {
            var kv = rest.remove(0);
            a.remove(kv);
            var eq = kv.indexOf('=');
            if (eq <= 0) Out.die("fill: expected key=value, got: " + kv);
            pairs.put(kv.substring(0, eq), kv.substring(eq + 1));
        }
        if (pairs.isEmpty()) Out.die("fill: nothing to replace (pass key=value pairs, or --map file.json)");

        var reqs = new ArrayList<Request>();
        var keys = new ArrayList<String>();
        for (var e : pairs.entrySet()) {
            keys.add(e.getKey());
            reqs.add(new Request().setReplaceAllText(new ReplaceAllTextRequest()
                    .setContainsText(new SubstringMatchCriteria()
                            .setText(e.getKey()).setMatchCase(caseSensitive))
                    .setReplaceText(e.getValue())));
        }

        var res = Api.flush(deck, reqs);
        if (res == null || res.getReplies() == null) return;

        var total = 0;
        for (var i = 0; i < res.getReplies().size(); i++) {
            var rep = res.getReplies().get(i);
            var n = rep.getReplaceAllText() == null || rep.getReplaceAllText().getOccurrencesChanged() == null
                    ? 0 : rep.getReplaceAllText().getOccurrencesChanged();
            total += n;
            Out.data(keys.get(i) + ": " + n);
            if (n == 0) Out.error("no occurrences of \"" + keys.get(i) + "\"");
        }
        Out.success(total + " occurrence(s) replaced across the deck");
    }

    private static void fromJson(String file, LinkedHashMap<String, String> into) {
        try {
            var json = JsonParser.parseString(Files.readString(Path.of(file)));
            if (!json.isJsonObject()) Out.die("--map expects a flat JSON object of key/value strings");
            for (var e : ((JsonObject) json).entrySet()) into.put(e.getKey(), e.getValue().getAsString());
        } catch (Exception e) {
            Out.die("cannot read --map " + file + ": " + e.getMessage());
        }
    }
}
