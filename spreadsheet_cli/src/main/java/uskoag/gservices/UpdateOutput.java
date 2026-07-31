package uskoag.gservices;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Renders an {@link UpdateDiff} plus commit metadata as json (default) or tsv. */
public class UpdateOutput {

    public static String format(UpdateDiff d, boolean dryRun, int written, String fmt) {
        return switch (fmt.toLowerCase()) {
            case "json" -> json(d, dryRun, written);
            case "tsv"  -> tsv(d);
            default -> throw new IllegalArgumentException("update --format must be json|tsv (got '" + fmt + "')");
        };
    }

    private static String json(UpdateDiff d, boolean dryRun, int written) {
        var root = new LinkedHashMap<String, Object>();
        root.put("affected", d.affected());
        root.put("changedCells", d.changes().size());
        root.put("written", written);
        root.put("dryRun", dryRun);
        var list = new ArrayList<Map<String, Object>>();
        for (var c : d.changes()) {
            var m = new LinkedHashMap<String, Object>();
            m.put("row", c.row());
            m.put("cell", c.cell());
            m.put("col", c.col());
            m.put("old", c.oldVal());
            m.put("new", c.newVal());
            list.add(m);
        }
        root.put("changes", list);
        Gson g = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
        return g.toJson(root);
    }

    private static String tsv(UpdateDiff d) {
        var sb = new StringBuilder("row\tcell\tcol\told\tnew\n");
        for (var c : d.changes())
            sb.append(c.row()).append("\t").append(c.cell()).append("\t").append(c.col())
              .append("\t").append(esc(c.oldVal())).append("\t").append(esc(c.newVal())).append("\n");
        return sb.toString().stripTrailing();
    }

    private static String esc(String v) {
        return v == null ? "" : v.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "");
    }
}
