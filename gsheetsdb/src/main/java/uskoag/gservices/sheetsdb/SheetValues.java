package uskoag.gservices.sheetsdb;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * The scalar conversions between a spreadsheet cell, an H2 column and a Java field.
 *
 * <h3>Read every range with UNFORMATTED_VALUE</h3>
 *
 * <p>This class assumes it. With the API's default {@code FORMATTED_VALUE} a numeric cell arrives as
 * the string the user sees — {@code "1,234"}, {@code "TRUE"}, {@code "$5.00"}, {@code "2 Aug 2026"} —
 * and every typed column downstream quietly fills with garbage that only fails much later. With
 * {@code UNFORMATTED_VALUE} the API hands back {@link String}, {@link Double} or {@link Boolean} and
 * nothing else.
 *
 * <h3>Dates are numbers, and that is not a bug</h3>
 *
 * <p>The consequence of unformatted reads: a date cell comes back as a <b>serial number</b> — days
 * since 1899-12-30, the fractional part being the time of day — not as text. So {@link LocalDate} and
 * {@link LocalDateTime} fields convert through {@link #EPOCH} in both directions, and a date is
 * written back as a number so it stays a real date to the spreadsheet's own formulas rather than
 * degrading into text on the first save.
 *
 * <p>The one wrinkle worth knowing: writing a serial number into a column that carries no date
 * formatting displays it as a number. Round-tripping is exact either way; it is only what a human
 * sees in a brand-new column, and it is fixed once with Format &gt; Number &gt; Date in the sheet.
 */
final class SheetValues {

    /** Day zero of the spreadsheet serial-date scheme. Lotus 1-2-3's, inherited via Excel. */
    static final LocalDate EPOCH = LocalDate.of(1899, 12, 30);

    private static final double SECONDS_PER_DAY = 86_400d;

    private SheetValues() {}

    // ---- H2 schema -------------------------------------------------------------

    /** The H2 column type for a declared field type. Anything unrecognised is stored as text. */
    static String h2Type(Class<?> t) {
        if (t == String.class) return "VARCHAR";
        if (t == Integer.class || t == int.class) return "INT";
        if (t == Long.class || t == long.class) return "BIGINT";
        if (t == Double.class || t == double.class) return "DOUBLE PRECISION";
        if (t == Float.class || t == float.class) return "DOUBLE PRECISION";
        if (t == Boolean.class || t == boolean.class) return "BOOLEAN";
        if (t == LocalDate.class) return "DATE";
        if (t == LocalDateTime.class) return "TIMESTAMP";
        if (t == BigDecimal.class) return "DECIMAL(38,10)";
        return "VARCHAR";
    }

    // ---- sheet cell -> Java ----------------------------------------------------

    /**
     * One cell as the declared field type, or {@code null} when the cell is empty or cannot be read
     * as that type.
     *
     * <p>Unreadable is deliberately {@code null} rather than an exception: a spreadsheet is edited by
     * people, a single stray cell is normal, and failing the whole load over one of them would make
     * the library unusable against real data. A caller who needs strictness can check for nulls.
     */
    static Object toJava(Object cell, Class<?> target) {
        if (cell == null || target == null) return null;
        final String s = cell instanceof String str ? str : null;
        if (s != null && s.isBlank() && target != String.class) return null;

        try {
            if (target == String.class) return asString(cell);
            if (target == Integer.class || target == int.class) return (int) asDouble(cell);
            if (target == Long.class || target == long.class) return (long) asDouble(cell);
            if (target == Double.class || target == double.class) return asDouble(cell);
            if (target == Float.class || target == float.class) return (float) asDouble(cell);
            if (target == Boolean.class || target == boolean.class) return asBoolean(cell);
            if (target == BigDecimal.class) return new BigDecimal(asString(cell));
            if (target == LocalDate.class) return asDate(cell);
            if (target == LocalDateTime.class) return asDateTime(cell);
            if (target.isEnum()) return asEnum(cell, target);
            return asString(cell);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String asString(Object cell) {
        if (cell instanceof String s) return s;
        if (cell instanceof Double d && d == Math.floor(d) && !d.isInfinite()) {
            // Every unformatted number arrives as a Double, so an id typed as 1234 would otherwise
            // stringify to "1234.0" and stop matching the key it is supposed to be.
            return String.valueOf(d.longValue());
        }
        return String.valueOf(cell);
    }

    private static double asDouble(Object cell) {
        if (cell instanceof Number n) return n.doubleValue();
        if (cell instanceof Boolean b) return b ? 1 : 0;
        return Double.parseDouble(String.valueOf(cell).trim().replace(",", ""));
    }

    private static Boolean asBoolean(Object cell) {
        if (cell instanceof Boolean b) return b;
        if (cell instanceof Number n) return n.doubleValue() != 0;
        String s = String.valueOf(cell).trim();
        if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes") || s.equals("1")) return Boolean.TRUE;
        if (s.equalsIgnoreCase("false") || s.equalsIgnoreCase("no") || s.equals("0")) return Boolean.FALSE;
        return null;
    }

    private static LocalDate asDate(Object cell) {
        if (cell instanceof Number n) return EPOCH.plusDays((long) Math.floor(n.doubleValue()));
        return LocalDate.parse(String.valueOf(cell).trim());
    }

    private static LocalDateTime asDateTime(Object cell) {
        if (cell instanceof Number n) {
            double serial = n.doubleValue();
            long days = (long) Math.floor(serial);
            long nanos = Math.round((serial - days) * SECONDS_PER_DAY * 1_000_000_000d);
            return EPOCH.plusDays(days).atStartOfDay().plusNanos(nanos);
        }
        return LocalDateTime.parse(String.valueOf(cell).trim());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object asEnum(Object cell, Class<?> target) {
        String name = String.valueOf(cell).trim();
        if (name.isEmpty()) return null;
        return Enum.valueOf((Class<Enum>) target, name);
    }

    // ---- H2 column -> Java -----------------------------------------------------

    /**
     * One JDBC value as the declared field type.
     *
     * <p>Separate from {@link #toJava} because the mirror is typed: H2 hands back a real
     * {@link LocalDate} where the API handed back a serial number. The temporal cases are spelled out
     * rather than left to a cast because the JDBC type H2 returns for {@code DATE}/{@code TIMESTAMP}
     * has changed between major versions, and a driver upgrade should not silently null a column.
     */
    static Object fromSql(Object value, Class<?> target) {
        if (value == null || target == null) return null;

        if (target.isEnum()) return asEnum(value, target);

        if (target == LocalDate.class) {
            if (value instanceof LocalDate d) return d;
            if (value instanceof java.sql.Date d) return d.toLocalDate();
            if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().toLocalDate();
            return toJava(value, target);
        }
        if (target == LocalDateTime.class) {
            if (value instanceof LocalDateTime dt) return dt;
            if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
            if (value instanceof java.sql.Date d) return d.toLocalDate().atStartOfDay();
            return toJava(value, target);
        }

        if (target.isInstance(value)) return value;
        return datapotter.datahelper.DataHelper_I.convertType(value, target);
    }

    // ---- Java -> sheet cell ----------------------------------------------------

    /**
     * A field value as the cell to write. Never {@code null} — an unset field becomes the empty
     * string, which is how the API clears a cell rather than leaving whatever was there.
     */
    static Object toCell(Object value) {
        return switch (value) {
            case null -> "";
            case LocalDate d -> (double) ChronoUnit.DAYS.between(EPOCH, d);
            case LocalDateTime dt -> ChronoUnit.DAYS.between(EPOCH, dt.toLocalDate())
                    + dt.toLocalTime().toNanoOfDay() / 1_000_000_000d / SECONDS_PER_DAY;
            case Enum<?> e -> e.name();
            case Number n -> n;
            case Boolean b -> b;
            default -> value.toString();
        };
    }
}
