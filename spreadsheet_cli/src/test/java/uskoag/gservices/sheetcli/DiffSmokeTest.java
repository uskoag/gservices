package uskoag.gservices.sheetcli;

import java.util.*;

/** mvn exec:java -Dexec.mainClass=uskoag.gservices.sheetcli.DiffSmokeTest -Dexec.classpathScope=test */
public final class DiffSmokeTest {

    public static void main(String[] args) {
        var cols = 16;
        var oldV = new ArrayList<List<String>>();
        var oldF = new ArrayList<List<String>>();
        var header = new ArrayList<String>();
        for (var c = 0; c < cols; c++) header.add("H" + A1.col(c));
        oldV.add(header); oldF.add(header);
        for (var r = 1; r <= 8; r++) {
            var v = new ArrayList<String>();
            v.add("Platform" + r);
            for (var c = 1; c < cols; c++) v.add(String.valueOf(r * 100 + c));
            oldV.add(v); oldF.add(new ArrayList<>(v));
        }
        var total = new ArrayList<String>(List.of("TOTAL"));
        for (var c = 1; c < cols; c++) total.add("9" + c);
        oldV.add(total);
        var totalF = new ArrayList<>(total);
        totalF.set(2, "=SUM(C2:C9)");
        oldF.add(totalF);

        var newV = deep(oldV);
        var newF = deep(oldF);
        newV.remove(3); newF.remove(3);
        var t = newV.remove(newV.size() - 1); var tf = newF.remove(newF.size() - 1);
        newV.add(2, t); newF.add(2, tf);
        var tr = 2;
        newV.get(tr).set(2, "999"); newF.get(tr).set(2, "999");
        newV.get(0).set(5, "HFF"); newF.get(0).set(5, "HFF");
        newV.get(4).set(7, "#REF!"); newF.get(4).set(7, "=A1+#REF!");
        for (var r = 0; r < newV.size(); r++)
            for (var k = 0; k < 4; k++) { newV.get(r).add(12, r == 0 ? "NEW" + k : ""); newF.get(r).add(12, r == 0 ? "NEW" + k : ""); }

        var a = new Grid("X", "Test", 1, "old", List.of(new Grid.Tab(0, "Platform Report", oldV, oldF)));
        var b = new Grid("X", "Test", 2, "new", List.of(new Grid.Tab(0, "Platform Report", newV, newF)));
        DiffReport.text(GridDiff.of(a, b, null, false), null, false, 50);
    }

    private static List<List<String>> deep(List<List<String>> g) {
        var out = new ArrayList<List<String>>();
        for (var r : g) out.add(new ArrayList<>(r));
        return out;
    }
}
