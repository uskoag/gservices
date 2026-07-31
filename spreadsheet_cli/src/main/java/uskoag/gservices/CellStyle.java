package uskoag.gservices;

import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.RepeatCellRequest;
import com.google.api.services.sheets.v4.model.TextFormat;

import java.util.ArrayList;

/** Builds the RepeatCellRequest (userEnteredFormat) for the `format` command. */
public class CellStyle {

    public static Request buildRequest(GridRange range, String bg, String text, Boolean bold, Boolean italic,
                                        String align, String valign, Integer fontSize, boolean wrap) {
        var format = new com.google.api.services.sheets.v4.model.CellFormat();
        var fields = new ArrayList<String>();

        if (bg != null) {
            format.setBackgroundColor(CliColor.hexToColor(bg));
            fields.add("userEnteredFormat.backgroundColor");
        }

        var textFormat = new TextFormat();
        var hasTextFormat = false;
        if (text != null) { textFormat.setForegroundColor(CliColor.hexToColor(text)); fields.add("userEnteredFormat.textFormat.foregroundColor"); hasTextFormat = true; }
        if (bold != null) { textFormat.setBold(bold); fields.add("userEnteredFormat.textFormat.bold"); hasTextFormat = true; }
        if (italic != null) { textFormat.setItalic(italic); fields.add("userEnteredFormat.textFormat.italic"); hasTextFormat = true; }
        if (fontSize != null) { textFormat.setFontSize(fontSize); fields.add("userEnteredFormat.textFormat.fontSize"); hasTextFormat = true; }
        if (hasTextFormat) format.setTextFormat(textFormat);

        if (align != null) { format.setHorizontalAlignment(align.toUpperCase()); fields.add("userEnteredFormat.horizontalAlignment"); }
        if (valign != null) { format.setVerticalAlignment(valign.toUpperCase()); fields.add("userEnteredFormat.verticalAlignment"); }
        if (wrap) { format.setWrapStrategy("WRAP"); fields.add("userEnteredFormat.wrapStrategy"); }

        if (fields.isEmpty()) throw new IllegalArgumentException("no formatting flags given");

        var cell = new CellData().setUserEnteredFormat(format);
        var repeatCell = new RepeatCellRequest().setRange(range).setCell(cell).setFields(String.join(",", fields));
        return new Request().setRepeatCell(repeatCell);
    }
}
