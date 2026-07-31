package uskoag.gservices;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Renders a {@link QueryResult} as json (objects keyed by result column), tsv, or csv. */
public class QueryOutput {

    public static String format(QueryResult r, String fmt) {
        return switch (fmt.toLowerCase()) {
            case "json" -> json(r);
            case "tsv"  -> tsv(r);
            case "csv"  -> csv(r);
            default -> throw new IllegalArgumentException(
                "query --format must be json|tsv|csv (got '" + fmt + "')");
        };
    }

    private static String json(QueryResult r) {
        var list = new ArrayList<Map<String, Object>>(r.rows().size());
        for (var row : r.rows()) {
            var m = new LinkedHashMap<String, Object>();
            for (var c = 0; c < r.columns().size(); c++) m.put(r.columns().get(c), row.get(c));
            list.add(m);
        }
        Gson g = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
        return g.toJson(list);
    }

    private static String tsv(QueryResult r) {
        var sb = new StringBuilder(String.join("\t", r.columns())).append("\n");
        for (var row : r.rows())
            sb.append(row.stream().map(QueryOutput::tsvCell).collect(Collectors.joining("\t"))).append("\n");
        return sb.toString().stripTrailing();
    }

    private static String tsvCell(Object v) {
        return v == null ? ""
            : v.toString().replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "");
    }

    private static String csv(QueryResult r) {
        var sb = new StringBuilder(r.columns().stream().map(QueryOutput::csvCell).collect(Collectors.joining(","))).append("\n");
        for (var row : r.rows())
            sb.append(row.stream().map(QueryOutput::csvCell).collect(Collectors.joining(","))).append("\n");
        return sb.toString().stripTrailing();
    }

    private static String csvCell(Object v) {
        var s = (v == null) ? "" : v.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}
