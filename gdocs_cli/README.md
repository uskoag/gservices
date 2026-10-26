# uskoag-gdocscli

Google Docs from the command line, through the USK OAG GServices Wallet (the tool holds no credential).

- **Read** a document with every document tab, nested tabs included, as markdown, simple html, docx, or the Docs API's own json.
- **Edit** it surgically: `replace`, `insert`, `delete`, or `apply --plan` for many edits in one atomic batch. Anchors are the opening words of a paragraph (`--occurrence N` picks one of several identical ones; `delete` also takes whole tables, pictures and page breaks in the range, and `--to-end`); new paragraphs copy the style and list of the paragraph they follow, or of a named `like` paragraph, or force a fresh bullet list with `list: bullet` when there is nothing to copy from, with tight spacing via `spacing: tight`. Dry run by default; `--apply` pins the batch to the revision just read and reads the result back to verify it.

- **Suggest** instead of edit: `--suggest` on any edit lands it as a tracked suggestion, with an optional `--note` posted on it. `accept` / `reject` settle suggestions.
- **Converse** in comment threads: `comments` / `suggestions` (with `--awaiting` for what a person asked and nobody answered), `comment add|reply|resolve|reopen|edit|delete|unsign`, and `read --comments` for the text and its threads in one read. Everything the tool posts is signed `AI(<model>): `. These use Google's Docs API Developer Preview.
- **Pictures**: `images`, `image insert`, `image replace` (from a local file or a URL); `image resize` and `image crop` on a picture already in the document (both delete-and-reinsert under the hood — the API has no in-place resize or crop — so the object gets a new id).
- **Fill a new document** from simple markdown with `write`, after `uskoag-gdrivecli new doc` has created it in the right folder.
- **Import a whole Word document** with `import <doc> --from <file.docx|Google Doc> [--at-end|--after "<paragraph>"]`: the converted document is replayed paragraph by paragraph into a tab, with styles, highlights, links, continuous numbered lists, tables (merged cells and nested tables included), pictures, page breaks and footnotes, then read back and compared with the source.
- **Links**: `link` sets or removes a hyperlink on existing text, including a link to another tab of the same document (`--to-tab`) or to a heading (`--to-heading`); `toc delete` removes a native table of contents; `[text](url)`, `[text](tab:<sel>)` and `[text](heading:<start>)` work in `write`, `insert` and plans.
- **Tabs**: `tab add` (a child tab with `--parent`; many at once from `--titles-file`, in one batch), `tab rename`, `tab move`, `tab delete` (`--with-children` for a tab that has them). Same dry-run / `--apply` / VERIFIED discipline as the edit verbs. Google nests tabs at most 3 levels deep.
- **Footnotes** (Google Docs has no separate endnote): `footnote add --find "<text>" --text "<md>"` creates one right after that text; `footnote edit --footnote <sel> --text "<md>"` replaces a footnote's whole body; `footnote delete --footnote <sel>` removes it. `<sel>` is the `[^n]` number `read` already shows, or the footnote id.
- **History**: `history` lists the saved versions (who, when); `read --revision <n>` reads the document as it was at ANY revision number, not only the saved ones; `diff` reports exactly what changed between two versions, word-level inside a paragraph, plus moves and markup-only changes, grouped by tab and heading; `diff --steps` walks every revision in a range, edit by edit. Versions are cached locally, since they never change.

```
uskoag-gdocscli -e <account> tabs <doc-url-or-id>
uskoag-gdocscli -e <account> read <doc> --tab 2 --html --out tab2.html
uskoag-gdocscli -e <account> apply <doc> --plan edits.json            # dry run
uskoag-gdocscli -e <account> apply <doc> --plan edits.json --apply
uskoag-gdocscli -e <account> tab add <doc> --parent "25 questions" --titles-file titles.txt --apply
```

`uskoag-gdocscli --help` is the reference; `CLAUDE_CODE_USAGE.md` is the working method for an agent.

## Build

`mvn install` in this module (the `docs` library first if it changed). The shaded CLI is `target/uskoag-gdocscli.jar`, launched by `uskoag-gdocscli.exe` + `.jrc` in cmdtools. The thin jar is also a library, `io.github.uskoag:uskoag-gservices-gdocs_cli`.

## Limits

- Earlier versions come from Drive's per-revision markdown export (the Docs API reads only the present). It flattens tabs (they are split again by their title lines) and carries no colour, font or size, so a change of only those is invisible to `diff`. Google paces the export (HTTP 429); the tool backs off and retries, so a first walk over a hundred uncached versions takes minutes, the second none.
- docx export is always the whole document; the API cannot export one tab.
- Image links in md/html are Google's temporary content URIs.
- A bullet copied from another list starts a new list: the API cannot add a paragraph to an existing list except by splitting a paragraph already in it.
- **Nested and numbered lists** (PRP 18). Nesting is a real list level, not typed characters: the paragraph text carries leading tabs when `createParagraphBullets` runs and Docs consumes them as the nesting level, so every position used after that request has the tabs already gone. `write` reads markdown lists (`- `, `1. `, indentation, and `(a)` / `a.` / `(i)` under a numbered item) and makes ONE list with levels, so numbering continues after the sub-points. `insert` / plan paragraphs take `list` (`bullet`|`number`), `level` (0..8) and `preset`; the `list` verb converts existing paragraphs in place (typed marker removed, level from `--level` or `--auto-level`); `read --md` writes the nesting and the document's own numbering back. Docs has no `(a)` preset: the default is `NUMBERED_DECIMAL_ALPHA_ROMAN` (1. a. i.), `NUMBERED_DECIMAL_ALPHA_ROMAN_PARENS` gives 1) a) i). A list always counts from 1.
- **import** (PRP 20), measured on a 107-page, 1,100-paragraph legal notice (30 pictures, 6 tables, one nested, 625 list paragraphs): Google's docx conversion is the reader, so nothing is hand-parsed. Docs makes lists only from presets, so a source list's glyphs that no preset has come out as the nearest preset (a level written "(1)" comes out "a."), and the dry run and the final report say which lists. A source list interrupted by other paragraphs stays ONE list over its extent, so its numbering runs on; the paragraphs inside it are taken out or made into their own lists afterwards. Docs reads leading tabs relative to the fewest in the range (measured), so a stretch of nested items borrows a neighbour with none. A table's borders are not in the API's JSON, so they are read from Google's docx export of the same document. The converted document's picture links last about 30 minutes, so they are fetched again just before the pictures go in; nothing is uploaded or shared. Not carried: bookmarks, comments, drawings and charts, page size and margins (the target document owns its page). A table of contents becomes plain entries whose links point at the copied headings.
