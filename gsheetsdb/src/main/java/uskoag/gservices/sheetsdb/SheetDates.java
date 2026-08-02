package uskoag.gservices.sheetsdb;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Spreadsheet serial dates, in both directions.
 *
 * <p><b>Why a model declares a date column as {@code Double} and not as {@code LocalDate}.</b> The
 * DataHelper processor's field whitelist is primitives, boxed primitives, {@code String}, other
 * DataHelper types, {@code List} and {@code Map}. A {@code LocalDate} field is rejected outright at
 * compile time, so a {@code @Data} model cannot declare one today.
 *
 * <p>{@code Double} is the right column type to use in the meantime, and not merely the available
 * one. A spreadsheet stores a date as a number of days since 1899-12-30, and reading with
 * {@code UNFORMATTED_VALUE} is what hands that number back. Keeping the field numeric means the value
 * round-trips exactly and the cell stays a real date to the sheet's own formulas. Declaring it
 * {@code String} would be worse than it looks — an unformatted date cell arrives as {@code 35502.0},
 * so a String field would faithfully store {@code "35502"}.
 *
 * <pre>
 * &#64;Data
 * public final class Treaty extends Treaty_A {
 *     String treatyId;
 *     Double signedOn;                       // serial date
 *
 *     public LocalDate signedOnDate() {      // named to avoid the generated accessors
 *         return SheetDates.toLocalDate(signedOn);
 *     }
 * }
 * </pre>
 *
 * <p>This library converts {@link LocalDate} and {@link LocalDateTime} properties correctly wherever
 * it meets them, so a hand-written {@code DataHelper_I} may use them directly, and {@code @Data}
 * models will need no change here if the processor's whitelist ever grows.
 */
public final class SheetDates {

    private SheetDates() {}

    /** Day zero of the spreadsheet serial scheme: 1899-12-30. */
    public static LocalDate epoch() {
        return SheetValues.EPOCH;
    }

    /** A serial number as a date, or {@code null}. The time of day, if any, is discarded. */
    public static LocalDate toLocalDate(Number serial) {
        if (serial == null) return null;
        return SheetValues.EPOCH.plusDays((long) Math.floor(serial.doubleValue()));
    }

    /** A serial number as a date and time, or {@code null}. */
    public static LocalDateTime toLocalDateTime(Number serial) {
        if (serial == null) return null;
        double value = serial.doubleValue();
        long days = (long) Math.floor(value);
        long nanos = Math.round((value - days) * 86_400d * 1_000_000_000d);
        return SheetValues.EPOCH.plusDays(days).atStartOfDay().plusNanos(nanos);
    }

    /** A date as the serial number to store, or {@code null}. */
    public static Double toSerial(LocalDate date) {
        if (date == null) return null;
        return (double) ChronoUnit.DAYS.between(SheetValues.EPOCH, date);
    }

    /** A date and time as the serial number to store, or {@code null}. */
    public static Double toSerial(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        return ChronoUnit.DAYS.between(SheetValues.EPOCH, dateTime.toLocalDate())
                + dateTime.toLocalTime().toNanoOfDay() / 1_000_000_000d / 86_400d;
    }
}
