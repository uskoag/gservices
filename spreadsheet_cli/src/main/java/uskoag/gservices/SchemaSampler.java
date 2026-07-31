package uskoag.gservices;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

/**
 * Uniqueness-first, then-random sampling: one row per distinct value in the uniqueness-criteria
 * column (picked randomly within each group), topped up with random leftover rows if there aren't
 * enough distinct values to reach sampleSize. Never sequential/mechanical row selection.
 */
public class SchemaSampler {

    public static List<SchemaRow> sample(List<SchemaRow> rows, int uniquenessColIdx0, int sampleSize) {
        var random = new Random();
        var groups = new LinkedHashMap<String, List<SchemaRow>>();
        for (var row : rows) {
            if (uniquenessColIdx0 < 0 || uniquenessColIdx0 >= row.cells().size()) continue;
            groups.computeIfAbsent(row.cells().get(uniquenessColIdx0), k -> new ArrayList<>()).add(row);
        }

        var keys = new ArrayList<>(groups.keySet());
        Collections.shuffle(keys, random);

        var selected = new LinkedHashSet<SchemaRow>();
        for (var key : keys) {
            if (selected.size() >= sampleSize) break;
            var candidates = groups.get(key);
            selected.add(candidates.get(random.nextInt(candidates.size())));
        }

        if (selected.size() < sampleSize) {
            var leftover = new ArrayList<>(rows);
            leftover.removeAll(selected);
            Collections.shuffle(leftover, random);
            for (var row : leftover) {
                if (selected.size() >= sampleSize) break;
                selected.add(row);
            }
        }

        var result = new ArrayList<>(selected);
        result.sort(Comparator.comparingInt(SchemaRow::rowNum));
        return result;
    }
}
