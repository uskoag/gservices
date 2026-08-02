package uskoag.gservices.sheetsdb;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The cell-level conversions on their own.
 *
 * <p>Worth testing directly rather than only through a model, because the temporal and enum branches
 * are not reachable from a {@code @Data} class at all — the processor's field whitelist rejects those
 * types. They stay supported here for hand-written {@code DataHelper_I} implementations, and so that
 * nothing needs rewriting if that whitelist grows. Untested support would be a promise, not a feature.
 */
class SheetValuesTest {

    private enum Status { DRAFT, SIGNED }

    @Test
    @DisplayName("numbers arrive as Double and land as the declared width")
    void numbers() {
        assertAll(
                () -> assertEquals(1997, SheetValues.toJava(1997d, Integer.class)),
                () -> assertEquals(1997L, SheetValues.toJava(1997d, Long.class)),
                () -> assertEquals(2.5d, SheetValues.toJava(2.5d, Double.class)),
                () -> assertEquals(2.5f, SheetValues.toJava(2.5d, Float.class)),
                () -> assertEquals(new BigDecimal("2.5"), SheetValues.toJava("2.5", BigDecimal.class)));
    }

    @Test
    @DisplayName("an integral number read as text does not pick up a decimal point")
    void integralNumbersStringifyCleanly() {
        // The one that bites: an id typed as 1234 arrives as 1234.0 and would stop matching its key.
        assertEquals("1234", SheetValues.toJava(1234d, String.class));
        assertEquals("2.5", SheetValues.toJava(2.5d, String.class));
    }

    @Test
    @DisplayName("booleans, however the sheet spells them")
    void booleans() {
        assertAll(
                () -> assertEquals(Boolean.TRUE, SheetValues.toJava(Boolean.TRUE, Boolean.class)),
                () -> assertEquals(Boolean.TRUE, SheetValues.toJava("TRUE", Boolean.class)),
                () -> assertEquals(Boolean.TRUE, SheetValues.toJava("yes", Boolean.class)),
                () -> assertEquals(Boolean.FALSE, SheetValues.toJava("no", Boolean.class)),
                () -> assertEquals(Boolean.FALSE, SheetValues.toJava(0d, Boolean.class)));
    }

    @Test
    @DisplayName("serial numbers convert to temporals and back")
    void temporals() {
        double serial = SheetDates.toSerial(LocalDate.of(1997, 3, 14));
        assertEquals(LocalDate.of(1997, 3, 14), SheetValues.toJava(serial, LocalDate.class));
        assertEquals(serial, (Double) SheetValues.toCell(LocalDate.of(1997, 3, 14)), 0.0001);

        var noon = LocalDateTime.of(2010, 7, 1, 12, 0);
        double dtSerial = SheetDates.toSerial(noon);
        assertEquals(noon, SheetValues.toJava(dtSerial, LocalDateTime.class));
        assertEquals(dtSerial, (Double) SheetValues.toCell(noon), 0.0001);

        // An ISO string is accepted too, for a sheet whose date column is really text.
        assertEquals(LocalDate.of(1997, 3, 14), SheetValues.toJava("1997-03-14", LocalDate.class));
    }

    @Test
    @DisplayName("enums round-trip through their name")
    void enums() {
        assertEquals(Status.SIGNED, SheetValues.toJava("SIGNED", Status.class));
        assertEquals("SIGNED", SheetValues.toCell(Status.SIGNED));
        assertNull(SheetValues.toJava("", Status.class));
        assertNull(SheetValues.toJava("NOT_A_STATUS", Status.class), "a bad cell is null, not a crash");
    }

    @Test
    @DisplayName("an empty or unreadable cell is null rather than an exception")
    void tolerant() {
        assertAll(
                () -> assertNull(SheetValues.toJava(null, String.class)),
                () -> assertNull(SheetValues.toJava("", Integer.class)),
                () -> assertNull(SheetValues.toJava("   ", Integer.class)),
                () -> assertNull(SheetValues.toJava("not a number", Integer.class)),
                () -> assertEquals("", SheetValues.toJava("", String.class),
                        "an empty string field stays an empty string"));
    }

    @Test
    @DisplayName("null becomes an empty cell, which is what clears it")
    void nullClears() {
        assertEquals("", SheetValues.toCell(null));
    }

    @Test
    @DisplayName("H2 column types for the declared field types")
    void h2Types() {
        assertAll(
                () -> assertEquals("VARCHAR", SheetValues.h2Type(String.class)),
                () -> assertEquals("INT", SheetValues.h2Type(Integer.class)),
                () -> assertEquals("BIGINT", SheetValues.h2Type(Long.class)),
                () -> assertEquals("DOUBLE PRECISION", SheetValues.h2Type(Double.class)),
                () -> assertEquals("BOOLEAN", SheetValues.h2Type(Boolean.class)),
                () -> assertEquals("DATE", SheetValues.h2Type(LocalDate.class)),
                () -> assertEquals("TIMESTAMP", SheetValues.h2Type(LocalDateTime.class)),
                () -> assertEquals("VARCHAR", SheetValues.h2Type(Status.class)));
    }
}
