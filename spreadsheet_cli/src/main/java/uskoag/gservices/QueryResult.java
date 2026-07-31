package uskoag.gservices;

import java.util.List;

/**
 * Result of a {@link SheetQuery} run: the result-set column labels and the rows.
 * Each cell is a String, or a Number/Boolean for SQL numeric/boolean columns
 * (so {@code _row} and {@code COUNT(*)} render as JSON numbers), or null.
 */
public record QueryResult(List<String> columns, List<List<Object>> rows) {}
