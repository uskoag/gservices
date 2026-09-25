# uskoag-gdocscli

Google Docs from the command line, through the USK OAG GServices Wallet (the tool holds no credential).

- **Read** a document with every document tab, nested tabs included, as markdown, simple html, docx, or the Docs API's own json.
- **Edit** it surgically: `replace`, `insert`, `delete`, or `apply --plan` for many edits in one atomic batch. Anchors are the opening words of a paragraph; new paragraphs copy the style and list of the paragraph they follow, or of a named `like` paragraph. Dry run by default; `--apply` pins the batch to the revision just read and reads the result back to verify it.

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

- docx export is always the whole document; the API cannot export one tab.
- Image links in md/html are Google's temporary content URIs.
- A bullet copied from another list starts a new list: the API cannot add a paragraph to an existing list except by splitting a paragraph already in it.
