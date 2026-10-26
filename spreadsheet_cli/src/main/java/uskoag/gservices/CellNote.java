package uskoag.gservices;

import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.GridCoordinate;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.RowData;
import com.google.api.services.sheets.v4.model.UpdateCellsRequest;

import java.util.List;

/** Builds the UpdateCells request for the `note` command: writes hover notes only, leaving values and formats untouched. */
public class CellNote {

    public static Request buildRequest(int sheetId, int startRow, int startCol, List<List<String>> notes) {
        var rows = notes.stream()
            .map(r -> new RowData().setValues(r.stream().map(n -> new CellData().setNote(n == null ? "" : n)).toList()))
            .toList();
        return new Request().setUpdateCells(new UpdateCellsRequest()
            .setStart(new GridCoordinate().setSheetId(sheetId).setRowIndex(startRow - 1).setColumnIndex(startCol - 1))
            .setRows(rows)
            .setFields("note"));
    }
}
