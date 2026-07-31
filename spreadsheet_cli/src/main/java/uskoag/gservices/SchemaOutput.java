package uskoag.gservices;

/** Renders a SpreadsheetSchema as md/text (json is emitted directly via GSON by the caller).
 *  Plain nested lists, not tables — easier to read outside a rendered-markdown viewer. */
public class SchemaOutput {

    public static String render(SpreadsheetSchema schema, String format) {
        var md = format.equalsIgnoreCase("md");
        var sb = new StringBuilder();
        sb.append(md ? "# " : "").append(schema.spreadsheetName()).append(" (").append(schema.spreadsheetId()).append(")\n\n");
        if (schema.spreadsheetNotes() != null && !schema.spreadsheetNotes().isBlank()) {
            sb.append(schema.spreadsheetNotes()).append("\n\n");
        }
        for (var sheet : schema.sheets()) sb.append(renderSheet(sheet, md));
        return sb.toString();
    }

    private static String renderSheet(SheetSchema sheet, boolean md) {
        var sb = new StringBuilder();
        sb.append(md ? "## " : "Sheet: ").append(sheet.sheetName()).append(" (id ").append(sheet.sheetId()).append(")\n");
        sb.append("Header row ").append(sheet.headerRowNum())
          .append(", data starts row ").append(sheet.dataStartRow())
          .append(", columns ").append(CellRange.colToLetter(sheet.dataColumnStart()))
          .append("-").append(CellRange.colToLetter(sheet.dataColumnEnd()))
          .append(", sampled ").append(sheet.dataSampledRows()).append(" rows -> ").append(sheet.dataSampleSize())
          .append(", uniqueness column ").append(sheet.dataUniquenessCriteriaColumn()).append("\n\n");

        sb.append(md ? "### Columns\n" : "Columns:\n");
        for (var h : sheet.headerRow()) {
            sb.append("- ").append(h.colAddr()).append(" (").append(h.colNum()).append("): \"").append(h.value()).append("\"");
            if (h.comment() != null && !h.comment().isEmpty()) sb.append(" - ").append(h.comment());
            sb.append("\n");
        }

        sb.append(md ? "\n### Sample data\n" : "\nSample data:\n");
        for (var col : sheet.sampleData()) {
            sb.append("- ").append(col.colAddr()).append(":\n");
            for (var v : col.sampleValues()) {
                sb.append("  - row ").append(v.rowNum()).append(": ").append(v.value()).append("\n");
            }
        }
        sb.append("\n");
        return sb.toString();
    }
}
