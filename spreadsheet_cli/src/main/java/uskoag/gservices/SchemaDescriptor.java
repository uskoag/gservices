package uskoag.gservices;

import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.RowData;

import java.util.ArrayList;
import java.util.List;

/** Builds a SheetSchema from already-fetched header/data grid cells — column-extent auto-detection,
 *  header/comment extraction, row conversion, sampling, and truncation. No API calls in this class. */
public class SchemaDescriptor {

    public static SheetSchema describe(String sheetName, int sheetId, int headerRowNum, int dataStartRow,
                                        int colStart, Integer colEndOverride,
                                        List<CellData> fullHeaderRow, List<RowData> dataRows,
                                        int sampleRowsAnalyzed, int sampleSize, String uniquenessCol, int truncateLen) {
        int colEnd = colEndOverride != null ? colEndOverride : detectColumnEnd(fullHeaderRow, colStart);
        var header = buildHeaderRow(fullHeaderRow, colStart, colEnd);
        var rows = toRows(dataRows, dataStartRow, colStart, colEnd);

        int uniquenessIdx0 = CellRange.letterToCol(uniquenessCol.toUpperCase()) - colStart;
        var selected = SchemaSampler.sample(rows, uniquenessIdx0, sampleSize);
        var sampleData = pivotSampleData(selected, colStart, colEnd, truncateLen);

        return new SheetSchema(sheetName, sheetId, headerRowNum, dataStartRow, colStart, colEnd,
            sampleRowsAnalyzed, sampleSize, uniquenessCol.toUpperCase(), header, sampleData);
    }

    /** Last non-empty header cell at/after colStart (1-based), or colStart itself if the header is empty. */
    static int detectColumnEnd(List<CellData> fullHeaderRow, int colStart) {
        int lastNonEmpty = colStart;
        for (int i = 0; i < fullHeaderRow.size(); i++) {
            int col1 = i + 1;
            if (col1 < colStart) continue;
            var cell = fullHeaderRow.get(i);
            if (cell != null && cell.getFormattedValue() != null && !cell.getFormattedValue().isEmpty()) lastNonEmpty = col1;
        }
        return lastNonEmpty;
    }

    static List<SchemaHeaderCell> buildHeaderRow(List<CellData> fullHeaderRow, int colStart, int colEnd) {
        var out = new ArrayList<SchemaHeaderCell>();
        for (int col1 = colStart; col1 <= colEnd; col1++) {
            var cell = (col1 - 1 < fullHeaderRow.size()) ? fullHeaderRow.get(col1 - 1) : null;
            var value = (cell != null && cell.getFormattedValue() != null) ? cell.getFormattedValue() : "";
            var comment = (cell != null && cell.getNote() != null) ? cell.getNote() : "";
            out.add(new SchemaHeaderCell(col1, CellRange.colToLetter(col1), value, comment));
        }
        return out;
    }

    static List<SchemaRow> toRows(List<RowData> dataRows, int dataStartRow, int colStart, int colEnd) {
        var out = new ArrayList<SchemaRow>();
        int numCols = colEnd - colStart + 1;
        for (int i = 0; i < dataRows.size(); i++) {
            var cells = dataRows.get(i).getValues();
            var vals = new ArrayList<String>();
            for (int j = 0; j < numCols; j++) {
                var cell = (cells != null && j < cells.size()) ? cells.get(j) : null;
                vals.add((cell != null && cell.getFormattedValue() != null) ? cell.getFormattedValue() : "");
            }
            out.add(new SchemaRow(dataStartRow + i, vals));
        }
        return out;
    }

    static List<SchemaSampleColumn> pivotSampleData(List<SchemaRow> selected, int colStart, int colEnd, int truncateLen) {
        var out = new ArrayList<SchemaSampleColumn>();
        for (int col1 = colStart; col1 <= colEnd; col1++) {
            int j = col1 - colStart;
            var values = new ArrayList<SchemaSampleValue>();
            for (var row : selected) {
                var raw = (j < row.cells().size()) ? row.cells().get(j) : "";
                values.add(new SchemaSampleValue(row.rowNum(), truncate(raw, truncateLen)));
            }
            out.add(new SchemaSampleColumn(col1, CellRange.colToLetter(col1), values));
        }
        return out;
    }

    /** Preserves both ends of a long value rather than a plain tail-cut, so head+tail context survives. */
    static String truncate(String value, int truncateLen) {
        if (value == null) return "";
        if (value.length() <= truncateLen) return value;
        var head = value.substring(0, truncateLen / 2);
        var tail = value.substring(value.length() - truncateLen / 2);
        return head + " ... (sample truncated,total length " + value.length() + " characters) " + tail;
    }
}
