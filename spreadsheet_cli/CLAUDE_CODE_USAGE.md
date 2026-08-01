# Using uskoag-gsheetscli from Claude Code

## TL;DR

```bash
uskoag-gsheetscli read  SHEET_ID SheetName A1
uskoag-gsheetscli read  SHEET_ID SheetName A1:H20 --format tsv
uskoag-gsheetscli write SHEET_ID SheetName A1 "value"
uskoag-gsheetscli write SHEET_ID SheetName A1 --tsv-file data.tsv
```

**Output contract (as of PRP-01):**
- **stdout** carries ONLY the command's data (cell values / sheet list / a single `SUCCESS:` line).
- **stderr** carries ONLY `[ERROR]` (always) and `[INFO]` (only with `-v`).
- No JVM/AOT bootstrap noise reaches either stream on normal (warm) runs — the AOT cache is pre-built at `mvn package` time. You do **not** need `2>/dev/null` anymore, though it remains harmless.

## Commands

| Command | Purpose |
|---------|---------|
| `read <id> <sheet> <range> [<range2>...]` | Read a cell, a range, or several ranges (batchGet). `--formulas` / `--render` for formulas; `--both` for formula+value; `--rich` for value+formula+note+color+numberFormat. |
| `query <id> <sheet> "<SQL>"` | Run **SQL** over the sheet (columns `A,B,C…` + `_row`). Read-only. |
| `update <id> <sheet> "<SQL UPDATE>"` | Conditional **UPDATE**; writes only changed cells, returns `old→new` diff. `--dry-run`, `--as-date`. |
| `inspect <id> <sheet> [<range>]` | Show cell **notes** + background **colors** (things `read` can't see). |
| `write <id> <sheet> <address> [<value>]` | Write a single cell, or a 2-D block from a file/inline data. `--as-date` for real date values. |
| `append <id> <sheet> [<startRange>]` | Append rows to the end of a sheet (INSERT_ROWS). `--as-date`. |
| `clear <id> <sheet> <range>` | Empty a range. |
| `listsheet <id>` | List sheet tabs + IDs. |
| `createsheet <id> <name>` | Add a sheet tab. |
| `renamesheet <id> <old> <new>` | Rename a sheet tab. |
| `freeze <id> <sheet> [--rows N] [--cols N]` | Freeze header rows/columns (0 unfreezes). |
| `insertcolumn <id> <sheet> <colLetter>` | Insert blank column(s), shifting everything at/after right. `--count N`. |
| `insertrow <id> <sheet> <rowNumber>` | Insert blank row(s), shifting everything at/after down. `--count N`. |
| `format <id> <sheet> <range> [flags]` | Color/style cells (bg, text color, bold, italic, align, wrap). |
| `numberformat <id> <sheet> <range>` | Set number/date rendering format. `--type DATE\|TIME\|DATE_TIME\|NUMBER\|PERCENT\|CURRENCY\|SCIENTIFIC\|TEXT`, `--pattern`, or `--clear`. |
| `validate <id> <sheet> <range> (--from-range R\|--list a,b,c)` | Dropdown / data validation. `--warn`, `--clear`; `--colors`/`--color-legend` to bundle in matching coloring. |
| `highlight <id> <sheet> <range> (--from-range R\|--list a,b,c\|--colors-from-range R)` | Color-code each allowed value (conditional format). `--colors`, `--clear`. |
| `filter <id> <sheet> [<range>]` | Turn on the header-row filter funnel buttons; `--clear` removes. Range optional (→ whole sheet). |
| `describeschema <id> [--sheets a,b,...]` | Export a rich per-sheet schema (headers+notes, diversified sample) for feeding a spreadsheet's shape to an AI agent. `--format json\|md\|text`. |
| `grant --read\|--write <id> [name]` | Add the spreadsheet to the local allowlist. |
| `revoke <id>` | Remove it. |
| `listperms` | Show the allowlist (no Google auth needed). |

Global flags: `-v`/`--verbose` (logs to stderr), `--quiet` (suppress the `SUCCESS:` line; exit code still conveys success).

## Value input mode (write / append) — IMPORTANT

- **Default is `RAW`**: values are stored literally. A phone number `+91 80 4656 3000`, an ID with leading zeros, or a string starting with `=`/`+`/`-` is preserved exactly — no `#ERROR!`, no `tel:` workaround needed.
- Add **`--user-entered`** to opt into Google's formula/auto-typing (so `=1+1` becomes `2`, `50%` becomes `0.5`, etc.).
- `--raw` is accepted explicitly but is the default, so it's a no-op.

## Bulk write — one call, not 160

Write a 2-D block starting at the top-left of `<address>`:

```bash
# From files (best for agents — no shell quoting headaches):
uskoag-gsheetscli write SHEET_ID Sheet1 A1 --tsv-file  grid.tsv
uskoag-gsheetscli write SHEET_ID Sheet1 A1 --csv-file  data.csv     # RFC-4180: quotes/commas/newlines OK
uskoag-gsheetscli write SHEET_ID Sheet1 A1 --json-file matrix.json  # [["a","b"],["c","d"]]

# Inline:
uskoag-gsheetscli write SHEET_ID Sheet1 A1 --tsv $'a\tb\tc\nd\te\tf'
uskoag-gsheetscli write SHEET_ID Sheet1 A1 --json '[["a","b"],["c","d"]]'
```

- **TSV** is the friendliest: tab = column break, newline = row break, no quoting.
- **CSV** is full RFC-4180: embedded commas, `""`-escaped quotes, and newlines inside quoted fields all round-trip losslessly.
- The same data flags work on `append`.

## Read output formats

```bash
uskoag-gsheetscli read SHEET_ID Sheet1 A1:C2 --format kv    # default: "A1": "value"  (one line per cell)
uskoag-gsheetscli read SHEET_ID Sheet1 A1:C2 --format tsv   # rectangular grid, cheapest to parse
uskoag-gsheetscli read SHEET_ID Sheet1 A1:C2 --format csv   # RFC-4180
uskoag-gsheetscli read SHEET_ID Sheet1 A1:C2 --format json  # [["..","..",..],..]
```

- A single-cell read (`read ... A1`) always prints the bare value, regardless of format.
- Blank cells are preserved positionally (not collapsed).
- Columns past Z work correctly: `read ... Y1:AC2` emits `Y1,Z1,AA1,AB1,AC1,…`.

### Formulas instead of values

```bash
uskoag-gsheetscli read SHEET_ID Sheet1 C1 --formulas            # "=SUM(A1:A9)" instead of 30
uskoag-gsheetscli read SHEET_ID Sheet1 C1 --render UNFORMATTED  # superset: FORMATTED (default)|UNFORMATTED|FORMULA
```
`--formulas` is shorthand for `--render FORMULA` (mutually exclusive with `--render`). A cell with no formula still returns its literal value.

### Both formula and value — no need to choose

```bash
uskoag-gsheetscli read SHEET_ID Sheet1 A1:C3 --both --format json   # nested {"formula":..,"value":..} per cell
uskoag-gsheetscli read SHEET_ID Sheet1 A1:C3 --both --format tsv    # same pair as compact JSON text per cell
```
`--both` fetches each range twice (value + formula) and pairs them per cell — mutually exclusive with `--formulas`/`--render`. `--format json` gets real nested objects; `kv`/`tsv`/`csv` embed the same `{"formula":"=SUM(A1:A9)","value":"30"}` pair as a compact JSON string per cell, so it round-trips through every format (tsv included — no embedded tabs/newlines to worry about).

### Batch read (scattered ranges in one call)
```bash
uskoag-gsheetscli read SHEET_ID Sheet1 A1:C3 E1:F2 --format tsv
# Output is delimited per range:
#   # Sheet1!A1:C3
#   <rows...>
#   # Sheet1!E1:F2
#   <rows...>
```

## Query — SQL over the sheet (don't download-then-filter)

When you need *specific* rows/columns — "who haven't I emailed yet", "which contacts are duplicated", "distinct recipients" — **don't `read` the whole sheet and reason over it** (token-wasteful, error-prone). Push the work down as SQL. The tab (or `--range`) loads into a throwaway in-memory H2 table `t`, your `SELECT` runs, and only the result returns. It's **read-only** — the copy is discarded per call, so it can never write to the real sheet.

```bash
# rows not yet emailed (blank "Emailed" column D) — _row ties each back to the sheet
uskoag-gsheetscli query SHEET_ID Contacts "SELECT _row, A, C FROM t WHERE _row>1 AND D=''"

# duplicate emails (repeat candidates) — GROUP BY / COUNT / HAVING
uskoag-gsheetscli query SHEET_ID Contacts "SELECT C, COUNT(*) n FROM t WHERE _row>1 GROUP BY C HAVING COUNT(*)>1"

# distinct recipients as cheap TSV
uskoag-gsheetscli query SHEET_ID Contacts "SELECT DISTINCT C FROM t WHERE _row>1" --format tsv

# load only part of the sheet; _row stays aligned to real sheet rows
uskoag-gsheetscli query SHEET_ID Contacts "SELECT _row, A FROM t" --range A2:D
```

Rules to write correct queries:
- **Columns = sheet letters**: `A`, `B`, `C`, … (bare, uppercase). Quote two-letter names that are SQL keywords: `"AS"`, `"IN"`, `"OR"`.
- **`_row`** = the 1-based source sheet row number (an extra column). Select it so every result row is traceable.
- **Everything is text** (stored literally). For numeric/date compares, `CAST`: `WHERE CAST(E AS INT) > 100`.
- **Row 1 (header) is data too** → add `WHERE _row > 1` to skip it. (Header-not-on-row-1 handling is deliberately deferred; you know the schema, so filter explicitly.)
- Full SQL is available: `WHERE`, `GROUP BY`/`HAVING`, `COUNT`/`DISTINCT`, `ORDER BY`, `LIMIT`, self-joins, subqueries.
- **`--format json`** (default) = array of objects keyed by result column; `_row` and `COUNT(*)` come back as JSON **numbers**. `--format tsv`/`csv` = header row + rows (cheapest tokens).
- Bad SQL/column → one `[ERROR]` line on stderr, exit 1 (e.g. `Column "Z" not found`).

**Gmail ↔ sheet reconciliation** (the motivating workflow): use `query` for the sheet side — distinct recipients / already-emailed / blank-status / duplicates — then diff against the `uskoag-gmailcli` side agent-side. The sheet CLI covers one spreadsheet at a time; cross-source correlation stays in the agent.

## Update — conditional SQL UPDATE (writes changed cells + returns old→new)

The write side of `query`, and the safe way to change existing rows (e.g. mark contacts emailed). The sheet loads into the in-memory `t` table, your SQL **`UPDATE`** runs, and **only the cells that actually changed** are pushed back — with a full `old→new` diff so you can verify and undo. Needs **write** permission.

```bash
# PREVIEW first — computes the diff, writes NOTHING:
uskoag-gsheetscli update SHEET_ID Contacts "UPDATE t SET D='yes' WHERE C='a@x.com'" --dry-run

# COMMIT — mark row 5 emailed with a date, one call:
uskoag-gsheetscli update SHEET_ID Contacts "UPDATE t SET D='yes', E='2026-07-15' WHERE _row=5"
```

How to use it safely:
- **`WHERE` is the safety gate** — if it matches nothing, `affected` is `0` and nothing is written.
- **Always `--dry-run` first** when unsure; it returns the exact diff without touching the sheet. Re-run without the flag to commit.
- The output is JSON (default) or `tsv`, reporting `affected` (rows matched), `changedCells`, `written` (0 on dry-run), `dryRun`, and `changes[]` = `{row, cell, col, old, new}`:
  ```json
  {"affected":2,"changedCells":2,"written":2,"dryRun":false,
   "changes":[{"row":2,"cell":"D2","col":"D","old":"","new":"yes"},
              {"row":4,"cell":"D4","col":"D","old":"","new":"yes"}]}
  ```
- **Self-heal / undo**: reverse each change from the diff — `update ID Sheet "UPDATE t SET <col>='<old>' WHERE _row=<row>"`.
- Same column rules as `query`: text cells (`CAST` for numeric compares), `A,B,C…` letters, and **row 1 is data** — add `WHERE _row>1`, or target a specific `WHERE _row=N`.
- **Only `UPDATE`** is accepted (`DELETE`/`INSERT`/`DROP` are rejected). Add rows with `append`; blank cells with `clear`/`write`. Changed cells go in RAW by default; `--user-entered` for formulas.
- **Upsert pattern**: run `update`; if `affected` is `0` the key isn't there yet → `append` the row. Two transparent, self-describing steps.

## Inspect — notes & cell colors (what `read` can't see)

`read` returns only values. Use `inspect` to pull **cell notes** and **background colors** in one call:

```bash
uskoag-gsheetscli inspect SHEET_ID Sheet1                  # whole sheet: notes + colors
uskoag-gsheetscli inspect SHEET_ID Sheet1 A1:Q240          # restrict to a range
uskoag-gsheetscli inspect SHEET_ID Sheet1 --notes-only     # just notes
uskoag-gsheetscli inspect SHEET_ID Sheet1 --colors-only --format json
```

- **Notes** → one line per cell: `O120: <text>` (newlines escaped to `\n` in text mode).
- **Colors** → same-colored contiguous cells are merged into maximal rectangles and grouped by
  color, so you get `#fff2cc light yellow: A1:E1, A120:Q139` instead of hundreds of identical
  lines. Each color carries its `#rrggbb` hex + a nearest-swatch human name. Plain white
  (unfilled) cells are omitted.
- `--format text` (default) | `json`. JSON:
  `{"sheet","range","notes":[{"cell","note"}],"colors":[{"hex","name","ranges":[…]}]}`.
- **Output is UTF-8** — en-dashes / arrows / curly quotes / non-Latin text in notes come through
  intact (no cp1252 `?`/`0x96` corruption).

## Freeze, format, validate — header rows, cell style, dropdowns

Write-side formatting commands, aimed at sheets a script/agent generates for a human to use
afterward (a leads/CRM tracker, say): a frozen + styled header row, and a column constrained
to an allowed set of values via a dropdown whose vocabulary lives in another sheet (so it's
editable in one place instead of hard-coded per cell).

```bash
# Stylized header, then freeze it
uskoag-gsheetscli format SHEET_ID Sheet1 A1:Z1 --bg "#4a86e8" --text "#ffffff" --bold
uskoag-gsheetscli freeze SHEET_ID Sheet1 --rows 1

# Dropdown sourced from another sheet — the recommended pattern
uskoag-gsheetscli validate SHEET_ID Sheet1 C2:C --from-range "'Allowed Values'!A2:A"

# Dropdown from an inline list; --warn flags bad input instead of rejecting it
uskoag-gsheetscli validate SHEET_ID Sheet1 D2:D --list contacted,replied,bounced --warn

# Remove a validation rule
uskoag-gsheetscli validate SHEET_ID Sheet1 C2:C --clear

# highlight: color-code each allowed value so it reads at a glance (conditional format, not static)
uskoag-gsheetscli highlight SHEET_ID Sheet1 C2:C --from-range "'Allowed Values'!A2:A"   # auto palette
uskoag-gsheetscli highlight SHEET_ID Sheet1 C2:C --list contacted,replied,bounced --colors "#d9ead3,#fff2cc,#f4cccc"
uskoag-gsheetscli highlight SHEET_ID Sheet1 C2:C --colors-from-range "'Allowed Values'!A2:A"   # mirror the legend's own colors
uskoag-gsheetscli highlight SHEET_ID Sheet1 C2:C --clear

# bundle: validate + highlight in one call, colors the data range AND the legend
uskoag-gsheetscli validate SHEET_ID Sheet1 C2:C --from-range "'Allowed Values'!A2:A" \
    --colors "#d9ead3,#fff2cc,#f4cccc" --color-legend
```

- `freeze` needs at least one of `--rows N` / `--cols N`; `N=0` unfreezes.
- `format` only touches the flags you pass (a fields mask) — `--bg`/`--text` (`#rrggbb`),
  `--bold`/`--italic`, `--align left|center|right`, `--valign top|middle|bottom`,
  `--font-size N`, `--wrap`.
- `validate` defaults to **strict** (invalid entries rejected); `--warn` flags instead of
  rejecting. Both `--from-range` and `--list` show a dropdown (`showCustomUi`) in Sheets.
- `highlight` constrains nothing — it just makes *which value is there* visible: one conditional-format
  rule per distinct value (exact-text match, background color only). Value/color source, pick one:
  `--from-range`/`--list` (paired with `--colors` or the auto palette), or `--colors-from-range`
  (reads BOTH value and color directly off a legend's own current cells — color the legend once,
  then mirror that mapping anywhere without retyping a `--colors` list). Prints the `value=color`
  pairs actually used.
- **Re-running `highlight` on the same range replaces its rules instead of stacking duplicates** —
  this is what makes revisions painless: add a value to the legend, re-run the same command, done.
  New rules are appended after any *other* pre-existing conditional formatting on the sheet, which
  stays untouched; `--clear` only removes rules on the exact range given.
- `validate --colors [--color-legend]` bundles highlight in: `--colors` alone colors the data range;
  `--color-legend` (requires `--from-range`) also colors the legend cells with the same mapping.
  Because `--from-range` validation is *live* (Sheets re-reads the legend every time — no revision
  ever needed there), the only thing that ever needs re-running is the coloring: bump the legend,
  extend `--colors`, re-run the exact same `validate` command, and both color sets refresh in sync.
  Not allowed with `--clear` (clearing the dropdown and clearing highlight rules stay separate,
  deliberate actions).

## Filter — turn on the header-row filter buttons

`filter` enables the "Create a filter" funnel buttons on a header row (the Sheets UI's
Data → Create a filter). It turns the *option* on — no specific column criteria are set — so a
human can then filter/sort interactively.

```bash
uskoag-gsheetscli filter SHEET_ID Sheet1            # whole sheet: funnel buttons on row 1
uskoag-gsheetscli filter SHEET_ID Sheet1 A1:H       # over a range; its first row is the header
uskoag-gsheetscli filter SHEET_ID Sheet1 --clear    # remove the filter
```

- Range is optional (omit → whole sheet, buttons on row 1). A range's **first row** is the header.
- A sheet holds **exactly one** basic filter, so re-running replaces it — it never stacks.
- Needs **write** permission. Pairs with `freeze --rows 1` for a header that filters *and* stays pinned.

## Append

```bash
uskoag-gsheetscli append SHEET_ID Sheet1 --tsv-file newrows.tsv     # adds below existing data
uskoag-gsheetscli append SHEET_ID Sheet1 A1 --json '[["x","y"]]'    # optional start range (search anchor)
```
Prints the range that was actually written, e.g. `SUCCESS: appended 3 rows × 2 cols to Sheet1!A21:B23`.

## Number format — how a value renders

`format` is color/style; `numberformat` is the separate concept of how the value **renders**:

```bash
uskoag-gsheetscli numberformat SHEET_ID Sheet1 B2:B --type DATE                     # default yyyy-mm-dd
uskoag-gsheetscli numberformat SHEET_ID Sheet1 D2:D --type CURRENCY --pattern "$#,##0.00"
uskoag-gsheetscli numberformat SHEET_ID Sheet1 B2:B --clear
```
`--type` (required, or `--clear`): `DATE`, `TIME`, `DATE_TIME`, `NUMBER`, `PERCENT`, `CURRENCY`,
`SCIENTIFIC`, `TEXT` — each with a sensible default `--pattern`, overridable. Rendering only — it
doesn't convert a literal string into a real value (see `--as-date` for that).

## Writing real dates — `--as-date`

A date string written `RAW` (the default) just renders as text; `--as-date` parses it and writes a
real Sheets date value plus a matching number format, in one call — no locale-parsing ambiguity:

```bash
uskoag-gsheetscli write  SHEET_ID Sheet1 B2 "2026-07-24" --as-date
uskoag-gsheetscli write  SHEET_ID Sheet1 C2 "07/24/2026" --as-date --date-format MM/dd/yyyy
uskoag-gsheetscli update SHEET_ID Sheet1 "UPDATE t SET E='2026-07-24' WHERE _row=5" --as-date
```
`--date-type DATE` (default) / `TIME` / `DATE_TIME`; `--date-format <pattern>` overrides the ISO
default. `write`/`append` fail loudly on a value that doesn't parse (blanks are skipped, not an
error); `update`'s SQL `SET` can touch unrelated non-date columns, so those just pass through
unchanged. Ignores `--raw`/`--user-entered` — it writes a typed numeric value directly.

## Insert rows/columns

```bash
uskoag-gsheetscli insertcolumn SHEET_ID Sheet1 C
uskoag-gsheetscli insertrow    SHEET_ID Sheet1 3 --count 5
```
Shifts everything at/after the given column/row; existing formulas/validation/formatting shift with it.

## Read — rich per-cell detail (`--rich`)

Everything `inspect` can see (notes, color) plus value/formula/numberFormat, per cell, in one call:
```bash
uskoag-gsheetscli read SHEET_ID Sheet1 A1:C3 --rich --format json
```
`--format json` → real nested objects; `kv`/`tsv`/`csv` embed the same object as compact JSON per
cell (fields with nothing to report — no note, white/no-fill background — are simply omitted).
Mutually exclusive with `--formulas`/`--render`/`--both`.

## Describe schema — a spreadsheet's shape, for an agent's context

Feeding a whole spreadsheet's contents to an agent floods its context. `describeschema` exports
just the shape: each sheet's headers (with any cell notes as column comments) plus a small,
**diversified** data sample (not just the first N rows):

```bash
uskoag-gsheetscli describeschema SHEET_ID                                     # every sheet
uskoag-gsheetscli describeschema SHEET_ID --sheets Contacts --format md
```
`--sample-rows`/`--sample-size`/`--uniqueness-col` control the sampling (one random row per
distinct value in the uniqueness column, topped up randomly if there aren't enough distinct
values); `--truncate` caps long cell values (head+tail preserved around a truncation marker).
`--header-row`/`--data-start-row`/`--col-start`/`--col-end` override per-sheet defaults, but only
when `--sheets` names exactly one sheet. 3 API calls total regardless of sheet count.

## Errors

Default: one greppable line, e.g.
```
[ERROR] API ERROR 400: Unable to parse range: Sheet1!NOTACELL
[ERROR] PERMISSION DENIED: No read permission for: <id>
[ERROR] NOT FOUND (404): Requested entity was not found.
```
Add `-v` to also get the Java stack trace on stderr.

## Permissions / allowlist

The spreadsheet must be on the local allowlist before use. Manage it via the CLI (no manual XML editing):

```bash
uskoag-gsheetscli grant --write 1Bv0...Ge7Q "My Sheet"   # write implies read
uskoag-gsheetscli grant --read  1Bv0...Ge7Q "Readonly"
uskoag-gsheetscli listperms
uskoag-gsheetscli revoke 1Bv0...Ge7Q
```

Config file (still editable by hand if preferred):
`%USERPROFILE%\uskoag\gservices\spreadsheet_cli\SpreadsheetCli.xml`
```xml
<SpreadsheetCli>
    <permissions>
        <allowWrite name="My Sheet">1Bv0...Ge7Q</allowWrite>
        <allowRead  name="Readonly">ANOTHER_ID</allowRead>
    </permissions>
</SpreadsheetCli>
```

## Test spreadsheet
**ID**: `1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCd` · **Sheet**: `Sheet1`
