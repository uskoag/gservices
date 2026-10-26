package uskoag.gservices.sheetcli;

import java.util.*;
import uskoag.gservices.CellRange;

/** A spreadsheet as it is now, through the Sheets API: one metadata read, then values and formulas for every tab in two batched reads. */
final class GridLoad {

    private GridLoad() {}

    static Grid live(String id, String onlyTab, String source) throws Exception {
        var sheets = Auth.sheetsRead();
        var ss = sheets.spreadsheets().get(id).setFields("properties.title,sheets.properties(sheetId,title,sheetType)").execute();
        var props = ss.getSheets().stream().map(s -> s.getProperties())
                .filter(p -> p.getSheetType() == null || p.getSheetType().equals("GRID"))
                .filter(p -> onlyTab == null || p.getTitle().equals(onlyTab)).toList();
        if (onlyTab != null && props.isEmpty()) Out.die("no tab \"" + onlyTab + "\" in " + ss.getProperties().getTitle());
        var ranges = props.stream().map(p -> CellRange.a1Sheet(p.getTitle())).toList();
        var time = System.currentTimeMillis();
        var values = batch(id, ranges, "FORMATTED_VALUE");
        var formulas = batch(id, ranges, "FORMULA");
        var tabs = new ArrayList<Grid.Tab>();
        for (var i = 0; i < props.size(); i++)
            tabs.add(new Grid.Tab(props.get(i).getSheetId(), props.get(i).getTitle(), values.get(i), formulas.get(i)));
        return new Grid(id, ss.getProperties().getTitle(), time, source, tabs);
    }

    private static List<List<List<String>>> batch(String id, List<String> ranges, String render) throws Exception {
        var out = new ArrayList<List<List<String>>>();
        if (ranges.isEmpty()) return out;
        Out.info("values.batchGet " + ranges.size() + " tab(s) " + render);
        var res = Auth.sheetsRead().spreadsheets().values().batchGet(id).setRanges(ranges)
                .setValueRenderOption(render).setDateTimeRenderOption("FORMATTED_STRING").execute();
        for (var vr : res.getValueRanges()) {
            var rows = new ArrayList<List<String>>();
            if (vr.getValues() != null)
                for (var row : vr.getValues()) rows.add(row.stream().map(c -> c == null ? "" : String.valueOf(c)).toList());
            out.add(rows);
        }
        return out;
    }
}
