package uskoag.gservices;

import java.util.List;

/** One column's sampled values, always from the same set of sampled rows across all columns. */
public record SchemaSampleColumn(int colNum, String colAddr, List<SchemaSampleValue> sampleValues) {}
