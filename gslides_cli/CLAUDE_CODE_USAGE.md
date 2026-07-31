# Using uskoag-gslides from Claude Code

## TL;DR

```bash
uskoag-gslides grant --write DECK_ID "My deck"      # once per deck
uskoag-gslides describe DECK_ID                      # ALWAYS do this first
uskoag-gslides text DECK_ID title_1 --set-html="<b>New title</b>"
uskoag-gslides export DECK_ID --slide 3 --quick --max-width 1200 --out check.png
```

Then **read `check.png`** and fix what you see. That loop is the whole point.

## Output contract

- **stdout** carries only the command's data, or a single `SUCCESS:` line.
- **stderr** carries only `[ERROR]` (always) and `[INFO]` (only with `-v`).
- Exit code conveys success; `--quiet` drops the `SUCCESS:` line.
- The AOT cache is pre-built at `mvn package` time, so warm runs emit no bootstrap noise. No `2>/dev/null` needed.

## The one thing to internalise about addressing

A spreadsheet cell address is derivable — `D7` exists whether or not anyone wrote to it. **A slide element address is not.** An element exists only because something created it, and it carries an opaque server id like `g1165df7f5a6_3_285`.

So:

- **Call `describe` before touching anything.** You cannot guess element ids.
- **When you create anything, pass `--id NAME`.** That is what makes the deck addressable, your commands re-runnable, and your cleanup exact. Name things `slide3_title`, `hero_image`, `footer_rule`.

**Id rules (the API rejects otherwise, and dots look natural but are invalid):** 5–50 characters, first character one of `[a-zA-Z0-9_]`, the rest `[a-zA-Z0-9_-:]`. **No dots** — `slide3.title` fails. The CLI validates client-side and suggests a legal form, so you get a useful message rather than an API 400.

## Do not compute character indices

`style DECK elem@12:19 --bold` works, but offsets shift on every edit, and recomputing them is where an agent goes wrong.

**Use HTML instead — it needs no indices at all:**

```bash
uskoag-gslides text DECK body_1 --set-html="<b>Held:</b> the petition is <i>dismissed</i>."
uskoag-gslides text DECK body_1 --set-html-file=body.html    # long or unicode text
```

Supported: `b/strong`, `i/em`, `u`, `s/strike/del`, `a href`, `br`, `p`, `div`, `h1`-`h6`, `ul`/`ol` + `li`, and `style="color: ; font-size: ; font-family: ; text-align: "`.

Reach for `style`/`para` with explicit `@S:E` only for surgical edits to text that already exists — and run `runs DECK elem` first to get the real ranges.

Long text through PowerShell quoting is a known failure. Prefer `--set-html-file=` / `--set-file=` for anything with Devanagari, em-dashes, quotes, or newlines.

## Verifying visually — the part that has no textual substitute

The API will not tell you that text overflowed its box, that white text landed on a pale photo, or that three boxes are misaligned. None of it is in the response. You have to render and look:

```bash
uskoag-gslides export DECK --slide 5 --quick --max-width 1200 --out s5.png
```

- `--quick` uses the `getThumbnail` API: capped near 1600px but **no rate-limit sleep**, so it is fast. Use it for iteration.
- `--max-width` downscales locally. What you pay to look at an image scales with its dimensions, so cap it.
- Drop `--quick` for the full-resolution `/export/png` endpoint when you need real output. It sleeps `--delay` (default 3000ms) between slides because Google rate limits it — a 70-slide render is minutes, not seconds.

## Editing an existing deck — the strongest use case

```bash
uskoag-gslides describe DECK                                  # find the element
uskoag-gslides runs DECK body_7                               # see its runs
uskoag-gslides text DECK body_7 --replace="2019=>2024"        # surgical, format-preserving
uskoag-gslides export DECK --slide 7 --quick --out s7.png     # verify
```

Text edits go through the existing element and never delete-and-recreate its box, so geometry and untouched run formatting survive. Two consequences worth knowing: a `--set`/`--set-html` clears the four character toggles (bold/italic/underline/strike) across the whole element before applying what your HTML asked for, so only what you marked bold ends up bold; size, font and colour are deliberately left to inherit from the placeholder or theme.

## Authoring a deck

```bash
DECK=$(uskoag-gslides deck new "Case briefing" | head -1)     # auto-granted write
uskoag-gslides page add $DECK --id cover --layout BLANK
uskoag-gslides background $DECK cover --color "#0B1D3A"
uskoag-gslides create text $DECK cover --id cover.title --at 0.8,1.9 --size 8.4,1.2 \
    --html "<span style='color:#FFFFFF;font-size:34pt'>Case briefing</span>"
uskoag-gslides export $DECK --slide 1 --quick --out cover.png
```

Geometry is **inches** everywhere. A 16:9 deck is 10 × 5.625 in.

New decks are 16:9 and that cannot be changed — `presentations.create` ignores every field but the title, and the API exposes no request to set page size. Use `deck new "..." --copy-of TEMPLATE_ID` for anything else.

## Images: local paths just work

The Slides API can only ingest a **publicly fetchable URL**. This tool handles that for you — pass a local path anywhere an image is accepted and it gets uploaded to Drive and shared read-only-by-link automatically. So `krea-ai.exe` output goes straight in:

```bash
uskoag-gslides create image $DECK 2 --id hero --src ./generated/hero.png --cover
uskoag-gslides imagebg $DECK 3 ./photo.jpg --scrim 0.5      # full-bleed + legibility scrim
uskoag-gslides imagebg $DECK 3 --undo                       # removes exactly what it added
uskoag-gslides image $DECK hero --src ./v2.png              # swap content, keep geometry
```

`--cover` sizes an image to its own aspect and lets it overflow the page, because the API stretches an image to its box and would otherwise distort it.

## Templating

```bash
uskoag-gslides fill $DECK "{{CASE}}=Bhojshala HP#1" "{{DATE}}=2026-07-30"
uskoag-gslides fill $DECK --map values.json
```

Whole-deck `replaceAllText` in one call. It prints per-key occurrence counts and an `[ERROR]` line for any key that matched nothing, so a silent no-op is visible.

## Batch and dry-run

Every mutating verb sends one `batchUpdate`, so a multi-op `text` call is one round trip. `--dry-run` prints the requests it would send and sends nothing.

## Errors

```
[ERROR] PERMISSION DENIED: no write permission for <id> -- add it with: uskoag-gslides grant --write <id> "<name>"
[ERROR] NOT FOUND (404): Requested entity was not found.
[ERROR] no such element: title_9
```

`-v` adds the stack trace.

## Allowlist

Every deck must be granted before use; a deck created by `deck new` is granted automatically.

```bash
uskoag-gslides grant --write DECK_ID "My deck"
uskoag-gslides listperms
uskoag-gslides revoke DECK_ID
```

Config: `%USERPROFILE%\uskoag\gservices\gslides_cli\GSlidesCli.xml` (hand-editable; XML so entries can be commented out).

## Boundary against the other tools

- Deck-level **PDF / PPTX** export is `uskoag-gdrivecli get` — it exports the *file*. This tool exports *slides*.
- General Drive downloads are `uskoag-gdrivecli`'s too.
- Image generation is `krea-ai.exe` or codex; this tool consumes their output.

## What this tool cannot see

Worth knowing before trusting a generated deck:

- **Text overflow is invisible to the API.** There is no measured-text call and no post-layout bounding box. Spilling or auto-shrunk-to-unreadable text is only detectable by rendering and looking.
- **Element overlap** is computable from geometry but is not yet checked (planned).
- **Aesthetic quality** is not something the primitives supply. They make placement possible, not tasteful.
