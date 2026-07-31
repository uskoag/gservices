package uskoag.gservices;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Standalone smoke test for the query engine — no Google auth needed.
 *  Run: mvn exec:java -Dexec.mainClass="uskoag.gservices.QuerySmokeTest" -Dexec.classpathScope=test */
public class QuerySmokeTest {

    public static void main(String[] args) {
        // Sheet rows 1..5; row 1 is a header; row 5 is ragged (missing C,D → blank).
        List<List<Object>> data = new ArrayList<>();
        data.add(row("Name", "City", "Email", "Emailed"));
        data.add(row("Alice", "NYC", "alice@gmail.com", ""));
        data.add(row("Bob", "LA", "bob@x.com", "yes"));
        data.add(row("Cara", "SF", "alice@gmail.com", ""));
        data.add(row("Dan", "LA"));

        show("not yet emailed (D empty, skip header)",
            "SELECT _row, A, C FROM t WHERE _row>1 AND D=''", data, "json");

        show("duplicate emails (GROUP BY / COUNT / HAVING)",
            "SELECT C, COUNT(*) n FROM t WHERE _row>1 AND C<>'' GROUP BY C HAVING COUNT(*)>1", data, "json");

        show("distinct cities (tsv)",
            "SELECT DISTINCT B FROM t WHERE _row>1", data, "tsv");

        show("keyword-safe + numeric _row is a JSON number",
            "SELECT _row, A FROM t WHERE _row>1 ORDER BY _row DESC LIMIT 2", data, "json");

        // ── update: before→after diff (no Google write here; just the engine) ──
        UpdateDiff d1 = SheetUpdate.run(data, 1, "UPDATE t SET D='yes' WHERE C='alice@gmail.com'");
        System.out.println("### UPDATE mark alice rows emailed (affected=" + d1.affected() + ")");
        System.out.println(UpdateOutput.format(d1, false, d1.changes().size(), "json"));
        System.out.println();

        UpdateDiff d2 = SheetUpdate.run(data, 1, "UPDATE t SET B='LA' WHERE _row=2");
        System.out.println("### UPDATE no-op-safe: NYC->LA on row 2 (tsv)");
        System.out.println(UpdateOutput.format(d2, false, d2.changes().size(), "tsv"));
        System.out.println();

        UpdateDiff d3 = SheetUpdate.run(data, 1, "UPDATE t SET D='x' WHERE C='nobody@x.com'");
        System.out.println("### UPDATE WHERE matches nothing -> empty changes (affected=" + d3.affected() + ")");
        System.out.println(UpdateOutput.format(d3, true, 0, "json"));
        System.out.println();

        try {
            SheetUpdate.run(data, 1, "DELETE FROM t WHERE _row=2");
        } catch (RuntimeException e) {
            System.out.println("### guard: non-UPDATE rejected -> " + e.getMessage());
        }
    }

    static List<Object> row(Object... cells) { return new ArrayList<>(Arrays.asList(cells)); }

    static void show(String title, String sql, List<List<Object>> data, String fmt) {
        System.out.println("### " + title);
        System.out.println("    " + sql);
        System.out.println(QueryOutput.format(SheetQuery.run(data, 1, sql), fmt));
        System.out.println();
    }
}
