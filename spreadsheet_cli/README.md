# uskoag-gsheetscli - Command-Line Google Sheets Tool

A command-line interface for reading and writing Google Sheets, built for the uskoag-gservices library and general-purpose (and agentic) spreadsheet automation.

## Features

- ✅ **Read** single cells, ranges, or several ranges in one call (`batchGet`)
- ✅ **Query** the sheet with **SQL** — columns as `A,B,C…` + a `_row` source-row-number column (in-memory H2, read-only)
- ✅ **Update** with a conditional **SQL `UPDATE`** — writes only the changed cells, returns an `old→new` diff, `--dry-run` preview
- ✅ **Inspect** cell **notes** and background **colors** — colors merged into maximal rectangles ("light yellow: A2:Q40")
- ✅ **Read formulas** (`--formulas`/`--render`) instead of computed values
- ✅ **Freeze** header rows/columns, **format** cell color/text/alignment, **validate** (dropdowns whose allowed values can live in another sheet), **highlight** (color-code each allowed value via conditional formatting — auto palette, explicit colors, or derived from an already-colored legend); `validate --colors`/`--color-legend` bundles both together and stays in sync across revisions
- ✅ **Filter** — turn on the "Create a filter" funnel buttons on the header row (`filter` / `filter --clear`)
- ✅ **Number format** — set a cell/range's rendering format (`numberformat`: date/time/number/percent/currency/scientific/text, or `--clear`)
- ✅ **Write real dates** — `--as-date` on `write`/`update`/`append` converts a date string into a proper Sheets date value (not a literal string) and applies a matching number format, in one call
- ✅ **Insert rows/columns** — `insertrow`/`insertcolumn`, shifting everything at/after down or right
- ✅ **Rich read** — `read --rich` returns value + formula + note + background color + number format per cell, in one call
- ✅ **Describe schema** — `describeschema` exports a rich per-sheet schema (headers with their notes, plus a diversified data sample) for feeding a spreadsheet's shape to an AI agent
- ✅ **Write** single cells or a full 2-D block from TSV / CSV / JSON (file or inline)
- ✅ **Append** rows to the end of a sheet, **clear** ranges
- ✅ **RAW by default** - phone numbers, IDs, leading `+`/`=`/`0` stored literally (no `#ERROR!`)
- ✅ **Choosable read output** - `kv` / `tsv` / `csv` / `json` with correct escaping
- ✅ **Full A1 range space** - columns past Z (AA, AB, …) handled correctly
- ✅ **Clean output** - data on stdout, `[ERROR]`/`[INFO]` on stderr, no JVM/AOT noise on warm runs
- ✅ **Allowlist permissions** managed from the CLI (`grant` / `revoke` / `listperms`)
- ✅ **Encrypted OAuth tokens** (AES-256-GCM)
- ✅ **Portable fat JAR** - single executable with all dependencies

## Installation

1. Build: `mvn clean package -pl spreadsheet_cli -am`
   - This also **pre-warms the JRC AOT cache** so the first real invocation is noise-free
     (skip with `-Dskip.aot.warmup=true` on CI or where the launcher isn't on PATH).
2. Invoke via the launcher on your PATH, e.g. `uskoag-gsheetscli` (a `jr`-based `.exe`/`.jrc`
   pointing at `target/uskoag-gsheetscli.jar`).

## Quick Start

```bash
# 1. Allow a spreadsheet (write implies read)
uskoag-gsheetscli grant --write 1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCd "Test Sheet"

# 2. First run opens a browser for Google OAuth; tokens are cached (encrypted) under
#    %USERPROFILE%\uskoag\gservices\spreadsheet_cli\<your-email>\

# 3. Use it
uskoag-gsheetscli read  1Bv0Yt... Sheet1 A1
uskoag-gsheetscli write 1Bv0Yt... Sheet1 A1 "Hello World"
```

Run `uskoag-gsheetscli --help` for the full, self-contained reference.

## Command Reference

```
uskoag-gsheetscli [-v|--verbose] [--quiet] <command> [args...]
```

| Command | Purpose |
|---------|---------|
| `read <id> <sheet> <range> [<range2> ...]` | Read a cell / range / several ranges; `--formulas`, `--render FORMATTED\|UNFORMATTED\|FORMULA`, `--both` (formula + value), or `--rich` (value+formula+note+color+numberFormat) |
| `query <id> <sheet> "<SQL>"` | Run SQL over the sheet (columns `A,B,C…` + `_row`); `--range`, `--format json\|tsv\|csv` |
| `update <id> <sheet> "<SQL UPDATE>"` | Conditional UPDATE; writes changed cells, returns `old→new` diff; `--dry-run`, `--user-entered`, `--as-date` |
| `inspect <id> <sheet> [<range>]` | Show cell notes + background colors (range optional → whole sheet) |
| `write <id> <sheet> <addr> [<value>]` | Write one cell, or a 2-D block (with a data flag); `--as-date` for real date values |
| `append <id> <sheet> [<startRange>]` | Append rows to the end of a sheet (needs a data flag); `--as-date` |
| `clear <id> <sheet> <range>` | Empty a range |
| `listsheet <id>` | List sheet tabs and their IDs |
| `createsheet <id> <name>` | Add a sheet tab |
| `renamesheet <id> <oldName> <newName>` | Rename a sheet tab |
| `freeze <id> <sheet> [--rows N] [--cols N]` | Freeze header rows/columns (0 unfreezes) |
| `insertcolumn <id> <sheet> <colLetter>` | Insert blank column(s), shifting everything at/after right; `--count N` |
| `insertrow <id> <sheet> <rowNumber>` | Insert blank row(s), shifting everything at/after down; `--count N` |
| `format <id> <sheet> <range> [flags]` | Color/style cells: `--bg`, `--text`, `--bold`, `--italic`, `--align`, `--valign`, `--font-size`, `--wrap` |
| `numberformat <id> <sheet> <range>` | Set number/date rendering format; `--type DATE\|TIME\|DATE_TIME\|NUMBER\|PERCENT\|CURRENCY\|SCIENTIFIC\|TEXT`, `--pattern`, or `--clear` |
| `validate <id> <sheet> <range> (--from-range R \| --list a,b,c)` | Dropdown / data validation; `--warn`, `--clear`; `--colors`/`--color-legend` to bundle in matching conditional-format coloring |
| `highlight <id> <sheet> <range> (--from-range R \| --list a,b,c \| --colors-from-range R)` | Color-code each allowed value (conditional format); `--colors`, `--clear` |
| `filter <id> <sheet> [<range>]` | Turn on the filter funnel buttons on the header row; `--clear` removes; range optional (→ whole sheet) |
| `describeschema <id> [--sheets a,b,...]` | Export a rich per-sheet schema (headers+notes, diversified data sample); `--format json\|md\|text` |
| `grant --read\|--write <id> [name]` | Add a spreadsheet to the local allowlist |
| `revoke <id>` | Remove it from the allowlist |
| `listperms` | Show the allowlist (no Google auth needed) |
| `help` | Show full help |

Global options: `-v`/`--verbose` (logs to stderr), `--quiet` (suppress the `SUCCESS:` line),
`-h`/`--help`.

### Value input (write / append)

- **Default is `RAW`** — values are stored literally. `+91 80 4656 3000`, `007`, and `=text`
  are preserved exactly (no `#ERROR!`, no `tel:` workaround).
- `--user-entered` opts into Google's formula/auto-typing: `=1+1` → `2`, `50%` → `0.5`, dates parsed.
- `--raw` is the default (accepted as an explicit no-op).

### Bulk / 2-D write

The block is written starting at the top-left of `<addr>`. Provide **one** data source:

```bash
uskoag-gsheetscli write ID Sheet1 A1 --tsv-file  grid.tsv      # tab=col, newline=row
uskoag-gsheetscli write ID Sheet1 A1 --csv-file  data.csv      # RFC-4180: commas/quotes/newlines preserved
uskoag-gsheetscli write ID Sheet1 A1 --json-file matrix.json   # [["a","b"],["c","d"]]
uskoag-gsheetscli write ID Sheet1 A1 --tsv  $'a\tb\nc\td'       # inline TSV
uskoag-gsheetscli write ID Sheet1 A1 --json '[["a","b"],["c","d"]]'

uskoag-gsheetscli append ID Sheet1 --csv-file newrows.csv      # same flags; INSERT_ROWS
```

A bulk write prints `SUCCESS: wrote N rows × M cols to Sheet1!A1`; append prints the range
actually written.

### Read output

```bash
uskoag-gsheetscli read ID Sheet1 A1               # single cell -> bare value
uskoag-gsheetscli read ID Sheet1 A1:C2            # default --format kv
uskoag-gsheetscli read ID Sheet1 A1:C2 --format tsv
uskoag-gsheetscli read ID Sheet1 A1:C2 --format csv
uskoag-gsheetscli read ID Sheet1 A1:C2 --format json
uskoag-gsheetscli read ID Sheet1 A1:C3 E1:F2 --format tsv   # batchGet; each range gets a "# Sheet!range" header
```

- `kv` (default): `"A1": "value"` per cell, quotes/backslashes escaped.
- `tsv`/`csv`/`json`: rectangular grid; blank cells preserved positionally.
- Columns past Z work: `read ID Sheet1 Y1:AC2` emits `Y1,Z1,AA1,AB1,AC1,…`.

### Read formulas, not just values

```bash
uskoag-gsheetscli read ID Sheet1 C1 --formulas              # "=SUM(A1:A9)" instead of its computed value
uskoag-gsheetscli read ID Sheet1 A1:C3 --formulas --format tsv
uskoag-gsheetscli read ID Sheet1 C1 --render UNFORMATTED    # superset: FORMATTED (default) | UNFORMATTED | FORMULA
```

- `--formulas` is shorthand for `--render FORMULA`; the two are mutually exclusive.
- A cell with no formula still returns its literal value — only formula cells are affected.

Need both at once, so you're not stuck choosing? `--both` (2 API calls) pairs the formula and the
value per cell — mutually exclusive with `--formulas`/`--render`:

```bash
uskoag-gsheetscli read ID Sheet1 A1:C3 --both --format json   # nested {"formula":..,"value":..} per cell
uskoag-gsheetscli read ID Sheet1 A1:C3 --both --format tsv    # same pair, as compact JSON text per cell
```

`--format json` gets real nested objects; `kv`/`tsv`/`csv` (flat, one-string-per-cell formats)
embed the same pair as a compact JSON string, e.g. `{"formula":"=SUM(A1:A9)","value":"30"}` —
works in every format, including tsv, since that string never contains a literal tab/newline.

### Query — SQL over the sheet

Instead of downloading a whole sheet and filtering agent-side (token-wasteful, imprecise), push the filter/projection/aggregation down as **SQL**. The tab (or `--range`) is loaded into a throwaway in-memory H2 table `t`, your `SELECT` runs against it, and only the result comes back. It is **read-only** — the in-memory copy is discarded after each call, so a query can never write to the real spreadsheet.

- Columns are addressed by their **sheet letter**: `A`, `B`, `C`, … (bare, uppercase). Two-letter names that clash with SQL keywords need quoting, e.g. `"AS"`, `"IN"`.
- `_row` is an extra column carrying the **1-based source sheet row number** — so every result row is traceable back to the sheet.
- Every cell is **text** (stored literally, matching the RAW write philosophy). `CAST` when you need a numeric/date comparison: `WHERE CAST(E AS INT) > 100`.
- **Row 1 (the header) is loaded as data too** — add `WHERE _row > 1` to skip it. (This deliberately defers "header not on row 1 / data not from row 2" handling; the agent is expected to know the schema.)
- Full SQL: `WHERE`, `GROUP BY`, `HAVING`, `COUNT`/`DISTINCT`, `ORDER BY`, `LIMIT`, self-joins, subqueries.

```bash
# contacts not yet emailed (blank "Emailed" column D), traceable by _row
uskoag-gsheetscli query ID Contacts "SELECT _row, A, C FROM t WHERE _row>1 AND D=''"

# duplicate emails — the classic dedupe check
uskoag-gsheetscli query ID Contacts "SELECT C, COUNT(*) n FROM t WHERE _row>1 GROUP BY C HAVING COUNT(*)>1"

# distinct recipients, cheap TSV
uskoag-gsheetscli query ID Contacts "SELECT DISTINCT C FROM t WHERE _row>1" --format tsv

# load only part of the sheet; _row stays aligned to the real sheet rows
uskoag-gsheetscli query ID Contacts "SELECT _row, A FROM t" --range A2:D
```

- `--format json` (default): array of objects keyed by result column — self-describing, always carries `_row`, and numeric columns (`_row`, `COUNT(*)`, …) render as JSON numbers. Best for agent correctness.
- `--format tsv` / `csv`: a header row of column names, then rows — cheapest to parse.
- SQL / column errors surface as one concise `[ERROR]` line (exit 1), e.g. `Column "Z" not found`.

### Update — conditional SQL `UPDATE` (write side)

The write counterpart to `query`. The tab (or `--range`) loads into the same in-memory `t` table; you run a SQL **`UPDATE`**; then **only the cells whose value actually changed** are written back to the real sheet, and you get an `old→new` diff for every one. It needs **write** permission.

Three things make it safe for agents:

- **The `WHERE` clause is the safety gate** — nothing matches, nothing is written.
- **`--dry-run`** computes and prints the full diff but writes *nothing* — preview, then re-run without the flag to commit.
- **The `old→new` diff is the self-heal record** — to undo, run the reverse for each change: `update ID Sheet "UPDATE t SET <col>='<old>' WHERE _row=<row>"`.

```bash
# preview: who would get marked emailed, and what changes
uskoag-gsheetscli update ID Contacts "UPDATE t SET D='yes' WHERE C='a@x.com'" --dry-run

# commit: mark row 5 emailed with a date, in one call
uskoag-gsheetscli update ID Contacts "UPDATE t SET D='yes', E='2026-07-15' WHERE _row=5"
```

Output (json default; `tsv` also available) reports `affected` (rows the `WHERE` matched), `changedCells`, `written` (0 on a dry-run), `dryRun`, and `changes[]` of `{row, cell, col, old, new}`:

```json
{"affected":2,"changedCells":2,"written":2,"dryRun":false,
 "changes":[{"row":2,"cell":"D2","col":"D","old":"","new":"yes"},
            {"row":4,"cell":"D4","col":"D","old":"","new":"yes"}]}
```

- Only `UPDATE` is accepted — `append` adds rows, `clear`/`write` cover the rest; `DELETE`/`INSERT`/etc. are rejected with a one-line error.
- Changed cells are written **RAW** by default (literal, matching `write`); add `--user-entered` for formula/auto-typing.
- **Upsert pattern**: run `update`; if `affected` is `0`, the key wasn't present → `append` the new row. Two transparent steps, each self-describing.

### Inspect — notes & cell colors

`read` only returns *values*. `inspect` surfaces the two things agents otherwise can't see —
**cell notes** and **background colors** — from a single `spreadsheets.get` (one API call for both).

```bash
uskoag-gsheetscli inspect ID Sheet1                 # whole sheet: notes + colors
uskoag-gsheetscli inspect ID Sheet1 A1:Q240         # restrict to a range
uskoag-gsheetscli inspect ID Sheet1 --notes-only    # just notes
uskoag-gsheetscli inspect ID Sheet1 --colors-only --format json
```

- **Notes** are listed one per cell: `O120: <note text>` (newlines escaped to `\n` in text mode).
- **Colors** are merged into maximal rectangles and grouped by color, so the output mirrors how the
  sheet *looks* rather than emitting one line per cell:

  ```
  #fff2cc light yellow: A1:E1, A120:Q139, A141:Q173
  #00ff00 green: A2:Q6, C7:Q26
  #ff0000 red: B7, B16, B27
  ```

  Each color shows its `#rrggbb` hex plus a nearest-swatch human name. The default white
  (unfilled) background is omitted.
- `--format text` (default) or `json`. JSON shape:
  `{"sheet","range","notes":[{"cell","note"}],"colors":[{"hex","name","ranges":[…]}]}`.

### Freeze, format, validate — formatting for consumer-built sheets

These three write-side commands cover what a generated workbook usually needs: a frozen,
styled header row, and columns constrained to an allowed set of values (a dropdown) whose
vocabulary lives in a separate sheet rather than being hard-coded per cell.

```bash
# Stylized, frozen header row
uskoag-gsheetscli format ID Sheet1 A1:Z1 --bg "#4a86e8" --text "#ffffff" --bold
uskoag-gsheetscli freeze ID Sheet1 --rows 1

# Dropdown whose allowed values come from another sheet (edit the vocabulary in one place)
uskoag-gsheetscli validate ID Sheet1 C2:C --from-range "'Allowed Values'!A2:A"

# Dropdown from an inline list instead, flagging (not rejecting) bad input
uskoag-gsheetscli validate ID Sheet1 D2:D --list contacted,replied,bounced --warn

# Remove a validation rule
uskoag-gsheetscli validate ID Sheet1 C2:C --clear
```

- **`freeze`** — `--rows N` and/or `--cols N` (at least one required); `N=0` unfreezes.
- **`format`** — only the flags you pass are touched (a fields mask), everything else on the
  cell is left alone: `--bg`/`--text` (`#rrggbb`), `--bold`/`--italic`, `--align left|center|right`,
  `--valign top|middle|bottom`, `--font-size N`, `--wrap`.
- **`validate`** — `--from-range` is the required pattern for a consumer-editable vocabulary
  (`ONE_OF_RANGE`); `--list` is a secondary inline convenience (`ONE_OF_LIST`). Default is
  **strict** (invalid input rejected); `--warn` flags it instead. Both show a dropdown in Sheets.

### Highlight — color-code each value so it reads at a glance

`validate` constrains *what* can go in a cell; `highlight` makes *which value is there* visible
without opening a note or reading the text closely — a conditional-format rule per distinct value
(exact-text match, background color only).

```bash
# Colors auto-assigned from a built-in palette (light green/yellow/red/blue/...), cycled if needed
uskoag-gsheetscli highlight ID Sheet1 C2:C --from-range "'Allowed Values'!A2:A"

# Explicit colors, one per value in order
uskoag-gsheetscli highlight ID Sheet1 C2:C --list contacted,replied,bounced --colors "#d9ead3,#fff2cc,#f4cccc"

# Mirror a legend's OWN current cell colors onto a data range — no --colors list to keep in sync by hand
uskoag-gsheetscli highlight ID Sheet1 C2:C --colors-from-range "'Allowed Values'!A2:A"

# Remove the highlight rule(s) previously added on this exact range
uskoag-gsheetscli highlight ID Sheet1 C2:C --clear
```

- **Value/color source — pick one**: `--from-range`/`--list` (paired with `--colors` or the auto
  palette), or `--colors-from-range` (reads BOTH the value and its background color directly off
  the legend's actual cells — color the legend once, by hand or with the bundled `validate` mode
  below, then stamp that same mapping onto any number of data ranges).
- Prints the `value=color` pairs actually used, so an auto-assigned or derived palette is visible.
- **Safely re-runnable**: re-running `highlight` on the same range replaces its rules instead of
  stacking duplicates — so after a revision (a new enum value added, colors changed), just run the
  same command again. New rules are appended after any *other*, pre-existing conditional formatting
  on the sheet, which is left untouched. `--clear` only removes rules that cover the exact range given.

### Bundling validate + highlight — one command that stays in sync

Adding an enum value later shouldn't mean hunting down every place its color is defined. `validate`
can bundle the coloring in directly:

```bash
uskoag-gsheetscli validate ID Sheet1 C2:C --from-range "'Allowed Values'!A2:A" \
    --colors "#d9ead3,#fff2cc,#f4cccc" --color-legend
```

This one call: (1) sets up the dropdown, (2) color-codes the data range `C2:C`, and (3) color-codes
the **legend cells themselves** (`'Allowed Values'!A2:A`) with the same mapping — so the legend
visually declares "this text = this color" and the data range matches it.

**Why this handles revisions cleanly**: `--from-range` validation is *live* — Sheets re-evaluates
the actual current legend contents on every keystroke, so a new row added to the legend is valid in
the dropdown immediately, no CLI action needed. Only the *coloring* is a static snapshot. So the
whole revision workflow is: add the new value to the legend, extend `--colors` by one, re-run the
exact same `validate` command — the color rules on both the legend and the data range refresh (old
ones replaced, not stacked), and the dropdown never needed touching at all.

- `--colors` alone colors just the data range; add `--color-legend` to also color the legend
  (requires `--from-range` — there's no legend location for a plain `--list`).
- Omit `--colors`' value and keep `--color-legend` for the auto palette on both.
- Not allowed together with `--clear` — clearing the dropdown and clearing highlight rules are kept
  as separate, deliberate actions (`validate --clear` / `highlight --clear`), so removing one never
  silently removes the other.

### Filter — turn on the header-row filter buttons

`filter` enables the "Create a filter" funnel buttons on a header row (Data → Create a filter in the
Sheets UI). It turns the *option* on — it doesn't set any specific column criteria — so users can then
filter/sort interactively.

```bash
uskoag-gsheetscli filter ID Sheet1               # whole sheet: funnel buttons on row 1
uskoag-gsheetscli filter ID Sheet1 A1:H          # over a range; the range's first row is the header
uskoag-gsheetscli filter ID Sheet1 --clear       # remove the filter
```

- The range is optional — omit it to filter the whole sheet (buttons on row 1). A range's **first row**
  is treated as the header the funnels sit on.
- A sheet holds **exactly one** basic filter, so re-running `filter` just replaces it — it never stacks.
- Pairs naturally with `freeze --rows 1` for a header row that both filters and stays pinned while scrolling.

### Number format — how a value renders (date, currency, percent, ...)

`format` covers color/style; `numberformat` covers the separate concept of how the underlying
value **renders** — as a date, currency, percentage, etc.

```bash
uskoag-gsheetscli numberformat ID Sheet1 B2:B --type DATE                     # default pattern yyyy-mm-dd
uskoag-gsheetscli numberformat ID Sheet1 D2:D --type CURRENCY --pattern "$#,##0.00"
uskoag-gsheetscli numberformat ID Sheet1 B2:B --clear
```

- `--type` is required (or `--clear`): `DATE`, `TIME`, `DATE_TIME`, `NUMBER`, `PERCENT`, `CURRENCY`,
  `SCIENTIFIC`, `TEXT`. Each has a sensible built-in default pattern; `--pattern` overrides it.
- Setting a number format only changes **rendering** — it doesn't convert a literal string into a
  real numeric/date value. For dates specifically, see `--as-date` below.

### Writing real dates — `--as-date`

A date typed as a plain string (even with `--user-entered`) doesn't always parse the way you'd
expect, and the default `RAW` mode never parses it at all — it just renders as text. `--as-date`
sidesteps the ambiguity entirely: it parses the value in Java, writes it as a proper Sheets date
**value** (not a string), and applies a matching number format, all in one call.

```bash
uskoag-gsheetscli write  ID Sheet1 B2 "2026-07-24" --as-date                       # ISO date (default)
uskoag-gsheetscli write  ID Sheet1 C2 "07/24/2026" --as-date --date-format MM/dd/yyyy
uskoag-gsheetscli write  ID Sheet1 D2 "14:30:00"   --as-date --date-type TIME
uskoag-gsheetscli append ID Sheet1 --tsv $'2026-07-24' --as-date
uskoag-gsheetscli update ID Sheet1 "UPDATE t SET E='2026-07-24' WHERE _row=5" --as-date
```

- `--date-type DATE` (default), `TIME`, or `DATE_TIME`; `--date-format <pattern>` overrides the ISO
  default (a `java.time.DateTimeFormatter` pattern, e.g. `MM/dd/yyyy`).
- `write`/`append` take one declared, homogeneous block of dates — a value that doesn't parse fails
  loudly (blank cells are simply skipped, not an error). `update`'s SQL `SET` can touch several
  unrelated columns in one statement, so a non-date value there passes through unchanged instead.
- Ignores `--raw`/`--user-entered` — `--as-date` writes a typed numeric value directly, bypassing
  that choice entirely.

### Insert rows / columns

```bash
uskoag-gsheetscli insertcolumn ID Sheet1 C          # insert 1 blank column at C, shifting C+ right
uskoag-gsheetscli insertrow    ID Sheet1 3          # insert 1 blank row at row 3, shifting 3+ down
uskoag-gsheetscli insertrow    ID Sheet1 3 --count 5
```

Existing formulas/validation/formatting shift along with the inserted rows/columns.

### Read — rich per-cell detail (`--rich`)

`--both` pairs formula+value; `--rich` goes further, returning everything `inspect` can see (notes,
background color) **plus** value/formula/number-format, per cell, in one call:

```bash
uskoag-gsheetscli read ID Sheet1 A1:C3 --rich --format json
```

`--format json` gives real nested objects; `kv`/`tsv`/`csv` embed the same object as compact JSON
per cell, e.g. `{"value":"2026-07-24","color":"#fff2cc","numberFormat":{"type":"DATE","pattern":"yyyy-mm-dd"}}`
— fields with nothing to report (no note, white/no-fill background) are simply omitted. Mutually
exclusive with `--formulas`/`--render`/`--both`.

### Describe schema — export a spreadsheet's shape for an AI agent

Feeding a whole spreadsheet's raw contents to an AI agent floods its context; `describeschema`
exports just the **shape** instead — each sheet's header row (with any cell notes as column
"comments") plus a small, diversified sample of data rows, not just the first few.

```bash
uskoag-gsheetscli describeschema ID                                          # every sheet, default sampling
uskoag-gsheetscli describeschema ID --sheets Contacts --sample-size 8 --format md
uskoag-gsheetscli describeschema ID --sheets Contacts --header-row 2 --col-end BQ
```

- `--sheets a,b,...` (default: all, in tab order); `--sample-rows N` (100) rows analyzed,
  `--sample-size N` (5) rows actually sampled, `--uniqueness-col A` (A) diversifies the sample —
  one random row per distinct value in that column, topped up with random extras if there aren't
  enough distinct values, so the sample is never a mechanical first-N-rows slice.
- `--truncate N` (1024) caps each sampled cell's length, keeping both the head and tail of the
  value around a `... (sample truncated, total length N characters)` marker.
- `--header-row`/`--data-start-row`/`--col-start`/`--col-end` override the per-sheet defaults
  (header row 1, data from row 2, columns auto-detected from the header) — only valid when
  `--sheets` names exactly **one** sheet.
- `--notes "..."` attaches a free-text note about the spreadsheet to the export.
- `--format json` (default), `md`, or `text`. 3 API calls total, regardless of sheet count.
- Ports the design (not the code) of a separate reference tool built for the same purpose.

## Output Contract

- **stdout**: command data only — cell values, sheet list, or a single `SUCCESS:` line.
- **stderr**: `[ERROR]` (always) and `[INFO]` (only with `-v`). Errors are one concise line;
  add `-v` for the stack trace. No JVM/AOT bootstrap noise on warm runs.
- **Encoding**: both streams are pinned to **UTF-8**, so notes/values containing en-dashes,
  curly quotes, arrows, or non-Latin scripts survive intact (not mangled to `?`/`0x96` via the
  Windows cp1252 default).

```bash
uskoag-gsheetscli read ID Sheet1 A1 > result.txt    # stdout is clean and pipe-friendly
```

## Configuration / Permissions

A spreadsheet must be on the allowlist before use. Prefer the CLI (`grant`/`revoke`/`listperms`),
which validates and de-dupes the XML. You can still edit it by hand:

**Location**: `%USERPROFILE%\uskoag\gservices\spreadsheet_cli\SpreadsheetCli.xml`

```xml
<SpreadsheetCli>
    <permissions>
        <allowRead  name="Readonly">spreadsheet_id_1</allowRead>
        <allowWrite name="My Work Sheet">spreadsheet_id_2</allowWrite>
    </permissions>
</SpreadsheetCli>
```

- `<allowRead>` → read-only; `<allowWrite>` → read **and** write; `name` is an optional label.
- Auto-created with example comments on first run if missing.

## Security

- OAuth tokens encrypted with **AES-256-GCM**; key derived from the app key `uskoag-spreadsheet-cli-key-2025`.
- Tokens stored under `~/uskoag/gservices/spreadsheet_cli/<email>/tokens_<hashed-key>/`.
- `credentials.json` (OAuth client) is plaintext and less sensitive; user tokens are encrypted.

## Troubleshooting

- **"No permission"** — add the ID with `grant`, and use `--write` for write operations.
- **Browser doesn't open for OAuth** — check firewall; default port 8888; ensure
  `credentials.json` is in `~/uskoag/gservices/spreadsheet_cli/<your-email>/`.
- **Build** — `mvn clean package -pl spreadsheet_cli -am` (add `-Dskip.aot.warmup=true` if the
  `uskoag-gsheetscli` launcher isn't on PATH).

## Technical Details

- **Dependencies (bundled)**: Google Sheets API v4, Google OAuth Client + Jetty,
  uskoag-gservices-oauth, uskoag-gservices-sheets, Apache HTTP Client, Gson, Guava,
  H2 (in-memory SQL engine for `query`).
- **JAR**: `target/uskoag-gsheetscli.jar` · **Main class**: `uskoag.gservices.SpreadsheetCli`.
- **A1/column math** lives in `CellRange` (bijective base-26) with full multi-letter support.

## Test Spreadsheet

- **URL**: https://docs.google.com/spreadsheets/d/1AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCd/
- **Sheet**: `Sheet1`

## Related PRPs

- **PRP-01 (this project)**: agentic feature set — clean output, RAW default, bulk write,
  full column math, read formats, append/clear/grant. See `prp/`.
- **PRP-02 (this project)**: `inspect` command — cell notes + background colors (merged ranges),
  UTF-8 output. See `prp/`.
- **PRP-03 (this project)**: `query` (read-only SQL over the sheet) + `update` (conditional SQL `UPDATE`
  that writes only changed cells and returns an `old→new` diff, with `--dry-run`), both via in-memory H2
  (`A,B,C…` + `_row`). Serves Gmail↔sheet reconciliation (who's emailed / not yet / duplicates). See `prp/`.
- **PRP-04 (this project)**: `read --formulas`/`--render`/`--both`; `freeze`/`format`/`validate`/`highlight`/`filter`,
  including `highlight --colors-from-range` (derive a color mapping from an already-colored legend),
  `validate --colors`/`--color-legend` (bundle matching conditional-format coloring into the same
  call, staying in sync when the allowed set changes), and `filter` (turn on the header-row filter
  buttons) — formatting features needed by a consumer project's generated leads/CRM workbook. See `prp/`.
- **PRP-06 (this project)**: `numberformat` (rendering format for a cell/range); `--as-date` on
  `write`/`update`/`append` (write real date/time values, not literal strings); `insertrow`; `read --rich`
  (value+formula+note+color+numberFormat per cell); `describeschema` (rich per-sheet schema export —
  headers+notes, diversified data sampling — ported from a separate reference tool's design). See `prp/`.
- Foundational: encrypted OAuth token storage; per-service uskoag-gservices sub-projects.

## License

Part of the uskoag-gservices project.
