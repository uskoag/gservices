package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.TableCellStyle;
import java.util.ArrayList;
import java.util.List;

/** A cell: paragraphs and, since Docs allows them, tables nested in it. */
final class SrcCell {

    int row, col, rowSpan = 1, colSpan = 1;
    TableCellStyle style;
    final List<Unit> units = new ArrayList<>();
}
