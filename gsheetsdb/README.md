# uskoag-gservices-gsheetsdb

A Google spreadsheet, used as a small typed document store.

Annotate a class, and its documents live in the sheet named after it. Query them in Java with the compiler checking field names and value types. Reads are served from an in-process H2 mirror, so after one load there are no API calls at all. Writes accumulate and go back in a single batched update when you say so.

Built for the UAN treaties and documents website: read-heavy, human-edited data that belongs in a spreadsheet because people need to edit it in a spreadsheet.

## This is an ORM, minus the parts that made ORMs a byword

- **No lazy loading, so no N+1.** `load()` reads everything registered in two API calls, and after that nothing in the library can decide to fetch. There is no proxy that hits the network from a getter.
- **No dirty-checking magic.** A document is a plain object. A save is a save.
- **No detached-entity state machine.** One session; it is open or it is closed.
- **No string queries.** Fields are `$symbols` the compiler checks. `eq($year, "big")` does not compile.
- **No reflection**, anywhere — so it survives GraalVM native.
- **No name mapping.** There is no `@Table(name=…)` and no `@Column(name=…)`, deliberately: those are how a schema nobody can read ends up next to a model no schema explains. Here the sheet is named after the type and a field is named after itself, so the person editing the spreadsheet is reading the class.

What is left is the part that was always worth having: typed objects instead of strings and casts.

The vocabulary throughout is **document** and **field**, following ArcadeDB — a sheet holds documents, a document has fields. Nothing here says table, row or column. The grid is storage, the same way ArcadeDB's buckets are storage, and you never name those either.

## Where it came from

`uskoag-gdrive-core` uses ArcadeDB through the `@ArcadeData` / `TypeDef` / `Query` ergonomics of `xyz-jphil-arcadedb-datahelper`. That reads well and is worth having over a spreadsheet too. This library reproduces the ergonomics and none of the graph engine.

It deliberately does **not** use `@ArcadeData`: that annotation's generated base implements `ArcadeDoc_I`, which binds `com.arcadedb.database.Document`, `Database`, `Edge`, `Vertex` and `RID` — so every model class would drag `arcadedb-engine` onto the classpath for a graph database nothing ever calls. The base `@Data` annotation gives everything this needs and one thing more (`_R` immutable record projections, which `@ArcadeData` does not generate). `prp/01-prp.01.design.md` has the full audit.

## A document type

```java
@Data
public final class Treaty extends Treaty_A {

    String treatyId;
    String title;
    Integer year;
    Boolean ratified;
    Double signedOn;          // serial date — see "Dates" below
    String status;

    public static final TypeDef<Treaty> DEF =
            typeDef(Treaty.class, Treaty::new)
                    .key($treatyId)
                    .__();
}
```

Package-private fields, `final` class, `extends Treaty_A`. The processor writes the accessors, the `$field` symbols, and the reflection-free property access the library runs on.

Two rules, neither overridable. **The sheet is named after the type** — `Treaty` lives in the sheet called `Treaty`. **A field is named after itself** — `titleShort` is the header `titleShort`. There is no rename hook, because a mapping layer is only a place for the two names to drift, and the fix for drift is to correct the sheet.

Fields are not hand-listed. `__()` reads them off a prototype instance, so a field list cannot fall out of step with the class.

## Using it

```java
import static uskoag.gservices.sheetsdb.TypeDefBuilder.typeDef;
import static uskoag.gservices.sheetsdb.Query.query;
import static uskoag.gservices.sheetsdb.Upsert.upsert;
import static com.example.model.Treaty_IR.*;          // $treatyId, $title, $year

Sheets api = SheetsService.sheets(access, "uan-treaties");

try (var db = GSheetDb.open(api, spreadsheetId).register(Treaty.DEF).load()) {

    List<Treaty> recent = query(db, Treaty.DEF)
            .gt($year, 2000)
            .eq($ratified, true)
            .orderByDesc($year)
            .limit(20)
            .list();

    Treaty t = query(db, Treaty.DEF).byKey("UAN-1997-03");
    t.title("Revised title");
    upsert(db, Treaty.DEF).save(t);

    db.flush();               // one batched write, here and nowhere else
}
```

`query(db, DEF)` and `upsert(db, DEF)` take the same two arguments ArcadeDB's `query(db, TYPEDEF)` takes, and for the same reason: the definition already carries the type name and the `X::new` factory, so neither appears at the call site.

`load()` costs two API calls total no matter how many types are registered — one for the workbook metadata, one `batchGet` for all the sheets. Everything after that is local.

Conditions: `eq neq lt le gt ge like ilike in between isNull isNotNull`, joined with `and()` / `or()`, grouped with `group(...)` / `notGroup(...)`, plus a raw `where("...", params)` hatch. Terminals: `list stream iterator first firstOrNull byKey count exists`.

Grouping is worth pointing out because the ArcadeDB original could not do it — its javadoc says `(a OR b) AND (c OR d)` is inexpressible and to drop to raw SQL:

```java
query(db, Treaty.DEF)
        .group(g -> g.eq($status, "SIGNED").or().eq($status, "RATIFIED"))
        .gt($year, 1990)
        .list();
```

## Key integrity — the one constraint a spreadsheet cannot enforce

A document is identified by its key field, never by its position: a row moves whenever anything above it is inserted or deleted. A type with no `.key(...)` is read-only, because without a key there is no way to say which existing document an object is, and guessing overwrites somebody's data.

But no spreadsheet can enforce uniqueness, so this library does what can be done at each end.

**At load**, every key value carried on more than one row is found and recorded. Touching one of them throws, naming the rows, rather than picking one:

```
Treaty: the key 'UAN-1997-03' appears on sheet rows [2, 6]. Two documents cannot share
one identity — reading would pick one at random and writing would discard the other.
```

Two documents with one identity is corruption, not a result. `db.duplicateKeys(Treaty.DEF)` returns the whole map for reporting, and one bad key does not close the sheet — every other key still works.

**In the spreadsheet**, `db.setup(Treaty.DEF).keyGuard().apply()` installs three things at the other end of the wire:

- **A duplicate-report column.** One formula, in the header cell, covering the whole column — no fill-down to go stale when rows are added. Where a key repeats, the cell reads `rows 14, 37`: an error message the spreadsheet generates for itself, naming exactly where to look. `report(Report.COUNT)` gives the cheaper `2` instead, for a sheet big enough that per-row `MAP`/`FILTER` starts to drag.

  ```
  ={"treatyId__duplicates";MAP($A$2:$A,LAMBDA(v,IF(v="","",
     IF(COUNTIF($A$2:$A,v)>1,"rows "&TEXTJOIN(", ",TRUE,FILTER(ROW($A$2:$A),$A$2:$A=v)),""))))}
  ```

  It is appended after the last header, not inserted beside the key. Inserting shifts every column to its right and silently invalidates every A1 reference anyone has written into the sheet. Appending disturbs nothing — and a save never reaches it, because a save writes only as far as the last declared field.

- **A whole-row highlight**, so a duplicate is visible from across the room rather than needing you to be looking at the id column.

- **Input validation** on the key — reject-on-invalid with a custom formula.

Everything is idempotent: existing rules are read once and identical ones are not added twice, so a build can call it on every run.

### Data validation is not enforcement, and it is worth being blunt about that

It looks like a unique constraint. It is not one, and it cannot be made into one. It stops somebody **typing** a duplicate id, which is genuinely worth having, and it stops nothing else:

- **The API ignores it.** Validation is a UI-layer check. Anything written through `spreadsheets.values` — including this library's own `flush()` — goes straight past it.
- **Paste carries its own rules.** Pasting cells brings the source's validation with it, so a paste can overwrite the guard along with the value that violates it.
- **Anyone can open Data › Data validation and delete it.**

So the three sheet-side measures make a duplicate loud, not impossible. The one that actually holds is the load-time check above, because it is the only one on this side of the network. Treat the spreadsheet as a place where duplicates get noticed early, and the loader as the thing that refuses to act on one.

## Sharing the sheet with people who are editing it

A spreadsheet is not a database you own; it is a document other people have open right now. Two rules follow, and they are structural rather than best-effort.

**A save writes only the cells its own fields occupy.** A row is sent as one rectangle per run of consecutive declared fields. For a sheet whose columns go `docId title notes year ratified`, saving one document sends `A2:B2` and `D2:E2` — column `notes` is not in the request at all. Whoever is typing in it cannot lose that edit to a flush, whatever the timing.

This replaced an earlier design that read the whole row at load, overwrote the declared cells and wrote the lot back. That preserved the neighbour only *as it stood at load time*, so an edit made in the browser between the load and the flush was silently reverted. That is a lost update, and it is not fixable by re-reading more often — only by not writing the cell.

The same rule protects the far end of the sheet: the write extent is the last **declared** field, not the last column. Anything to the right of your model — a note column, a chart's source range, the duplicate report this library installs itself — is never in range.

**A new column is placed past everything the sheet is using, and checked again before it is claimed.** `usedWidth` is measured across every row, not just the header, because a column can hold data under a blank header and the header row alone cannot see it. Then, because that measurement was taken at `load()` and a spreadsheet has other people in it, the target column is re-read immediately before writing and anything unexpected is refused:

```
Will not write the duplicate report into column AY of 'Documents': row 12 of it already
holds "checked - RG". Something was added to the sheet after this session loaded it.
Reload and try again, or clear that column first — this library will overwrite nobody's
data to make room for a convenience.
```

Refusing is the right failure there. A duplicate-report column is a convenience; somebody's data is not.

Appended rather than inserted, too. Inserting shifts every column to its right and silently invalidates every A1 reference anyone has written into the workbook — a formula, a named range, a chart, another tool's hard-coded address.

**Every address is re-resolved immediately before the write.** The dangerous failure on a shared sheet is not a stale *value*, it is a stale *address*. Everything held about where things are was measured at `load()`. Insert one column and every held index is off by one; write then, and each value lands one column to the left of where it belongs, silently, in every row touched. Insert a row and every `_ROW` points at the neighbouring document.

So `flush()` spends two small reads first: the header row of each type with pending changes, then its key column. Columns are re-bound by field **name**, documents re-located by **key**. A column or row somebody inserted since the load becomes a non-event rather than a corruption, and the mirror is re-read afterwards because its own row numbers are now stale.

When it cannot be resolved silently, nothing is written at all:

```
Sheet 'Documents': [un-report-11] no longer has a row — deleted after this session
loaded. Nothing has been written; the rest of the flush was abandoned with it.
Reload and retry.
```

```
Sheet 'Documents' no longer has a header for [clearance, ref]. Someone changed the
header row after this session loaded it, so there is nowhere safe to write. Nothing
has been written. Reload and retry.
```

**Why this rather than a lock.** A lock binds whoever agrees to read it. The person inserting a column is in a browser and never will — so a semaphore tab protects you only against your own other processes, which is the rare case, and gives nothing against the common one. It also adds a failure mode with teeth: a run that dies mid-write leaves the lock held, and the next build refuses to start until a human clears a cell. Verification at the moment of use needs no cooperation from anyone and has nothing to clean up after a crash.

**And `prepare()` protects the header row**, which is the one thing in this area Google genuinely enforces. `AddProtectedRangeRequest` with `warningOnly` makes a reorder require confirmation in the browser; without it, only the listed editors can touch the header at all. Verification catches a reorder having happened; protection stops it happening.

**What is still not protected**, stated so nobody assumes otherwise. A document whose *own* fields someone edits between your load and your flush will be overwritten by your values — an ordinary write conflict, no version check; keep the window short, or flush more often. And `delete` removes a whole sheet row, so anything on that row in an undeclared column goes with it.

## Freezing and formatting

```java
db.prepare(Treaty.DEF);       // keyGuard + frozen header + styled header row
```

or pick:

```java
db.setup(Treaty.DEF)
  .keyGuard(Report.ROWS, true, true)
  .freeze(1, 1)               // header row, and the key column
  .headerStyle()
  .autoResizeColumns()
  .apply();
```

`apply()` returns one line per change actually made, suitable for a build log; empty means it was all already in place. It costs one `batchUpdate`, plus one `values.update` when a duplicate column is being written — that write is the library's only `USER_ENTERED` one, because it is a formula and `RAW` would store it as text.

`autoResizeColumns()` is opt-in and not in `prepare()`, because it is only an improvement when no field holds long prose. One 400-character `summary` and auto-resize gives you a column wider than the screen, which is worse than the default.

## Things worth knowing before you rely on it

**The mirror does not see edits made in the browser after it loaded.** Call `refresh()` when that matters. It refuses while writes are pending rather than dropping them; `discard()` is the explicit "throw my changes away".

**`close()` does not flush.** Closing is not saving. A try-with-resources that silently wrote to a shared spreadsheet on the way out of an exception would be worse than one that did not.

**A save never addresses a cell this library does not own** — see the section below. That is the guarantee that matters on a sheet people are editing while you work.

**A declared field the sheet lacks** reads as `null` — which lets a model run against a sheet that has not grown the field yet — but blocks saving, naming what it cannot place.

**`neq` follows SQL, so a blank cell does not match it.** `NULL <> 'X'` is unknown, not true. On a spreadsheet, where blank is the commonest value in any optional field, this surprises people: `.group(g -> g.neq($status, "X").or().isNull($status))` includes blanks. Occasionally the strictness is exactly right — `neq($field, "")` excludes an empty cell whether it holds an empty string or nothing at all.

**Deleting re-reads the sheet.** A deletion shifts every row below it, invalidating held row numbers. Rather than patch them, a flush carrying a deletion reloads afterwards — one extra API call, always correct.

**One instance, one thread.** It holds a single JDBC connection and mutable per-type write state.

**Writes use `RAW`, not `USER_ENTERED`.** A title beginning with `=` is a title, not a formula, and a code like `+44` is not a number. Booleans still write as real booleans, so a checkbox stays a checkbox.

## Dates

Declare a date field as `Double` and convert with `SheetDates`:

```java
public LocalDate signedOnDate() {
    return SheetDates.toLocalDate(signedOn);
}
```

A spreadsheet stores a date as days since 1899-12-30, and reading with `UNFORMATTED_VALUE` — which this library does, and must, or every number would arrive as the display string `"1,234"` — hands that number back. Keeping the field numeric round-trips exactly and keeps the cell a real date to the sheet's own formulas.

`String` would be worse than it looks: an unformatted date cell arrives as `35502.0`, so a String field would faithfully store `"35502"`.

**But if the date is a claim the document makes rather than one the spreadsheet owns, use `String` after all** — a partial date (`1997`, `1997-03`) and "no date stated" are facts a serial number cannot represent, and rounding them to 1 January invents a fact. Serial for a timestamp; text for a date being quoted.

The reason it is not simply `LocalDate`: the DataHelper processor's field whitelist is primitives, boxed primitives, `String`, other DataHelper types, `List` and `Map`. A `LocalDate` field, or an enum field, is rejected at compile time. This library converts `LocalDate`, `LocalDateTime` and enums correctly wherever it meets them — a hand-written `DataHelper_I` may use them today, and `@Data` models will need no change here if that whitelist grows.

## Maven — and the two compiler passes you also need

```xml
<dependency>
    <groupId>io.github.uskoag</groupId>
    <artifactId>uskoag-gservices-gsheetsdb</artifactId>
    <version>1.0</version>
</dependency>
```

**Your own build needs the two-pass compiler block** for whichever source root holds your model classes. A single pass cannot build them from clean, for two separate reasons, and neither failure looks like the problem:

1. JDK 23 stopped running annotation processing implicitly. Without an explicit `<proc>only</proc>` the processor is skipped **in silence** — no error, no note, just no generated code.
2. Even with processing on, one pass still fails. Code outside the model package imports the generated types by name (`Treaty_R`, `import static ...Treaty_IR.*`). Those imports are unresolvable in round one, javac reports them and gives up before any processing round runs — so the very files that would satisfy the imports are never generated.

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.13.0</version>
    <executions>
        <execution>
            <id>generate-datahelper-accessors</id>
            <phase>generate-sources</phase>
            <goals><goal>compile</goal></goals>
            <configuration>
                <proc>only</proc>
                <generatedSourcesDirectory>${project.build.directory}/generated-sources/annotations</generatedSourcesDirectory>
                <includes>
                    <include>com/example/model/*.java</include>
                </includes>
                <annotationProcessorPaths>
                    <path>
                        <groupId>io.github.xyz-jphil</groupId>
                        <artifactId>xyz-jphil-datahelper-processor</artifactId>
                        <version>1.0</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </execution>
        <execution>
            <id>default-compile</id>
            <configuration>
                <proc>none</proc>
                <generatedSourcesDirectory>${project.build.directory}/generated-sources/unused-second-pass</generatedSourcesDirectory>
            </configuration>
        </execution>
    </executions>
</plugin>
```

That second execution's redirected output directory is not cosmetic. The plugin clears its `generatedSourcesDirectory` before compiling, and on the default value that is the directory pass one just wrote to.

## Credentials

This library has none and wants none. It takes a configured `Sheets` and never asks where it came from — `uskoag.gservices.SheetsService` is the usual route, and going through the wallet is the application's business. gservices libraries stay credential-free; only end-user binaries carry a wallet dependency.

## Not in v1

- Relations between types. Sheets are flat and independent; there is no reference concept.
- Nested object, list and map fields. The processor supports them; this library does not map them to a grid yet.
- Creating a missing sheet or writing a header row. Creating spreadsheets belongs to `uskoag-gdrivecli`.
