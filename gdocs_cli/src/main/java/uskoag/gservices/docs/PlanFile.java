package uskoag.gservices.docs;

import com.google.gson.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A plan is JSON: {"edits":[...]} or a bare array. Each edit:
 *   {"id":"E1","op":"replace","tab":"Summary","in":"Offices:","find":"Paris and Rome.","with":"..."}
 *   {"id":"E2","op":"insert","tab":"Summary","after":"2019, Paris:","paragraphs":["**2021, Lisbon:** text"]}
 * A paragraph may instead be {"text":"...","like":"<start of a paragraph to copy the style of>"}, or
 * {"text":"...","style":"normal"} (or heading1..6, title, subtitle) for a fresh named style with nothing inherited.
 * "list":"bullet" forces a bulleted paragraph with nothing to inherit from; consecutive paragraphs so
 * marked (sharing the same "like", or none) become one list. "list" is bullet or number, "level" (0..8) nests
 * it, "preset" names an API list preset. "spacing":"tight" zeroes spaceAbove/spaceBelow.
 * "skip": true keeps an edit in the plan without applying it.
 */
final class PlanFile {

    /** {"suggest": true, "edits": [...]} applies the whole plan as suggested edits (PRP 08). */
    static boolean suggest;

    private PlanFile() {}

    static List<Edit> read(String file) throws Exception {
        var root = JsonParser.parseString(Files.readString(Path.of(file)));
        if (root.isJsonObject()) checkKeys(root.getAsJsonObject(), file, "suggest", "edits");
        suggest = root.isJsonObject() && root.getAsJsonObject().has("suggest") && root.getAsJsonObject().get("suggest").getAsBoolean();
        var arr = root.isJsonArray() ? root.getAsJsonArray() : root.getAsJsonObject().getAsJsonArray("edits");
        if (arr == null) Out.die(file + ": expected {\"edits\":[...]} or a JSON array");
        var out = new ArrayList<Edit>();
        for (var i = 0; i < arr.size(); i++) {
            var o = arr.get(i).getAsJsonObject();
            var label = str(o, "id", "#" + (i + 1));
            if (o.has("skip") && o.get("skip").getAsBoolean()) { Out.info(label + ": skipped"); continue; }
            var op = str(o, "op", "");
            out.add(switch (op) {
                case "replace" -> {
                    checkKeys(o, label, "id", "op", "skip", "tab", "note", "in", "occurrence", "find", "with", "all", "literal");
                    yield new Edit.Replace(label, str(o, "tab", null), str(o, "in", null), num(o, "occurrence"), str(o, "find", null), str(o, "with", null),
                            flag(o, "all"), flag(o, "literal"), str(o, "note", null));
                }
                case "insert" -> {
                    checkKeys(o, label, "id", "op", "skip", "tab", "note", "after", "afterImage", "occurrence", "paragraphs");
                    if (o.has("after") && o.has("afterImage")) Out.die(label + ": give \"after\" or \"afterImage\", not both");
                    yield new Edit.Insert(label, str(o, "tab", null), o.has("afterImage") ? Scope.IMAGE + str(o, "afterImage", "") : str(o, "after", null), num(o, "occurrence"), paras(label, o.getAsJsonArray("paragraphs")), str(o, "note", null));
                }
                case "delete" -> {
                    checkKeys(o, label, "id", "op", "skip", "tab", "note", "from", "occurrence", "to", "toOccurrence", "toEnd");
                    yield new Edit.Delete(label, str(o, "tab", null), str(o, "from", null), num(o, "occurrence"), str(o, "to", null), num(o, "toOccurrence"), flag(o, "toEnd"), str(o, "note", null));
                }
                case "toc-delete" -> {
                    checkKeys(o, label, "id", "op", "skip", "tab", "note", "index");
                    yield new Edit.TocDelete(label, str(o, "tab", null), num(o, "index"), str(o, "note", null));
                }
                default -> { Out.die(label + ": op must be replace, insert, delete or toc-delete, not '" + op + "'"); yield null; }
            });
        }
        return out;
    }

    private static List<Edit.NewPara> paras(String label, JsonArray a) {
        if (a == null) Out.die(label + ": insert needs \"paragraphs\"");
        var out = new ArrayList<Edit.NewPara>();
        for (var e : a) {
            if (e.isJsonPrimitive()) { out.add(new Edit.NewPara(e.getAsString(), null, null, null, null, null, null)); continue; }
            var o = e.getAsJsonObject();
            checkKeys(o, label, "text", "like", "style", "list", "spacing", "level", "preset", "align", "keepWithNext", "keepLinesTogether");
            var level = o.has("level") && !o.get("level").isJsonNull() ? o.get("level").getAsInt() : null;
            ListPresets.check(label, str(o, "list", null), str(o, "preset", null), level);
            var fmt = new ParaFmt(str(o, "align", null) == null ? null : ParaFmt.api(str(o, "align", null)), bool(o, "keepWithNext"), bool(o, "keepLinesTogether"));
            out.add(new Edit.NewPara(str(o, "text", ""), str(o, "like", null), str(o, "style", null), str(o, "list", null), str(o, "spacing", null), level, str(o, "preset", null), fmt));
        }
        return MdList.expand(out);
    }

    private static Integer num(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : null; }

    private static Boolean bool(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsBoolean() : null; }

    private static boolean flag(JsonObject o, String k) { return o.has(k) && o.get(k).getAsBoolean(); }

    private static String str(JsonObject o, String k, String dflt) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : dflt;
    }

    /** An unrecognised key stays silent otherwise — exactly the trap that made a plan's "list":"bullet"
     * look applied when the field was simply never read (found live, 2026-09-28). */
    private static void checkKeys(JsonObject o, String label, String... allowed) {
        var ok = java.util.Set.of(allowed);
        for (var k : o.keySet()) if (!ok.contains(k)) Out.die(label + ": unknown key \"" + k + "\" (expected one of: " + String.join(", ", allowed) + ")");
    }
}
