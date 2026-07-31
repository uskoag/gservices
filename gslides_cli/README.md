# uskoag-gservices-gslides_cli

Google Slides from the command line, built as **primitives plus a few abstractions** rather than as a deck generator. Launcher: `uskoag-gslides.exe`. Agent-facing guide: `CLAUDE_CODE_USAGE.md`.

Artifact `io.github.uskoag:uskoag-gservices-gslides_cli:1.0` · package `uskoag.gservices.slides` · main class `uskoag.gservices.slides.GSlidesCli`

## Why it is shaped this way

`uskoag-sheetcli` works because a sheet has one unambiguous primitive: a **cell**, addressed by A1 notation, carrying value, formula, style, note, number format. Every verb is "get or set attribute X over address Y", nothing is hidden, and the tool consequently serves workflows nobody anticipated when it was written.

This module applies the same discipline to slides. Expose the primitives; add a small number of higher-level abstractions on top so ordinary work is not cumbersome. Primitives give versatility, abstractions give ergonomics, and neither substitutes for the other.

## The structural difference from sheets

In a sheet, **addressing is free** — `D7` exists whether or not anyone wrote to it, derivable from the grid.

In a slide, **addressing must be created and then discovered**. A slide is a free canvas holding a z-ordered element list. An element exists only because something created it, and it carries an opaque server id.

One decision follows, and it is the most important one in the tool: **every create operation accepts a caller-assigned `--id NAME`.** That converts an opaque server namespace into one the caller controls — addressable later, safe to re-run, exactly deletable. Without it every operation needs a discovery round trip first and nothing is reliably repeatable.

Ids are validated client-side against the API's rule — 5–50 characters, first one of `[a-zA-Z0-9_]`, rest `[a-zA-Z0-9_-:]`. **Dots are invalid**, which matters because `slide3.title` is the name everyone reaches for first; `Ids.suggest` offers the legal form in the error.

## The address grammar

```
deck        presentation id, or any Slides URL it can be extracted from
page        1-based slide number, or a pageId
element     objectId
text range  <objectId>@<start>:<end>, half-open
```

`deck 7 title@0:14` is as specific as `Sheet1!D7`.

## The primitive

Common to every element: **identity** (id, type), **geometry** (x, y, w, h, rotation — in inches; EMU is the API's problem), **z-order**. Then per type: shape (shapeType, fill, outline, text), image, video, line, table, group.

Inside text, two independent overlays on the same character sequence:

- **runs** — `[start,end)` carrying bold, italic, underline, strikethrough, size, font, colour, link
- **paragraphs** — carrying alignment, indent, line spacing, spacing above/below, bullets

## Three levels of text input

The layering matters, because index arithmetic is where an agent goes wrong — offsets shift on every edit.

- **Level 0, plain** — `text ... --set="Hello"`. One run, inherits style. Most calls.
- **Level 1, HTML** — `text ... --set-html="<b>Held:</b> <i>dismissed</i>"`. **No indices anywhere.** The authoring path.
- **Level 2, explicit runs** — `style ... elem@12:19 --bold`. Surgical edits to existing text, after reading `runs`.

HTML is the level-1 format rather than markdown because it is already this office's rich-text input (`uskoag-gmailcli --html`), needs no new dependency (jsoup was already here), and unlike markdown can express underline, colour, size and font.

The same three-level shape will recur in a Docs CLI and in rich cell text for sheets — `CellData.textFormatRuns` is the identical model and `uskoag-sheetcli` does not yet expose it. `RichText`/`RichSpan`/`RichPara` are deliberately free of any Google type so they can be promoted to a shared module **when a second write-consumer actually exists**, not before.

## Verbs

**Read** — `describe`, `get` (deck / page / element depth), `runs`, `notes`

**Export** — `export` with `--slide N`, `--start/--end`, `--quick`, `--max-width`, `--delay`, `--video`

**Deck and slides** — `deck new`, `page add|delete|duplicate|move`

**Create** — `create text|box|ellipse|line|image|video`, `delete`, `geom`

**Text** — `text` (ordered ops), `style`, `para`, `notes set`

**Images** — `image`, `image upload`, `background`, `imagebg`

**Templating** — `fill`

**Admin** — `grant`, `revoke`, `listperms`, `reauth`, `help`

`uskoag-gslides help` is the reference; it lives in `src/main/resources/gslides-help.txt`.

## Dual role: CLI and library

The shade plugin does not replace the main artifact when its `finalName` differs from the build `finalName`, so one module ships both:

- `target/uskoag-gservices-gslides_cli-1.0.jar` — thin library jar, installed, importable
- `target/uskoag-gslides.jar` — fat CLI jar, launched by the exe, never installed

**`createDependencyReducedPom` must stay `false`.** The default (`true`) rewrites the installed pom to strip every shaded dependency. That is harmless for a pure CLI — which is why `spreadsheet_cli` and `gmail_cli` leave it alone — but here it would let a library consumer resolve this artifact and then fail to find `google-api-services-slides`.

## Auth

Every verb requests the same union of scopes: `PRESENTATIONS`, `DRIVE_READONLY`, `DRIVE_FILE`.

That is forced, not stylistic. `OAuthToken` keys its token directory by `md5(appKey)` alone and never by scopes, and the Google client library reuses a stored token without re-prompting even after the requested scopes grow. Per-verb scopes would mean the first verb ever run wins and every wider verb then fails with an opaque 403.

Credentials: `~/uskoag/gservices/gslides_cli/<email>/credentials.json`, matching the other CLIs, falling back to the shared `~/uskoag/gdrive_gdocs_auth/` when the per-tool directory has none. App key `uskoag-gslides-cli-key-2026`; `reauth` clears the token when scopes change.

## Build

```
cd C:\user\code\uskoag\gservices
mvn -pl oauth,slides,drive install      # if those changed
mvn -pl gslides_cli install
```

`package` also runs `uskoag-gslides.exe listperms` via maven-antrun to pre-build the JRC AOT cache, so warm runs emit no bootstrap noise. Skip with `-Dskip.aot.warmup=true` where the launcher is not on PATH.

## Known limits

- **Text overflow is invisible to the API.** No measured-text call, no post-layout bounding box. Spilled or auto-shrunk text is only detectable by rendering and looking, which is why `export --slide N --quick` is a first-class verb rather than a convenience.
- **Element overlap** is computable from geometry (`Box.overlaps`) but is not yet enforced. Planned.
- **Page size is not settable.** `presentations.create` ignores every field but the title and the API has no request to change `pageSize`. Use `deck new --copy-of`.
- **Resize is scale, not size.** There is no set-size request; `geom --size` reads the element's unscaled base size and derives the scale factor.
- **Rate limits are real.** The full-resolution export endpoint needs the `--delay` sleep; `--quick` avoids it.

## Boundaries

Deck-level PDF/PPTX export and general Drive downloads are `uskoag-gdrivecli`'s (it exports the *file*; this exports *slides*). Image generation is `krea-ai.exe` or codex; this consumes their output.

## History

Replaces two launchers: `gslides2pngs.exe` (PNG export only, from `uskoag/gdrive/gdrive-gslides2png`, now merged in here) and `uskoag-slidescli.exe`, whose `.jrc` pointed at the spreadsheet CLI's jar and so had never run a slides tool at all.

Grew out of `slides_utils`, a library whose only consumers were JBang scripts in a closed project. That code is not carried forward structurally — what survived is the export logic, the format-preserving text primitives, the two text gotchas documented on `RichRequests.setText`, the Drive upload-and-share step, and the cover-aspect calculation.

Design record: `prp/01-prp-promote_to_gslides_cli.md`.
