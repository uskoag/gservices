package uskoag.gservices.docs;

import com.google.gson.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A plan is JSON: {"edits":[...]} or a bare array. Each edit:
 *   {"id":"E1","op":"replace","tab":"Summary","in":"Offices:","find":"Paris and Rome.","with":"..."}
 *   {"id":"E2","op":"insert","tab":"Summary","after":"2019, Paris:","paragraphs":["**2021, Lisbon:** text"]}
 * A paragraph may instead be {"text":"...","like":"<start of a paragraph to copy the style of>"}.
 * "skip": true keeps an edit in the plan without applying it.
 */
final class PlanFile {

    private PlanFile() {}

    static List<Edit> read(String file) throws Exception {
        var root = JsonParser.parseString(Files.readString(Path.of(file)));
        var arr = root.isJsonArray() ? root.getAsJsonArray() : root.getAsJsonObject().getAsJsonArray("edits");
        if (arr == null) Out.die(file + ": expected {\"edits\":[...]} or a JSON array");
        var out = new ArrayList<Edit>();
        for (var i = 0; i < arr.size(); i++) {
            var o = arr.get(i).getAsJsonObject();
            var label = str(o, "id", "#" + (i + 1));
            if (o.has("skip") && o.get("skip").getAsBoolean()) { Out.info(label + ": skipped"); continue; }
            var op = str(o, "op", "");
            out.add(switch (op) {
                case "replace" -> new Edit.Replace(label, str(o, "tab", null), str(o, "in", null), str(o, "find", null), str(o, "with", null),
                        o.has("all") && o.get("all").getAsBoolean(), str(o, "note", null));
                case "insert" -> new Edit.Insert(label, str(o, "tab", null), str(o, "after", null), paras(label, o.getAsJsonArray("paragraphs")), str(o, "note", null));
                case "delete" -> new Edit.Delete(label, str(o, "tab", null), str(o, "from", null), str(o, "to", null), str(o, "note", null));
                default -> { Out.die(label + ": op must be replace, insert or delete, not '" + op + "'"); yield null; }
            });
        }
        return out;
    }

    private static List<Edit.NewPara> paras(String label, JsonArray a) {
        if (a == null) Out.die(label + ": insert needs \"paragraphs\"");
        var out = new ArrayList<Edit.NewPara>();
        for (var e : a) out.add(e.isJsonPrimitive() ? new Edit.NewPara(e.getAsString(), null)
                : new Edit.NewPara(str(e.getAsJsonObject(), "text", ""), str(e.getAsJsonObject(), "like", null)));
        return out;
    }

    private static String str(JsonObject o, String k, String dflt) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : dflt;
    }
}
