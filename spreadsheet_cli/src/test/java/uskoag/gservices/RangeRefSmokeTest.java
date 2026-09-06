package uskoag.gservices;

/** Standalone smoke test for A1 reference building and the '+'-in-path escaping fix — no Google
 *  auth and no network, it only builds the request and reads back the URL the client would send.
 *  Run: mvn exec:java -Dexec.mainClass="uskoag.gservices.RangeRefSmokeTest" -Dexec.classpathScope=test */
public class RangeRefSmokeTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("### CellRange.a1Ref — quoting the tab title");
        eq("'Sheet1'!A1:C3",        CellRange.a1Ref("Sheet1", "A1:C3"));
        eq("'Q1 2026'!A1:C3",       CellRange.a1Ref("Q1 2026", "A1:C3"));
        eq("'spark-1+2'!A1:C3",     CellRange.a1Ref("spark-1+2", "A1:C3"));
        eq("'Sheet''s data'!A1",    CellRange.a1Ref("Sheet's data", "A1"));
        eq("'2026'!A1",             CellRange.a1Ref("2026", "A1"));
        eq("'already quoted'!A1",   CellRange.a1Ref("'already quoted'", "A1"));   // not quoted twice
        eq("'Sheet1'",              CellRange.a1Sheet("Sheet1"));

        // round trip: a reference we build is a reference we can take apart again
        eq("Q1 2026",  CellRange.sheetNameOf(CellRange.a1Ref("Q1 2026", "A1:C3"), "?"));
        eq("Sheet's data", CellRange.sheetNameOf(CellRange.a1Ref("Sheet's data", "A1"), "?"));
        eq("A1:C3",    CellRange.rangeOnlyOf(CellRange.a1Ref("Q1 2026", "A1:C3")));

        System.out.println();
        System.out.println("### the URL the Sheets client would actually send");
        // A range travels in the URL path. Before the fix, "%2B" was decoded and re-encoded back to a
        // bare '+', which Google's frontend then read as a space — "Unable to parse range: spark-1 2".
        var sheets = SheetsService.sheets(
            ServiceAccess.direct(request -> { }, "smoke-test", "smoke-test"), "smoke-test");
        var url = sheets.spreadsheets().values()
            .get("SSID", CellRange.a1Ref("spark-1+2", "A1:C3"))
            .buildHttpRequest().getUrl().build();
        System.out.println("  " + url);
        contains("%2B", url);
        absent("spark-1+2", url);

        var plain = sheets.spreadsheets().values()
            .get("SSID", CellRange.a1Ref("Sheet1", "A1:C3"))
            .buildHttpRequest().getUrl().build();
        System.out.println("  " + plain);
        contains("/values/'Sheet1'!A1:C3", plain);   // ordinary ranges are left exactly as they were

        System.out.println();
        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        if (failures > 0) System.exit(1);
    }

    private static void eq(String expected, String actual) {
        report(expected.equals(actual), actual, "expected \"" + expected + "\"");
    }

    private static void contains(String needle, String actual) {
        report(actual.contains(needle), actual, "expected to contain \"" + needle + "\"");
    }

    private static void absent(String needle, String actual) {
        report(!actual.contains(needle), actual, "expected NOT to contain \"" + needle + "\"");
    }

    private static void report(boolean ok, String actual, String what) {
        if (ok) {
            System.out.println("  ok   " + actual);
        } else {
            failures++;
            System.out.println("  FAIL " + actual + "  — " + what);
        }
    }
}
