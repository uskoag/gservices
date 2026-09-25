# uskoag-gdocscli — agent guide

`uskoag-gdocscli --help` is the full reference. This page is the working method.

## Reading

1. `uskoag-gdocscli -e <account> tabs <doc>` — always first on a document you have not seen. Documents have tabs, tabs nest, and a plain read of the Docs API returns only the first tab; this tool reads them all.
2. `uskoag-gdocscli -e <account> read <doc> [--tab <sel>]` — markdown by default. Each tab opens with `<!-- tab n/N id=t.xxx title="..." path="Parent / Child" -->`; split on that line, not on headings. `--html` keeps colour, size and font; `--json` is the API's own structure; `--docx --out f.docx` is Drive's export of the whole document.

Suggested edits read inline as `{++inserted++}` / `{--deleted--}` (md) or `ins`/`del` (html); `--suggestions accepted|original` gives the clean versions.

## Editing — the loop

1. `read` the tab, and copy anchors (the opening words of a paragraph) from that output.
2. Write the edits as a plan (`{"edits":[...]}`, see `--help`), or use `replace` / `insert` / `delete` for a single edit.
3. Run WITHOUT `--apply`. Read the dry run: every anchor resolved to one paragraph, each replace shown before/after, and each inserted paragraph's list and span styles.
4. Run again with `--apply`. It must print `VERIFIED`. `VERIFY FAILED` (exit 2) means the document does not look as planned: stop and report it rather than patching by hand.
5. `read` the tab once more.

## Rules that are not obvious

- An anchor that matches zero or several paragraphs stops the run and lists the candidates; lengthen it rather than guess.
- One plan = one atomic batch pinned to the revision just read. If anyone edits the document in between, nothing applies; read again and re-run.
- Several paragraphs inserted after one anchor belong in ONE insert op (a heading and what follows it), each with its own `like`.
- Without `like`, a new paragraph copies the anchor, list membership included. `like` copies another paragraph: a heading-style line, body text, or a bullet from another list (which starts a new list with the same glyph, shared by adjacent bullets copying it).
- Inserted text takes `**bold**` and `*italic*` only.
- A font equal to the paragraph style's own reads back as unset; that is Google normalising it, not a lost style.
- Docs edits are ordinary edits to the wallet (all reversible through version history); the first write to a document asks once. `uskoag-walletcli policy quiet --tier write` for a long run.
- Run it from Bash, not PowerShell.
