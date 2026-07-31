package uskoag.gservices;

import com.google.api.services.sheets.v4.model.CellData;

import java.util.LinkedHashMap;
import java.util.Map;

/** Builds the per-cell {value, formula, note, color, numberFormat} map for `read --rich`. */
public class CellRich {

    public static Map<String, Object> cellMap(CellData cell) {
        var m = new LinkedHashMap<String, Object>();
        m.put("value", (cell == null || cell.getFormattedValue() == null) ? "" : cell.getFormattedValue());
        if (cell == null) return m;

        var uv = cell.getUserEnteredValue();
        if (uv != null && uv.getFormulaValue() != null) m.put("formula", uv.getFormulaValue());

        if (cell.getNote() != null && !cell.getNote().isEmpty()) m.put("note", cell.getNote());

        var ef = cell.getEffectiveFormat();
        if (ef != null) {
            var hex = CellInspect.colorToHex(ef.getBackgroundColor());
            if (!CellInspect.isWhite(hex)) m.put("color", hex);

            var nf = ef.getNumberFormat();
            if (nf != null) {
                var nfMap = new LinkedHashMap<String, String>();
                nfMap.put("type", nf.getType());
                nfMap.put("pattern", nf.getPattern() != null ? nf.getPattern() : "");
                m.put("numberFormat", nfMap);
            }
        }
        return m;
    }
}
