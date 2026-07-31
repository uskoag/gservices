package uskoag.gservices;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Converts a date/time string into the Google Sheets serial-number representation, for `--as-date`. */
public class CellDate {

    static final LocalDate SHEETS_EPOCH = LocalDate.of(1899, 12, 30);

    public static double toSerial(String text, String type, String pattern) {
        var t = text.trim();
        try {
            return switch (type) {
                case "TIME" -> LocalTime.parse(t, pattern != null ? DateTimeFormatter.ofPattern(pattern) : DateTimeFormatter.ISO_LOCAL_TIME)
                    .toSecondOfDay() / 86400.0;
                case "DATE_TIME" -> {
                    var dt = LocalDateTime.parse(t, pattern != null ? DateTimeFormatter.ofPattern(pattern) : DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    yield (dt.toLocalDate().toEpochDay() - SHEETS_EPOCH.toEpochDay()) + dt.toLocalTime().toSecondOfDay() / 86400.0;
                }
                default -> (double) (LocalDate.parse(t, pattern != null ? DateTimeFormatter.ofPattern(pattern) : DateTimeFormatter.ISO_LOCAL_DATE)
                    .toEpochDay() - SHEETS_EPOCH.toEpochDay());
            };
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("cannot parse '" + text + "' as " + type
                + (pattern != null ? " with --date-format '" + pattern + "'" : "") + ": " + e.getMessage());
        }
    }
}
