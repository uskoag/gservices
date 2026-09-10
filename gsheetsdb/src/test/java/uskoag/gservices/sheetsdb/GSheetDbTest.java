package uskoag.gservices.sheetsdb;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uskoag.gservices.sheetsdb.testmodel.Treaty;
import datapotter.datahelper.Field;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uskoag.gservices.sheetsdb.Query.query;
import static uskoag.gservices.sheetsdb.TypeDefBuilder.typeDef;
import static uskoag.gservices.sheetsdb.Upsert.upsert;
import static uskoag.gservices.sheetsdb.testmodel.Treaty_IR.$ratified;
import static uskoag.gservices.sheetsdb.testmodel.Treaty_IR.$signedOn;
import static uskoag.gservices.sheetsdb.testmodel.Treaty_IR.$status;
import static uskoag.gservices.sheetsdb.testmodel.Treaty_IR.$title;
import static uskoag.gservices.sheetsdb.testmodel.Treaty_IR.$treatyId;
import static uskoag.gservices.sheetsdb.testmodel.Treaty_IR.$year;

/**
 * Everything above the Sheets API boundary, exercised without a network or a credential.
 *
 * <p>The grid below is shaped like what the API actually returns with {@code UNFORMATTED_VALUE}:
 * numbers as {@link Double} (dates included, as serial numbers), booleans as {@link Boolean}, text as
 * {@link String}, and trailing empty cells simply absent from the row.
 */
class GSheetDbTest {

    /** Every A1 range a flush would send, in order. */
    private static List<String> ranges(GSheetDb db) throws SQLException {
        return db.docs(Treaty.DEF).pendingBlocks().stream()
                .map(SheetDocs.WriteBlock::range).toList();
    }

    /** The first row of cells the flush would send to {@code range}. */
    private static List<Object> cellsAt(GSheetDb db, String range) throws SQLException {
        var blocks = db.docs(Treaty.DEF).pendingBlocks();
        for (var block : blocks) {
            if (block.range().equals(range)) return block.rows().get(0);
        }
        throw new AssertionError("no block for " + range + " in "
                + blocks.stream().map(SheetDocs.WriteBlock::range).toList());
    }

    private static List<Object> row(Object... cells) {
        var out = new ArrayList<Object>();
        for (Object c : cells) out.add(c);
        return out;
    }

    private static List<List<Object>> grid() {
        return new ArrayList<>(List.of(
                row("treatyId", "title", "notes", "year", "ratified", "signedOn", "status"),
                row("UAN-1997-03", "Treaty of Kailasa", "keep-me", 1997d, Boolean.TRUE,
                        SheetDates.toSerial(LocalDate.of(1997, 3, 14)), "RATIFIED"),
                // Deliberately short: the API drops trailing empties, so status is simply absent.
                row("UAN-2001-11", "Accord on Water", "note-2", 2001d, Boolean.FALSE),
                row("UAN-2010-07", "Protocol on Trade", "", 2010d, Boolean.TRUE,
                        SheetDates.toSerial(LocalDate.of(2010, 7, 1)), "DRAFT"),
                row("", "", "", "", "")));       // a spacer row is not a document
    }

    private GSheetDb db;

    @BeforeEach
    void setUp() {
        db = GSheetDb.offline("test-spreadsheet").register(Treaty.DEF);
        db.loadOffline(Map.of("Treaty", grid()));
    }

    @AfterEach
    void tearDown() {
        if (db != null) db.close();
    }

    // ---- loading and typing ----------------------------------------------------

    @Test
    @DisplayName("documents materialise with their declared types, serial dates included")
    void materialises() {
        var all = query(db, Treaty.DEF).list();
        assertEquals(3, all.size(), "the blank spacer row must not become a document");

        Treaty first = all.get(0);
        assertAll(
                () -> assertEquals("UAN-1997-03", first.treatyId()),
                () -> assertEquals("Treaty of Kailasa", first.title()),
                () -> assertEquals(1997, first.year()),
                () -> assertEquals(Boolean.TRUE, first.ratified()),
                () -> assertEquals(LocalDate.of(1997, 3, 14), first.signedOnDate()),
                () -> assertEquals("RATIFIED", first.status()));
    }

    @Test
    @DisplayName("a cell the API omitted reads as null, not as a crash")
    void tolerantOfShortRows() {
        Treaty t = query(db, Treaty.DEF).byKey("UAN-2001-11");
        assertNotNull(t);
        assertNull(t.signedOn());
        assertNull(t.status());
        assertEquals(2001, t.year());
    }

    @Test
    @DisplayName("fields are matched by header name, not by position")
    void headerOrderIsIndependent() {
        // Declared order is treatyId,title,year,... but the sheet has an undeclared `notes` third.
        assertEquals(List.of(), db.missingFields(Treaty.DEF));
        assertEquals("Protocol on Trade", query(db, Treaty.DEF).byKey("UAN-2010-07").title());
        assertEquals(2010, query(db, Treaty.DEF).byKey("UAN-2010-07").year());
    }

    @Test
    @DisplayName("a boolean survives the sheet, the mirror and the way back unchanged")
    void booleanRoundTrip() throws SQLException {
        assertEquals(Boolean.TRUE, query(db, Treaty.DEF).byKey("UAN-1997-03").ratified());
        assertEquals(Boolean.FALSE, query(db, Treaty.DEF).byKey("UAN-2001-11").ratified());
        assertEquals(2, query(db, Treaty.DEF).eq($ratified, true).count());

        Treaty t = query(db, Treaty.DEF).byKey("UAN-1997-03").ratified(false);
        upsert(db, Treaty.DEF).save(t);

        var cells = cellsAt(db, "'Treaty'!E2:E2");         // ratified is column E, and the only change
        assertEquals(Boolean.FALSE, cells.get(0),
                "written back as a real boolean, not the string \"false\" — with RAW that is what "
                + "keeps the cell a checkbox rather than turning it into text");
    }

    // ---- the query DSL ---------------------------------------------------------

    @Test
    @DisplayName("comparison, ordering, limit, count, exists")
    void queries() {
        var recent = query(db, Treaty.DEF).gt($year, 1999).orderByDesc($year).list();
        assertEquals(List.of("UAN-2010-07", "UAN-2001-11"),
                recent.stream().map(Treaty::treatyId).toList());

        assertEquals(1, query(db, Treaty.DEF).gt($year, 1999).limit(1).list().size());
        assertEquals(2L, query(db, Treaty.DEF).gt($year, 1999).count());
        assertTrue(query(db, Treaty.DEF).eq($ratified, true).exists());
        assertFalse(query(db, Treaty.DEF).eq($year, 1066).exists());
        assertEquals("Treaty of Kailasa", query(db, Treaty.DEF).eq($year, 1997).firstOrNull().title());
        assertNull(query(db, Treaty.DEF).eq($year, 1066).firstOrNull());
    }

    @Test
    @DisplayName("skip pages through an ordered result")
    void skipPages() {
        var page = query(db, Treaty.DEF).orderByAsc($year).skip(1).limit(1).list();
        assertEquals(List.of("UAN-2001-11"), page.stream().map(Treaty::treatyId).toList());
    }

    @Test
    @DisplayName("like, ilike, in, between, isNull, isNotNull")
    void moreConditions() {
        assertEquals(1, query(db, Treaty.DEF).like($title, "Treaty%").count());
        assertEquals(0, query(db, Treaty.DEF).like($title, "treaty%").count());
        assertEquals(1, query(db, Treaty.DEF).ilike($title, "treaty%").count());
        assertEquals(2, query(db, Treaty.DEF)
                .in($treatyId, List.of("UAN-1997-03", "UAN-2010-07")).count());
        assertEquals(0, query(db, Treaty.DEF).in($treatyId, List.<String>of()).count(),
                "an empty IN matches nothing");
        assertEquals(2, query(db, Treaty.DEF).between($year, 2000, 2011).count());
        assertEquals(1, query(db, Treaty.DEF).isNull($status).count());
        assertEquals(2, query(db, Treaty.DEF).isNotNull($signedOn).count());

        assertEquals(1, query(db, Treaty.DEF).neq($status, "RATIFIED").count(),
                "SQL semantics: the blank status is excluded, not counted as 'not RATIFIED'");
        assertEquals(2, query(db, Treaty.DEF)
                        .group(g -> g.neq($status, "RATIFIED").or().isNull($status)).count(),
                "and this is how you include it");
    }

    @Test
    @DisplayName("a serial date compares as the number it is")
    void dateConditions() {
        assertEquals(1, query(db, Treaty.DEF)
                .gt($signedOn, SheetDates.toSerial(LocalDate.of(2000, 1, 1))).count());
    }

    @Test
    @DisplayName("grouping expresses (a OR b) AND c, which the ArcadeDB builder could not")
    void grouping() {
        var found = query(db, Treaty.DEF)
                .group(g -> g.eq($year, 1997).or().eq($year, 2001))
                .eq($ratified, true)
                .orderByAsc($year)
                .list();
        assertEquals(List.of("UAN-1997-03"), found.stream().map(Treaty::treatyId).toList(),
                "without the parentheses, SQL precedence would also return UAN-2001-11");

        var negated = query(db, Treaty.DEF)
                .notGroup(g -> g.eq($year, 1997))
                .orderByAsc($year)
                .list();
        assertEquals(List.of("UAN-2001-11", "UAN-2010-07"),
                negated.stream().map(Treaty::treatyId).toList());
    }

    @Test
    @DisplayName("the raw SQL escape hatch binds its parameters")
    void rawWhere() {
        // "Treaty of Kailasa" and "Protocol on Trade" are 17; "Accord on Water" is 15.
        assertEquals(2, query(db, Treaty.DEF).where("LENGTH(\"title\") > ?", 16).count());
        assertEquals(0, query(db, Treaty.DEF).where("LENGTH(\"title\") > ?", 17).count());
    }

    @Test
    @DisplayName("filtering on something that is not a field fails by name, not silently")
    void unknownFieldIsLoud() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> query(db, Treaty.DEF).eq(new Field<Treaty, String>("nosuch", String.class), "x"));
        assertTrue(ex.getMessage().contains("nosuch"), ex.getMessage());
    }

    @Test
    @DisplayName("the condition-only builder handed to group() cannot be executed on its own")
    void groupBuilderIsNotExecutable() {
        query(db, Treaty.DEF).group(inner -> assertThrows(IllegalStateException.class, inner::list));
    }

    // ---- key integrity ---------------------------------------------------------

    @Test
    @DisplayName("a key on two rows is found at load and named")
    void duplicateKeysAreDetected() {
        var dup = grid();
        dup.add(row("UAN-1997-03", "Treaty of Kailasa (copy)", "", 1997d, Boolean.TRUE));

        try (var other = GSheetDb.offline("x").register(Treaty.DEF)) {
            other.loadOffline(Map.of("Treaty", dup));

            var duplicates = other.duplicateKeys(Treaty.DEF);
            assertEquals(1, duplicates.size());
            assertEquals(List.of(2, 6), duplicates.get("UAN-1997-03"),
                    "both sheet rows carrying the key, in order");
        }
    }

    @Test
    @DisplayName("a duplicated key is refused on read and on write, rather than picking one")
    void duplicateKeysAreRefused() {
        var dup = grid();
        dup.add(row("UAN-1997-03", "Treaty of Kailasa (copy)", "", 1997d, Boolean.TRUE));

        try (var other = GSheetDb.offline("x").register(Treaty.DEF)) {
            other.loadOffline(Map.of("Treaty", dup));

            var read = assertThrows(GSheetDbException.class,
                    () -> query(other, Treaty.DEF).byKey("UAN-1997-03"));
            assertTrue(read.getMessage().contains("[2, 6]"), read.getMessage());

            var write = assertThrows(GSheetDbException.class,
                    () -> upsert(other, Treaty.DEF).save(new Treaty().treatyId("UAN-1997-03")));
            assertTrue(write.getMessage().contains("installKeyGuard"), write.getMessage());

            assertEquals("Accord on Water", query(other, Treaty.DEF).byKey("UAN-2001-11").title(),
                    "an untouched key still works — one bad row does not close the sheet");
        }
    }

    @Test
    @DisplayName("blank keys are not duplicates of one another")
    void blankKeysAreNotDuplicates() {
        var withBlanks = grid();
        withBlanks.add(row("", "Untitled one", "", 2020d));
        withBlanks.add(row("", "Untitled two", "", 2021d));

        try (var other = GSheetDb.offline("x").register(Treaty.DEF)) {
            other.loadOffline(Map.of("Treaty", withBlanks));
            assertEquals(Map.of(), other.duplicateKeys(Treaty.DEF));
        }
    }

    // ---- writes ----------------------------------------------------------------

    @Test
    @DisplayName("a save covers the fields it changed, and no others")
    void updateExisting() throws SQLException {
        Treaty t = query(db, Treaty.DEF).byKey("UAN-2001-11");
        t.title("Revised Accord on Water").status("SIGNED");
        upsert(db, Treaty.DEF).save(t);

        assertTrue(db.isDirty());
        // treatyId A, title B, [notes C — not ours], year D, ratified E, signedOn F, status G.
        // Two fields were assigned, so two cells are sent — not the eight the type declares.
        assertEquals(List.of("'Treaty'!B3:B3", "'Treaty'!G3:G3"), ranges(db));

        assertEquals("Revised Accord on Water", cellsAt(db, "'Treaty'!B3:B3").get(0));
        assertEquals("SIGNED", cellsAt(db, "'Treaty'!G3:G3").get(0));
    }

    @Test
    @DisplayName("saving a document nobody edited sends nothing at all")
    void unchangedSaveWritesNothing() throws SQLException {
        upsert(db, Treaty.DEF).save(query(db, Treaty.DEF).byKey("UAN-1997-03"));
        assertEquals(List.of(), ranges(db),
                "read it, save it, change nothing: there is no cell to write, so there is no request");
    }

    @Test
    @DisplayName("a cell holding a formula survives a save of its neighbours, and yields to an edit")
    void doesNotFlattenAFormula() throws SQLException {
        // At this layer a formula is indistinguishable from a literal: the API is read with
        // UNFORMATTED_VALUE, so `title` arrives as whatever the formula computed to. Writing that
        // value back in RAW mode is what would replace the formula with a frozen copy of it.
        upsert(db, Treaty.DEF).save(query(db, Treaty.DEF).byKey("UAN-1997-03").status("SIGNED"));

        for (String range : ranges(db)) {
            assertFalse(range.contains("B"), "column B was not assigned, so it must not be "
                    + "written — on a formula cell that write is destructive: " + range);
        }
        assertEquals(List.of("'Treaty'!G2:G2"), ranges(db));

        // And nothing is forbidden: assign it and it is written like any other field.
        try (var other = GSheetDb.offline("x").register(Treaty.DEF)) {
            other.loadOffline(Map.of("Treaty", grid()));
            upsert(other, Treaty.DEF)
                    .save(query(other, Treaty.DEF).byKey("UAN-1997-03").title("Deliberately renamed"));
            assertEquals(List.of("'Treaty'!B2:B2"), ranges(other));
            assertEquals("Deliberately renamed", cellsAt(other, "'Treaty'!B2:B2").get(0));
        }
    }

    @Test
    @DisplayName("a value that only looks different across representations is not a change")
    void numericRepresentationIsNotAChange() throws SQLException {
        // The API hands back every number as a Double; the mirror hands back the declared type. 1997d
        // and Integer 1997 are one value, and treating them as two would write the cell needlessly.
        Treaty t = query(db, Treaty.DEF).byKey("UAN-1997-03");
        upsert(db, Treaty.DEF).save(t.year(1997).ratified(true).status("RATIFIED"));

        assertEquals(List.of(), ranges(db),
                "year re-set to the same number, ratified to the same boolean, status to the same "
                + "string — nothing moved, so nothing is sent");
    }

    @Test
    @DisplayName("a column no field declares is never in the payload at all")
    void neverWritesAnUndeclaredColumn() throws SQLException {
        upsert(db, Treaty.DEF).save(query(db, Treaty.DEF).byKey("UAN-1997-03").title("Renamed"));

        // Column C holds "keep-me". The old design read it at load, carried it through and wrote it
        // back — which reverted whatever a human had typed there in the meantime. Now it is simply
        // not addressed, so their edit cannot be lost to a save however the timing falls.
        for (String range : ranges(db)) {
            assertFalse(range.contains("C"), "no range may cover column C: " + range);
        }
        assertEquals(List.of("'Treaty'!B2:B2"), ranges(db));
    }

    @Test
    @DisplayName("dates are written back as serial numbers so they stay real dates")
    void writesDatesAsSerials() throws SQLException {
        Treaty t = query(db, Treaty.DEF).byKey("UAN-2001-11").signedOnDate(LocalDate.of(2001, 11, 5));
        upsert(db, Treaty.DEF).save(t);

        var cells = cellsAt(db, "'Treaty'!F3:F3");         // signedOn is column F, and the only change
        assertEquals(SheetDates.toSerial(LocalDate.of(2001, 11, 5)), (Double) cells.get(0), 0.0001);
    }

    @Test
    @DisplayName("an unknown key appends after the last document")
    void insertNew() throws SQLException {
        upsert(db, Treaty.DEF).save(new Treaty().treatyId("UAN-2026-01").title("New Accord")
                .year(2026).ratified(false).status("DRAFT"));

        assertEquals(4, query(db, Treaty.DEF).list().size());
        assertEquals(List.of("'Treaty'!A5:B5", "'Treaty'!D5:G5"), ranges(db),
                "the last real document is on row 4, so the next one goes on 5 — the blank spacer "
                + "row is space, not a landmark to append below");
        assertEquals("New Accord", cellsAt(db, "'Treaty'!A5:B5").get(1));
        assertEquals("", cellsAt(db, "'Treaty'!D5:G5").get(2), "an unset date clears the cell");
    }

    @Test
    @DisplayName("saving the same new key twice updates it rather than appending twice")
    void saveIsIdempotentOnKey() throws SQLException {
        upsert(db, Treaty.DEF)
                .save(new Treaty().treatyId("UAN-2026-01").title("First"))
                .save(new Treaty().treatyId("UAN-2026-01").title("Second"));

        assertEquals(4, query(db, Treaty.DEF).list().size());
        assertEquals(List.of("'Treaty'!A5:B5", "'Treaty'!D5:G5"), ranges(db));
        assertEquals("Second", cellsAt(db, "'Treaty'!A5:B5").get(1));
    }

    @Test
    @DisplayName("consecutive rows coalesce, a gap starts another rectangle")
    void coalescesContiguousRows() throws SQLException {
        var docs = upsert(db, Treaty.DEF);
        docs.save(query(db, Treaty.DEF).byKey("UAN-1997-03").title("a"));   // row 2
        docs.save(query(db, Treaty.DEF).byKey("UAN-2001-11").title("b"));   // row 3
        docs.save(query(db, Treaty.DEF).byKey("UAN-2010-07").title("c"));   // row 4
        assertEquals(List.of("'Treaty'!B2:B4"), ranges(db),
                "rows 2..4 are one run, and all three changed the same one column");
        assertEquals(3, db.docs(Treaty.DEF).pendingBlocks().get(0).rows().size());

        try (var fresh = GSheetDb.offline("x").register(Treaty.DEF)) {
            fresh.loadOffline(Map.of("Treaty", grid()));
            upsert(fresh, Treaty.DEF)
                    .save(query(fresh, Treaty.DEF).byKey("UAN-1997-03").title("a"))   // row 2
                    .save(query(fresh, Treaty.DEF).byKey("UAN-2010-07").title("c"));  // row 4

            assertEquals(List.of("'Treaty'!B2:B2", "'Treaty'!B4:B4"), ranges(fresh),
                    "rows 2 and 4 are not contiguous");
        }
    }

    @Test
    @DisplayName("rows that changed different columns do not share a rectangle")
    void doesNotCoalesceAcrossDifferentShapes() throws SQLException {
        upsert(db, Treaty.DEF)
                .save(query(db, Treaty.DEF).byKey("UAN-1997-03").title("a"))      // row 2, column B
                .save(query(db, Treaty.DEF).byKey("UAN-2001-11").status("X"));    // row 3, column G

        assertEquals(List.of("'Treaty'!B2:B2", "'Treaty'!G3:G3"), ranges(db),
                "one rectangle over both rows would write row 3's title and row 2's status, "
                + "neither of which anybody touched");
    }

    @Test
    @DisplayName("delete removes it from the mirror and schedules the row, highest first")
    void deletes() {
        upsert(db, Treaty.DEF)
                .delete(query(db, Treaty.DEF).byKey("UAN-1997-03"))            // row 2
                .delete(query(db, Treaty.DEF).byKey("UAN-2010-07"));           // row 4

        assertEquals(1, query(db, Treaty.DEF).list().size());
        assertTrue(db.docs(Treaty.DEF).hasDeletions());
        assertEquals(List.of(4, 2), db.docs(Treaty.DEF).deletionsDescending(),
                "deletions must be applied bottom-up or the indices shift underneath them");
    }

    @Test
    @DisplayName("deleting something that is not there is not an error")
    void deleteAbsent() {
        upsert(db, Treaty.DEF).delete(new Treaty().treatyId("nope"));
        assertFalse(db.isDirty());
        assertEquals(3, query(db, Treaty.DEF).list().size());
    }

    // ---- the refusals ----------------------------------------------------------

    @Test
    @DisplayName("a type with no declared key is read-only")
    void noKeyIsReadOnly() {
        var keyless = typeDef(Treaty.class, Treaty::new).__();
        try (var other = GSheetDb.offline("x").register(keyless)) {
            other.loadOffline(Map.of("Treaty", grid()));

            assertEquals(3, query(other, keyless).list().size(), "reading is fine");
            var ex = assertThrows(UnsupportedOperationException.class,
                    () -> upsert(other, keyless).save(new Treaty().treatyId("z")));
            assertTrue(ex.getMessage().contains("read-only"), ex.getMessage());
        }
    }

    @Test
    @DisplayName("a declared field the sheet lacks reads as null but refuses to be written")
    void missingFieldBlocksWrites() {
        var narrow = new ArrayList<List<Object>>(List.of(
                row("treatyId", "title", "year"),
                row("UAN-1997-03", "Treaty of Kailasa", 1997d)));

        try (var other = GSheetDb.offline("x").register(Treaty.DEF)) {
            other.loadOffline(Map.of("Treaty", narrow));

            assertEquals(List.of("ratified", "signedOn", "status"), other.missingFields(Treaty.DEF));
            assertNull(query(other, Treaty.DEF).byKey("UAN-1997-03").status(),
                    "an absent field reads as null");

            var ex = assertThrows(IllegalStateException.class,
                    () -> upsert(other, Treaty.DEF).save(query(other, Treaty.DEF).byKey("UAN-1997-03")));
            assertTrue(ex.getMessage().contains("status"), ex.getMessage());
        }
    }

    @Test
    @DisplayName("a document whose key is empty cannot be placed")
    void emptyKeyRefused() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> upsert(db, Treaty.DEF).save(new Treaty().title("nameless")));
        assertTrue(ex.getMessage().contains("treatyId"), ex.getMessage());
    }

    @Test
    @DisplayName("refresh refuses to silently drop pending writes")
    void refreshGuardsPendingWrites() {
        upsert(db, Treaty.DEF).save(query(db, Treaty.DEF).byKey("UAN-1997-03").title("changed"));
        var ex = assertThrows(IllegalStateException.class, () -> db.refresh());
        assertTrue(ex.getMessage().contains("flush()"), ex.getMessage());
    }

    @Test
    @DisplayName("registering after load is refused")
    void lateRegistrationRefused() {
        assertThrows(IllegalStateException.class, () -> db.register(Treaty.DEF));
    }

    @Test
    @DisplayName("querying an unregistered type is refused by name")
    void unregisteredTypeRefused() {
        var other = typeDef(Treaty.class, Treaty::new).key($treatyId).__();
        assertThrows(IllegalArgumentException.class, () -> query(db, other).list());
    }

    @Test
    @DisplayName("a def with no factory cannot be built")
    void factoryRequired() {
        var ex = assertThrows(IllegalStateException.class, () -> typeDef(Treaty.class).__());
        assertTrue(ex.getMessage().contains("factory"), ex.getMessage());
    }

    @Test
    @DisplayName("a key naming something that is not a field is caught at build time")
    void unknownKeyRefused() {
        var ex = assertThrows(IllegalStateException.class,
                () -> typeDef(Treaty.class, Treaty::new).key("nosuch").__());
        assertTrue(ex.getMessage().contains("nosuch"), ex.getMessage());
    }

    @Test
    @DisplayName("a save writes only as far as the last declared field, never over trailing columns")
    void doesNotWriteBeyondTheDeclaredFields() throws SQLException {
        // The duplicate-report column installKeyGuard adds lives out here, as would any note column.
        var wide = grid();
        wide.get(0).add("treatyId__duplicates");
        wide.get(1).add("rows 2, 9");

        try (var other = GSheetDb.offline("x").register(Treaty.DEF)) {
            other.loadOffline(Map.of("Treaty", wide));

            upsert(other, Treaty.DEF).save(query(other, Treaty.DEF).byKey("UAN-1997-03").title("x"));

            for (var block : other.docs(Treaty.DEF).pendingBlocks()) {
                assertFalse(block.range().contains("H"),
                        "column H holds the formula this library wrote; touching it on every save "
                        + "would be a self-inflicted wound: " + block.range());
            }
            assertEquals(List.of("'Treaty'!B2:B2"), ranges(other));
        }
    }

    @Test
    @DisplayName("a whole-column array formula does not push new documents to the bottom of the sheet")
    void appendsAfterTheLastRealDocument() throws SQLException {
        // What the duplicate-report column actually looks like once it is on the sheet: it evaluates
        // to "" for every row down to the sheet's last, and the API hands those back as real cells.
        var withFormula = grid();
        withFormula.get(0).add("treatyId__duplicates");
        for (int i = 1; i < withFormula.size(); i++) withFormula.get(i).add("");
        for (int r = 0; r < 40; r++) withFormula.add(row("", "", "", "", "", "", "", ""));

        try (var other = GSheetDb.offline("x").register(Treaty.DEF)) {
            other.loadOffline(Map.of("Treaty", withFormula));

            assertEquals(3, query(other, Treaty.DEF).list().size(), "still three documents");

            upsert(other, Treaty.DEF).save(new Treaty().treatyId("UAN-2026-01").title("New"));
            assertEquals(List.of("'Treaty'!A5:B5", "'Treaty'!D5:G5"), ranges(other),
                    "row 5, straight after the last real document — not row 46 below the blanks");
        }
    }

    // ---- re-addressing, the thing that runs immediately before every write -----

    @Test
    @DisplayName("a column inserted since load moves the write, it does not corrupt it")
    void reheaderFollowsAMovedColumn() throws SQLException {
        upsert(db, Treaty.DEF).save(query(db, Treaty.DEF).byKey("UAN-2001-11").title("Revised"));
        assertEquals(List.of("'Treaty'!B3:B3"), ranges(db));

        // Somebody inserts a column at B while we hold title=B, year=D, ... Everything shifts right.
        var moved = List.<Object>of("treatyId", "inserted", "title", "notes",
                "year", "ratified", "signedOn", "status");
        db.docs(Treaty.DEF).reheader(moved);
        db.docs(Treaty.DEF).relocate(keyColumn("UAN-1997-03", "UAN-2001-11", "UAN-2010-07"));

        assertEquals(List.of("'Treaty'!C3:C3"), ranges(db),
                "addressed by field name against the header as it is now, so title follows to C — "
                + "while the comparison that decided it changed still reads the loaded grid at B");
        assertEquals("Revised", cellsAt(db, "'Treaty'!C3:C3").get(0));
    }

    @Test
    @DisplayName("a row inserted since load moves the document, found again by its key")
    void relocateFollowsAMovedRow() throws SQLException {
        upsert(db, Treaty.DEF).save(query(db, Treaty.DEF).byKey("UAN-2010-07").title("Revised"));
        assertEquals(List.of("'Treaty'!B4:B4"), ranges(db));

        // Somebody inserts a row above it. UAN-2010-07 is now on row 5, not row 4.
        db.docs(Treaty.DEF).reheader(grid().get(0));
        db.docs(Treaty.DEF).relocate(
                keyColumn("UAN-1997-03", "UAN-2001-11", "brand-new", "UAN-2010-07"));

        assertEquals(List.of("'Treaty'!B5:B5"), ranges(db),
                "row 5 now — the write follows the document, not the row number it used to have");
        assertEquals("Revised", cellsAt(db, "'Treaty'!B5:B5").get(0));
    }

    @Test
    @DisplayName("a document deleted from under us aborts the whole flush, naming it")
    void relocateRefusesAVanishedDocument() {
        upsert(db, Treaty.DEF).save(query(db, Treaty.DEF).byKey("UAN-2001-11").title("Revised"));

        db.docs(Treaty.DEF).reheader(grid().get(0));
        var ex = assertThrows(GSheetDbException.class, () -> db.docs(Treaty.DEF)
                .relocate(keyColumn("UAN-1997-03", "UAN-2010-07")));      // 2001-11 is gone

        assertTrue(ex.getMessage().contains("UAN-2001-11"), ex.getMessage());
        assertTrue(ex.getMessage().contains("Nothing has been written"), ex.getMessage());
    }

    @Test
    @DisplayName("a header field that disappeared aborts before anything is written")
    void reheaderRefusesAMissingField() {
        upsert(db, Treaty.DEF).save(query(db, Treaty.DEF).byKey("UAN-2001-11").title("Revised"));

        var ex = assertThrows(GSheetDbException.class, () -> db.docs(Treaty.DEF)
                .reheader(List.of("treatyId", "title", "notes", "year")));

        assertTrue(ex.getMessage().contains("ratified"), ex.getMessage());
        assertTrue(ex.getMessage().contains("Nothing has been written"), ex.getMessage());
    }

    @Test
    @DisplayName("a new document appends after whatever is there now, not where we guessed")
    void relocatePlacesAnAppendAfterConcurrentGrowth() throws SQLException {
        upsert(db, Treaty.DEF).save(new Treaty().treatyId("UAN-2026-01").title("New"));
        assertEquals(List.of("'Treaty'!A5:B5", "'Treaty'!D5:G5"), ranges(db));

        // Two more documents arrived while we worked; ours belongs after them.
        db.docs(Treaty.DEF).reheader(grid().get(0));
        db.docs(Treaty.DEF).relocate(keyColumn("UAN-1997-03", "UAN-2001-11", "UAN-2010-07",
                "someone-else-1", "someone-else-2"));

        assertEquals(List.of("'Treaty'!A7:B7", "'Treaty'!D7:G7"), ranges(db),
                "row 7, after the two that appeared — not row 5, on top of one of them");
    }

    @Test
    @DisplayName("a deletion whose row somebody already removed is dropped, not mis-aimed")
    void relocateDropsAnAlreadyDeletedRow() {
        upsert(db, Treaty.DEF).delete(query(db, Treaty.DEF).byKey("UAN-2001-11"));
        assertTrue(db.docs(Treaty.DEF).hasDeletions());

        db.docs(Treaty.DEF).reheader(grid().get(0));
        db.docs(Treaty.DEF).relocate(keyColumn("UAN-1997-03", "UAN-2010-07"));

        assertFalse(db.docs(Treaty.DEF).hasDeletions(),
                "somebody deleted it first; deleting whatever now sits on that row would be the bug");
    }

    /** The key column as the API returns it: one cell per row, from the first data row down. */
    private static List<List<Object>> keyColumn(String... keys) {
        var out = new ArrayList<List<Object>>();
        for (String k : keys) out.add(List.of(k));
        return out;
    }

    @Test
    @DisplayName("several types register in one call")
    void registerVarargs() {
        var second = typeDef(Treaty.class, Treaty::new).key($treatyId).__();
        try (var other = GSheetDb.offline("x").register(Treaty.DEF, second)) {
            other.loadOffline(Map.of("Treaty", grid()));
            assertEquals(2, other.registered().size());
            assertEquals(3, query(other, second).list().size());
        }
    }

    @Test
    @DisplayName("registering nothing is refused")
    void registerNothingRefused() {
        try (var other = GSheetDb.offline("x")) {
            assertThrows(IllegalArgumentException.class, other::register);
        }
    }

    @Test
    @DisplayName("the sheet is named after the type, and the fields after themselves")
    void defDerivesEverything() {
        assertEquals("Treaty", Treaty.DEF.name());
        assertEquals(List.of("treatyId", "title", "year", "ratified", "signedOn", "status"),
                Treaty.DEF.fields());
        assertEquals(Integer.class, Treaty.DEF.fieldType("year"));
        assertEquals("treatyId", Treaty.DEF.keyField());
        assertEquals(1, Treaty.DEF.headerRow());
    }

    // ---- A1 --------------------------------------------------------------------

    @Test
    @DisplayName("column letters and quoted sheet names")
    void a1() {
        assertAll(
                () -> assertEquals("A", A1.col(1)),
                () -> assertEquals("Z", A1.col(26)),
                () -> assertEquals("AA", A1.col(27)),
                () -> assertEquals("AB", A1.col(28)),
                () -> assertEquals("BA", A1.col(53)),
                () -> assertEquals("'Treaties (2026)'", A1.sheet("Treaties (2026)")),
                () -> assertEquals("'Bob''s data'", A1.sheet("Bob's data")),
                () -> assertEquals("'T'!A2:C4", A1.rows("T", 2, 4, 3)));
    }
}
