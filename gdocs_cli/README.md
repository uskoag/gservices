# uskoag-gdocscli

Google Docs from the command line, through the USK OAG GServices Wallet (the tool holds no credential).

- **Read** a document with every document tab, nested tabs included, as markdown, simple html, docx, or the Docs API's own json.
- **Edit** it surgically: `replace`, `insert`, `delete`, or `apply --plan` for many edits in one atomic batch. Anchors are the opening words of a paragraph; new paragraphs copy the style and list of the paragraph they follow, or of a named `like` paragraph. Dry run by default; `--apply` pins the batch to the revision just read and reads the result back to verify it.

- **Suggest** instead of edit: `--suggest` on any edit lands it as a tracked suggestion, with an optional `--note` posted on it. `accept` / `reject` settle suggestions.
- **Converse** in comment threads: `comments` / `suggestions` (with `--awaiting` for what a person asked and nobody answered), `comment add|reply|resolve|reopen|edit|delete|unsign`, and `read --comments` for the text and its threads in one read. Everything the tool posts is signed `AI(<model>): `. These use Google's Docs API Developer Preview.
- **Pictures**: `images`, `image insert`, `image replace` (from a local file or a URL).
- **Fill a new document** from simple markdown with `write`, after `uskoag-gdrivecli new doc` has created it in the right folder.
- **History**: `history` lists the saved versions (who, when); `read --revision <n>` reads the document as it was at ANY revision number, not only the saved ones; `diff` reports exactly what changed between two versions, word-level inside a paragraph, plus moves and markup-only changes, grouped by tab and heading; `diff --steps` walks every revision in a range, edit by edit. Versions are cached locally, since they never change.

```
uskoag-gdocscli -e <account> tabs <doc-url-or-id>
uskoag-gdocscli -e <account> read <doc> --tab 2 --html --out tab2.html
uskoag-gdocscli -e <account> apply <doc> --plan edits.json            # dry run
uskoag-gdocscli -e <account> apply <doc> --plan edits.json --apply
```

`uskoag-gdocscli --help` is the reference; `CLAUDE_CODE_USAGE.md` is the working method for an agent.

## Build

`mvn install` in this module (the `docs` library first if it changed). The shaded CLI is `target/uskoag-gdocscli.jar`, launched by `uskoag-gdocscli.exe` + `.jrc` in cmdtools. The thin jar is also a library, `io.github.uskoag:uskoag-gservices-gdocs_cli`.

## Limits

- Earlier versions come from Drive's per-revision markdown export (the Docs API reads only the present). It flattens tabs (they are split again by their title lines) and carries no colour, font or size, so a change of only those is invisible to `diff`. Google paces the export (HTTP 429); the tool backs off and retries, so a first walk over a hundred uncached versions takes minutes, the second none.
- docx export is always the whole document; the API cannot export one tab.
- Image links in md/html are Google's temporary content URIs.
- A bullet copied from another list starts a new list: the API cannot add a paragraph to an existing list except by splitting a paragraph already in it.
