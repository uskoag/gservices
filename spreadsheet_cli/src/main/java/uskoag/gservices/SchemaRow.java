package uskoag.gservices;

import java.util.List;

/** One analyzed data row: its 1-based sheet row number and its formatted cell values (colStart-aligned). */
record SchemaRow(int rowNum, List<String> cells) {}
