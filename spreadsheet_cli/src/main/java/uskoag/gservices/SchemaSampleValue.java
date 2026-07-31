package uskoag.gservices;

/** One sampled cell value, tagged with its actual sheet row number for cross-column correlation. */
public record SchemaSampleValue(int rowNum, String value) {}
