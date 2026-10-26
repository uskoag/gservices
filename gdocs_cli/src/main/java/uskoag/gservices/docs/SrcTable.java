package uskoag.gservices.docs;

import java.util.ArrayList;
import java.util.List;

/** A source table: cells in row-major order, covered cells of a merge included, as the API lists them. */
final class SrcTable implements Unit {

    int rows, cols;
    boolean bordered = true;
    final List<SrcCell> cells = new ArrayList<>();
    final List<Double> widths = new ArrayList<>(), minHeights = new ArrayList<>();

    SrcCell cell(int r, int c) { return cells.get(r * cols + c); }
}
