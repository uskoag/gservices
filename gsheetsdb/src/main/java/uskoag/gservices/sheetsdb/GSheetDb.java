package uskoag.gservices.sheetsdb;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;
import com.google.api.services.sheets.v4.model.DeleteDimensionRequest;
import com.google.api.services.sheets.v4.model.DimensionRange;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.ValueRange;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import xyz.jphil.datahelper.DataHelper_I;

/**
 * A Google spreadsheet, opened as a small typed document store.
 *
 * <pre>
 * import static uskoag.gservices.sheetsdb.TypeDefBuilder.typeDef;
 * import static uskoag.gservices.sheetsdb.Query.query;
 * import static uskoag.gservices.sheetsdb.Upsert.upsert;
 * import static com.example.model.Treaty_IR.*;           // $treatyId, $title, $year
 *
 * Sheets api = SheetsService.sheets(access, "uan-treaties");
 *
 * try (var db = GSheetDb.open(api, spreadsheetId).register(Treaty.DEF).load()) {
 *
 *     List&lt;Treaty&gt; recent = query(db, Treaty.DEF).gt($year, 2000).orderByDesc($year).list();
 *
 *     Treaty t = query(db, Treaty.DEF).byKey("UAN-1997-03");
 *     t.title("Revised title");
 *     upsert(db, Treaty.DEF).save(t);
 *
 *     db.flush();                                        // one batched write, here and nowhere else
 * }
 * </pre>
 *
 * <h3>This is an ORM, and it leaves out the parts that made ORMs a byword</h3>
 *
 * <p>No lazy loading and therefore no N+1: {@link #load()} reads everything registered, in two API
 * calls total, and after that nothing in the library can decide to fetch. No dirty-checking by proxy:
 * a document is a plain object and a save is a save. No detached-entity state machine: there is one
 * session, it is open or it is closed. No string queries: fields are {@code $symbols} the compiler
 * checks. No reflection anywhere, so it holds up under GraalVM native. And no name mapping — the
 * sheet is named after the type and a field is named after itself, so the person editing the
 * spreadsheet is reading the class.
 *
 * <p>What is left is the part of an ORM that was always worth having: typed objects instead of
 * strings and casts.
 *
 * <h3>The mirror, and when Google is touched</h3>
 *
 * <p>{@link #load()} reads every registered sheet in <b>two</b> API calls — one for the workbook
 * metadata, one {@code batchGet} for all the sheets — and mirrors them into an in-process H2
 * database. Every query after that is local: no round-trip, no quota, no rate limit. Writes
 * accumulate and reach Google only on {@link #flush()}, batched.
 *
 * <p>The consequence, stated plainly: <b>the mirror does not notice edits made in the browser after
 * it was loaded.</b> Call {@link #refresh()} when that matters.
 *
 * <h3>Credentials are not this library's business</h3>
 *
 * <p>It takes a configured {@link Sheets} and never asks where it came from. gservices libraries stay
 * credential-free; getting one through the wallet belongs to the application.
 * {@code uskoag.gservices.SheetsService} is the usual route.
 *
 * <h3>Not thread-safe</h3>
 *
 * <p>One instance, one thread. It holds a single JDBC connection and mutable per-type write state.
 */
public final class GSheetDb implements AutoCloseable {

    private static final AtomicLong INSTANCE = new AtomicLong();

    private final Sheets sheets;
    private final String spreadsheetId;
    private final Connection conn;

    /** Registration order matters: it is how sheets line up with the batchGet response. */
    private final Map<TypeDef<?>, SheetDocs<?>> types = new LinkedHashMap<>();

    private boolean loaded;
    private boolean dirty;

    private GSheetDb(Sheets sheets, String spreadsheetId, Connection conn) {
        this.sheets = sheets;
        this.spreadsheetId = spreadsheetId;
        this.conn = conn;
    }

    /**
     * Open a session against one spreadsheet. Nothing is read until {@link #load()}.
     *
     * @param sheets        a configured Sheets client
     * @param spreadsheetId the spreadsheet id (the long token in its URL)
     */
    public static GSheetDb open(Sheets sheets, String spreadsheetId) {
        if (sheets == null) throw new IllegalArgumentException("sheets client is required");
        if (spreadsheetId == null || spreadsheetId.isBlank()) {
            throw new IllegalArgumentException("spreadsheetId is required");
        }
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new GSheetDbException("H2 driver not found on the classpath", e);
        }
        try {
            // Named per instance so two sessions in one JVM cannot see each other's mirrors.
            var url = "jdbc:h2:mem:gsheetsdb_" + INSTANCE.incrementAndGet();
            return new GSheetDb(sheets, spreadsheetId, DriverManager.getConnection(url));
        } catch (SQLException e) {
            throw new GSheetDbException("could not start the in-process H2 mirror", e);
        }
    }

    /**
     * Declare the document types this session will use. Register everything before {@link #load()}.
     *
     * <pre>
     * GSheetDb.open(api, id).register(Collections.DEF, Documents.DEF).load()
     * </pre>
     */
    public GSheetDb register(TypeDef<?>... defs) {
        if (defs == null || defs.length == 0) {
            throw new IllegalArgumentException("at least one type definition is required");
        }
        for (TypeDef<?> def : defs) {
            if (def == null) throw new IllegalArgumentException("def is required");
            if (loaded) {
                throw new IllegalStateException("register(" + def.name() + ") after load(). Register "
                        + "every type first, then load once — loading is what issues the batched "
                        + "read, and a late registration would need another.");
            }
            registerOne(def);
        }
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerOne(TypeDef<?> def) {
        types.put(def, new SheetDocs(this, def, conn));
    }

    /** Read every registered sheet into the mirror. Two API calls, whatever the number of types. */
    public GSheetDb load() {
        if (types.isEmpty()) {
            throw new IllegalStateException("nothing registered — call register(...) before load()");
        }
        try {
            var idByTitle = new LinkedHashMap<String, Integer>();
            var meta = sheets.spreadsheets().get(spreadsheetId).setIncludeGridData(false).execute();
            if (meta.getSheets() != null) {
                for (var s : meta.getSheets()) {
                    idByTitle.put(s.getProperties().getTitle(), s.getProperties().getSheetId());
                }
            }

            var ranges = new ArrayList<String>(types.size());
            for (TypeDef<?> def : types.keySet()) {
                if (!idByTitle.containsKey(def.name())) {
                    throw new GSheetDbException("The spreadsheet has no sheet named '" + def.name()
                            + "'. A type is stored in the sheet named after it, so either rename the "
                            + "sheet or rename the class. It has: " + idByTitle.keySet() + ".");
                }
                ranges.add(A1.wholeSheet(def.name()));
            }

            var response = sheets.spreadsheets().values().batchGet(spreadsheetId)
                    .setRanges(ranges)
                    // Non-negotiable. With the default FORMATTED_VALUE every number arrives as the
                    // string a human sees ("1,234", "TRUE") and every typed field fills with junk.
                    .setValueRenderOption("UNFORMATTED_VALUE")
                    .execute();

            var valueRanges = response.getValueRanges();
            int i = 0;
            for (var entry : types.entrySet()) {                 // same order as `ranges` above
                var vr = valueRanges != null && i < valueRanges.size() ? valueRanges.get(i) : null;
                entry.getValue().load(idByTitle.get(entry.getKey().name()),
                        vr == null ? null : vr.getValues());
                i++;
            }

            loaded = true;
            dirty = false;
            return this;
        } catch (IOException e) {
            throw new GSheetDbException("could not read spreadsheet " + spreadsheetId, e);
        } catch (SQLException e) {
            throw new GSheetDbException("could not build the H2 mirror", e);
        }
    }

    /**
     * Re-read everything, discarding the mirror.
     *
     * <p>Refuses while writes are pending rather than silently dropping them — losing an edit to a
     * refresh is the kind of thing nobody notices until the data is already wrong.
     */
    public GSheetDb refresh() {
        if (dirty) {
            throw new IllegalStateException("refresh() with unsaved changes pending. Call flush() "
                    + "first to keep them, or discard() to abandon them.");
        }
        return load();
    }

    /** Throw away pending writes and re-read. */
    public GSheetDb discard() {
        for (SheetDocs<?> d : types.values()) d.clearPending();
        dirty = false;
        return load();
    }

    /** Whether anything is waiting to be written. */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Write everything pending, in one batched update.
     *
     * <p>If any document was deleted, the sheets are re-read afterwards: deleting shifts every row
     * below it and the held row numbers would otherwise be wrong. One extra API call, and only on a
     * flush that actually carried a deletion.
     */
    public void flush() {
        if (!dirty) return;
        requireLoaded();

        try {
            boolean remapped = readdress();

            var data = new ArrayList<ValueRange>();
            for (SheetDocs<?> d : types.values()) {
                for (SheetDocs.WriteBlock block : d.pendingBlocks()) {
                    data.add(new ValueRange().setRange(block.range()).setValues(block.rows()));
                }
            }
            if (!data.isEmpty()) {
                sheets.spreadsheets().values().batchUpdate(spreadsheetId,
                        new BatchUpdateValuesRequest()
                                // RAW, not USER_ENTERED: a title beginning with '=' is a title, not a
                                // formula, and a code like "+44" is not a number.
                                .setValueInputOption("RAW")
                                .setData(data)).execute();
            }

            var deletions = new ArrayList<Request>();
            for (SheetDocs<?> d : types.values()) {
                if (!d.hasDeletions()) continue;
                for (int row : d.deletionsDescending()) {         // highest first, or indices shift
                    deletions.add(new Request().setDeleteDimension(new DeleteDimensionRequest()
                            .setRange(new DimensionRange()
                                    .setSheetId(d.sheetId())
                                    .setDimension("ROWS")
                                    .setStartIndex(row - 1)        // API is 0-based, half-open
                                    .setEndIndex(row))));
                }
            }
            boolean deleted = !deletions.isEmpty();
            if (deleted) {
                sheets.spreadsheets().batchUpdate(spreadsheetId,
                        new BatchUpdateSpreadsheetRequest().setRequests(deletions)).execute();
            }

            for (SheetDocs<?> d : types.values()) d.clearPending();
            dirty = false;

            // A deletion shifts every row below it; a re-addressing means the mirror's own row
            // numbers no longer match the sheet. Either way what is held is now stale, and the
            // cheapest correct answer is to read it again.
            if (deleted || remapped) load();
        } catch (IOException e) {
            throw new GSheetDbException("could not write to spreadsheet " + spreadsheetId, e);
        } catch (SQLException e) {
            throw new GSheetDbException("could not read pending changes out of the H2 mirror", e);
        }
    }

    /**
     * Re-resolve where everything pending actually lives, against the sheet as it is right now.
     *
     * <p>Two small reads, immediately before the write: the header row of each type with pending
     * changes, then its key column. Columns are re-bound by field <em>name</em> and documents are
     * re-located by <em>key</em>, so a column or row inserted by somebody else since {@link #load()}
     * is a non-event instead of a corruption.
     *
     * <p>This is the alternative to locking, and it is better than one here: a lock only binds
     * whoever agrees to read it, and the person inserting a column is in a browser and never will.
     * Verification at the moment of use needs no cooperation from anyone.
     *
     * @return whether anything moved, in which case the mirror needs re-reading afterwards
     */
    private boolean readdress() throws IOException {
        var pending = new ArrayList<SheetDocs<?>>();
        for (SheetDocs<?> d : types.values()) {
            if (d.isDirty()) pending.add(d);
        }
        if (pending.isEmpty()) return false;

        var headerRanges = new ArrayList<String>(pending.size());
        for (SheetDocs<?> d : pending) {
            headerRanges.add(A1.wholeRow(d.def().name(), d.def().headerRow()));
        }
        var headers = sheets.spreadsheets().values().batchGet(spreadsheetId)
                .setRanges(headerRanges)
                .setValueRenderOption("UNFORMATTED_VALUE")
                .execute()
                .getValueRanges();

        var keyRanges = new ArrayList<String>(pending.size());
        for (int i = 0; i < pending.size(); i++) {
            var d = pending.get(i);
            var rows = headers != null && i < headers.size() ? headers.get(i).getValues() : null;
            int keyColumn = d.reheader(rows == null || rows.isEmpty() ? List.of() : rows.get(0));
            keyRanges.add(A1.column(d.def().name(), keyColumn, d.def().headerRow() + 1));
        }
        var keyColumns = sheets.spreadsheets().values().batchGet(spreadsheetId)
                .setRanges(keyRanges)
                .setValueRenderOption("UNFORMATTED_VALUE")
                .execute()
                .getValueRanges();

        boolean remapped = false;
        for (int i = 0; i < pending.size(); i++) {
            var d = pending.get(i);
            var rows = keyColumns != null && i < keyColumns.size() ? keyColumns.get(i).getValues() : null;
            d.relocate(rows);
            remapped |= d.wasRemapped();
        }
        return remapped;
    }

    // ---- integrity -------------------------------------------------------------

    /**
     * Declared fields this type's sheet has no header for. Empty is the normal case.
     *
     * <p>They read as {@code null}, which lets a model run against a sheet that has not grown the
     * field yet, but saving is refused while any are missing.
     */
    public List<String> missingFields(TypeDef<?> def) {
        return docsOf(def).missingFields();
    }

    /**
     * Key values the sheet carries on more than one row, mapped to those rows. Empty is the normal
     * case, and anything else is corruption to be fixed in the sheet.
     *
     * <p>Touching one of these keys throws rather than picking a row. Report this after
     * {@link #load()} — it is the one integrity check a spreadsheet cannot make for itself.
     */
    public Map<Object, List<Integer>> duplicateKeys(TypeDef<?> def) {
        return docsOf(def).duplicateKeys();
    }

    /**
     * Everything this library can do <b>to</b> a sheet rather than with its contents: the duplicate
     * guard, freezing, header styling. See {@link SheetSetup}.
     *
     * <pre>
     * db.setup(Documents.DEF).keyGuard().freezeHeader().headerStyle().apply();
     * </pre>
     */
    public SheetSetup setup(TypeDef<?> def) {
        return new SheetSetup(this, def, docsOf(def));
    }

    /**
     * The sensible default set: duplicate guard, frozen header, styled header row.
     *
     * <p>Idempotent and cheap enough to call on every build. Auto-sizing the columns is deliberately
     * not included — see {@link SheetSetup#autoResizeColumns()}.
     *
     * @return one line per change actually made; empty means it was all already in place
     */
    public List<String> prepare(TypeDef<?> def) {
        return setup(def).keyGuard().protectHeader().freezeHeader().headerStyle().apply();
    }

    // ---- odds and ends ---------------------------------------------------------

    /** The spreadsheet this session is bound to. */
    public String spreadsheetId() {
        return spreadsheetId;
    }

    /** Registered types, in registration order. */
    public List<TypeDef<?>> registered() {
        return List.copyOf(types.keySet());
    }

    /**
     * Close the mirror. Pending writes are <b>not</b> flushed — closing is not saving, and a
     * try-with-resources that silently wrote to a shared spreadsheet on the way out of an exception
     * would be worse than one that did not.
     */
    @Override
    public void close() {
        try {
            conn.close();
        } catch (SQLException e) {
            throw new GSheetDbException("could not close the H2 mirror", e);
        }
    }

    // ---- package internals -----------------------------------------------------

    /** The engine for one type. Reached through {@link Query} and {@link Upsert}, not by callers. */
    @SuppressWarnings("unchecked")
    <E extends DataHelper_I<E>> SheetDocs<E> docs(TypeDef<E> def) {
        requireLoaded();
        return (SheetDocs<E>) docsOf(def);
    }

    private SheetDocs<?> docsOf(TypeDef<?> def) {
        var d = types.get(def);
        if (d == null) {
            throw new IllegalArgumentException((def == null ? "null" : def.name())
                    + " is not registered on this session. Call register("
                    + (def == null ? "def" : def.name() + ".DEF") + ") first.");
        }
        return d;
    }

    /** The API client, for {@link SheetSetup}. Never handed outside the package. */
    Sheets sheets() {
        return sheets;
    }

    void markDirty() {
        dirty = true;
    }

    void requireLoaded() {
        if (!loaded) {
            throw new IllegalStateException("load() has not been called, so the mirror is empty.");
        }
    }

    /**
     * A session with no Sheets client behind it, for tests.
     *
     * <p>Everything above the API boundary — the mirror, the query DSL, key matching and duplicate
     * detection, dirty tracking, row coalescing, the cells a flush would send — is exercisable
     * without a network or a credential, and is worth exercising that way.
     */
    static GSheetDb offline(String spreadsheetId) {
        try {
            Class.forName("org.h2.Driver");
            var url = "jdbc:h2:mem:gsheetsdb_offline_" + INSTANCE.incrementAndGet();
            return new GSheetDb(null, spreadsheetId, DriverManager.getConnection(url));
        } catch (ClassNotFoundException | SQLException e) {
            throw new GSheetDbException("could not start the in-process H2 mirror", e);
        }
    }

    /** Load the registered types from grids supplied directly, as {@link #load()} would from the API. */
    GSheetDb loadOffline(Map<String, List<List<Object>>> gridsByName) {
        try {
            int syntheticSheetId = 0;
            for (var entry : types.entrySet()) {
                String name = entry.getKey().name();
                if (!gridsByName.containsKey(name)) {
                    throw new GSheetDbException("no grid supplied for '" + name + "'");
                }
                entry.getValue().load(syntheticSheetId++, gridsByName.get(name));
            }
            loaded = true;
            dirty = false;
            return this;
        } catch (SQLException e) {
            throw new GSheetDbException("could not build the H2 mirror", e);
        }
    }
}
