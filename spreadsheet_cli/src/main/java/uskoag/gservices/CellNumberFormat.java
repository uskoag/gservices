package uskoag.gservices;

import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.CellFormat;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.NumberFormat;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.RepeatCellRequest;

import java.util.Set;

/** Builds the RepeatCellRequest (userEnteredFormat.numberFormat) for the `numberformat` command. */
public class CellNumberFormat {

    static final Set<String> TYPES = Set.of(
        "DATE", "TIME", "DATE_TIME", "NUMBER", "PERCENT", "CURRENCY", "SCIENTIFIC", "TEXT");

    public static Request buildRequest(GridRange range, String type, String pattern) {
        if (type == null) throw new IllegalArgumentException("--type is required (or use --clear)");
        var upper = type.toUpperCase();
        if (!TYPES.contains(upper)) throw new IllegalArgumentException("--type must be one of " + TYPES);

        var nf = new NumberFormat().setType(upper);
        var p = pattern != null ? pattern : defaultPattern(upper);
        if (p != null) nf.setPattern(p);

        var cell = new CellData().setUserEnteredFormat(new CellFormat().setNumberFormat(nf));
        var repeatCell = new RepeatCellRequest().setRange(range).setCell(cell).setFields("userEnteredFormat.numberFormat");
        return new Request().setRepeatCell(repeatCell);
    }

    public static Request buildClearRequest(GridRange range) {
        var cell = new CellData().setUserEnteredFormat(new CellFormat());
        var repeatCell = new RepeatCellRequest().setRange(range).setCell(cell).setFields("userEnteredFormat.numberFormat");
        return new Request().setRepeatCell(repeatCell);
    }

    static String defaultPattern(String type) {
        return switch (type) {
            case "DATE" -> "yyyy-mm-dd";
            case "TIME" -> "hh:mm:ss";
            case "DATE_TIME" -> "yyyy-mm-dd hh:mm:ss";
            case "NUMBER" -> "#,##0.###";
            case "PERCENT" -> "0.00%";
            case "CURRENCY" -> "$#,##0.00";
            case "SCIENTIFIC" -> "0.00E+00";
            default -> null; // TEXT has no pattern
        };
    }
}
