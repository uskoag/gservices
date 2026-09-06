# uskoag-gmailcli — Command-Line Gmail Companion

A command-line tool to **read, search, draft, and label** Gmail, built as a companion for Claude Code
and general email automation. It deliberately **does not send mail** — drafting only.

Part of the `uskoag-gservices` project; reuses the encrypted-OAuth token store from the `oauth` module.

## Features

- ✅ **Read** full messages (headers + decoded body + attachment list) and whole **threads**
- ✅ **Search / list** with native Gmail query syntax (`from:`, `is:unread`, `has:attachment`, `newer_than:7d`, …)
- ✅ **Batch** many ids/queries in one process under a single auth (`read-batch`, `thread-batch`, `search-batch`) via `gmail.batch()`
- ✅ **`--strip-quotes`** — drop quoted reply history from a body, so re-reading a thread doesn't re-read every prior message's text at each level (a token-saver for AI consumers)
- ✅ **Labels** — list, **create / rename / recolor / delete**, and **add/remove** on messages or whole threads (batched)
- ✅ **Draft** creation (plain or HTML, attachments, replies) — **never sends**
- ✅ **Download attachments** to disk
- ✅ **Encrypted OAuth tokens** (AES-256-GCM) keyed by a user-supplied **app-key**
- ✅ **Clean output** — results to stdout, logs to stderr; `--json` for machine parsing
- ✅ **Portable fat JAR** — single executable with all dependencies

## The app-key (not a password)

Every run needs your Gmail **email** and an **app-key**. The app-key is *not* your mail password —
it is an arbitrary secret that derives the encryption key for the OAuth tokens stored on disk
(and the token-folder name). Consequences:

- The same app-key on later runs transparently reuses your saved login.
- A **different or forgotten app-key** just means the tokens can't be found/decrypted, so you simply
  **re-login** (a browser consent). That's by design — there is no app-key recovery.

The app-key is supplied with `--app-key`/`-k`, or — if omitted and a console is available — via a
hidden interactive prompt. **No environment variables are used.**

## One-time setup

1. In Google Cloud Console: create a project, **enable the Gmail API**, and create an **OAuth client
   ID of type _Desktop app_**. Download its JSON.
2. Place that JSON at:
   ```
   %USERPROFILE%\uskoag\gservices\gmail_cli\<your-email>\credentials.json
   ```
   (the CLI prints this exact path and creates the folder for you if it's missing).
3. First real command opens a browser for OAuth consent (default local port 8888). Tokens are then
   saved **encrypted** under `…\<your-email>\tokens_<hash-of-app-key>\`.

> **The client must be _Desktop app_, not _Web application_.** The consent flow uses a loopback
> redirect (`http://localhost:8888/Callback`); Google auto-allows that only for Desktop-app clients.
> A Web-application client (top-level `"web"` key) fails with **`Error 400: redirect_uri_mismatch`**.
> The CLI now detects a wrong-type client up front and tells you to swap it.

### Personal `@gmail.com` (non-Workspace) accounts

A consumer Gmail account **cannot** use a project whose OAuth consent screen is **Internal** (those are
restricted to one Workspace org). For such accounts:

1. Use a project whose consent screen **User Type = External**.
2. Create the **Desktop app** OAuth client there (as above).
3. Add the account (e.g. `you@gmail.com`) as a **Test user** on that consent screen.
4. On first `auth`, the app is "unverified" — click **Advanced → Go to … (unsafe)** to finish consent.

## Build

```bash
mvn clean package -pl gmail_cli -am
# -> gmail_cli/target/uskoag-gmailcli.jar  (fat JAR)
```

Run with `java -jar gmail_cli/target/uskoag-gmailcli.jar <command> …`, or put a small launcher
(`uskoag-gmailcli.bat` calling the JAR) on your PATH.

## Commands

```
uskoag-gmailcli <command> [args] --email <you@gmail.com> [--app-key <key>] [--json] [-v]
```

| Command | Purpose |
|---|---|
| `auth` \| `login` | Run/verify OAuth, then print the profile |
| `reauth` \| `relogin` | Forget the stored token and re-consent — **run once** after upgrading to a build with label writes (see below) |
| `profile` \| `whoami` | Account profile (email, message/thread totals) |
| `listprofiles` \| `accounts` `[--probe]` | List configured accounts (OAuth client type + token-store count); `--probe` verifies live login via `getProfile` — **no browser** |
| `labels` | List all labels (id, name, type) |
| `label-create <name>` | Create a label (`--text-color`/`--bg-color`, `--hide`/`--show`) |
| `label-update <id\|name>` | Rename/recolor/re-show a label (`--name`, colors, `--hide`/`--show`) |
| `label-delete <id\|name>` | Delete a **user** label (system labels are refused) |
| `label <labels> <id…>` | **Add** label(s) to messages (or threads with `--threads`) |
| `unlabel <labels> <id…>` | **Remove** label(s) from messages (or threads with `--threads`) |
| `modify <id…>` | Add and/or remove labels in one call (`--add`, `--remove`, `--threads`) |
| `list` | List recent messages |
| `search "<query>"` | List messages matching a Gmail query (shorthand for `list -q`) |
| `read <messageId>` | Full message: headers, decoded body, attachment list |
| `thread <threadId>` | Every message in a thread |
| `read-batch <id…>` | Batch-read many messages under one auth (positionals, `--ids-file`, or `--ids-stdin`) |
| `thread-batch <id…>` | Batch-read many threads under one auth |
| `search-batch <query…>` | Run many queries under one auth (positionals, `--queries-file`, or `--queries-stdin`) |
| `drafts` | List existing drafts |
| `draft-read <draftId>` | Read one draft |
| `draft` | **Create** a draft (never sends) |
| `draft-delete <draftId…>` | Delete draft(s). **Labelling a draft's message `TRASH` does NOT remove it** — the draft record survives and keeps listing, so use this |
| `attachment <messageId> <attachmentId>` | Download an attachment to disk |

### Global flags

| Flag | Meaning |
|---|---|
| `--email`, `-e <email>` | Gmail account (**required**) |
| `--json` | Machine-readable JSON output |
| `--verbose`, `-v` | Log progress to stderr |

> ⚠️ **`--app-key`/`-k` is GONE.** Credentials now live in the USK OAG GServices Wallet, which this tool
> reaches over loopback and which holds every token itself. Pass `--email` and nothing else. A secret on
> argv lands in shell history and AI transcripts, which is why it was removed. **Sections below that still
> mention an app-key are stale and are being rewritten** — ignore `-k` wherever it appears in this file.

### `list` / `search` flags

| Flag | Meaning |
|---|---|
| `--query`, `-q "<gmail query>"` | Gmail search expression |
| `--label`, `-l <id or name>` | Restrict to a label (id like `INBOX`, or a name) |
| `--max`, `-n <N>` | Max results (default 20) |
| `--include-spam-trash` | Include SPAM and TRASH |
| `--bodies` (alias `--full`) | Inline each message's decoded body (plain; HTML with `--html`), not just the snippet |
| `--strip-quotes` (aliases `--no-quotes`, `--no-history`) | Drop quoted reply history from the body — see [Quoted-history stripping](#quoted-history-stripping---strip-quotes) below |

### `read` flags

| Flag | Meaning |
|---|---|
| `--html` | Prefer the HTML body part over plain text |
| `--headers-only` | Skip the body |
| `--raw` | Dump the raw RFC-822 message to stdout |
| `--strip-quotes` (aliases `--no-quotes`, `--no-history`) | Drop quoted reply history from the body — see [Quoted-history stripping](#quoted-history-stripping---strip-quotes) below |

### Batch commands (`read-batch`, `thread-batch`, `search-batch`)

Resolve many items in **one** process (one JVM start, one auth) instead of one launch per item.

| Flag | Meaning |
|---|---|
| `<id…>` / `<query…>` | Items as positionals (repeatable) |
| `--ids-file <path>` / `--queries-file <path>` | One id / query per line (blank and `#`-comment lines ignored) |
| `--ids-stdin` / `--queries-stdin` | Read items from standard input, one per line |
| `--html`, `--headers-only` | (`read-batch`) as for `read` |
| `-l`, `-n`, `--include-spam-trash`, `--bodies` | (`search-batch`) as for `search` |
| `--strip-quotes` (aliases `--no-quotes`, `--no-history`) | (all three) as for `read`/`list` — see [Quoted-history stripping](#quoted-history-stripping---strip-quotes) below |

Each chunk of ≤100 ids resolves in a single `gmail.batch()` round trip. Output is a JSON **array**
echoing each input id/query (`--json`), preserving input order; a failing item becomes a per-item
`{… "ok": false, "error": …}` entry and does **not** abort the rest. The process still exits `0` as
long as the batch ran — non-zero stays reserved for auth / usage / credential errors.

### Quoted-history stripping (`--strip-quotes`)

A reply's body normally embeds the entire prior message underneath it (Gmail's `On … wrote:`, Outlook's
`From:/Sent:/To:/Subject:` header block, or a `-----Original Message-----` divider). Reading a thread
message-by-message therefore re-reads message 1's text inside message 2, again inside message 3, and so
on — a compounding cost that mainly hurts an AI reading the output. `--strip-quotes` (aliases
`--no-quotes`, `--no-history`) trims each body down to just the newly-typed content before emitting it.

Available on `read`, `thread`, `list`, `search`, `read-batch`, `thread-batch`, and `search-batch` —
anywhere a body is emitted. It only affects the `body` field; headers, snippet, and labels are unchanged.

- **Plain-text bodies**: cut at the first `On … wrote:` / `-----Original Message-----` / Outlook
  `From:`+`Sent:`+`To:` boundary line, then trim any trailing `>`-quoted lines.
- **HTML bodies**: strip `<blockquote>`, `div.gmail_quote`/`gmail_extra`/`gmail_attr`, Outlook's
  `#divRplyFwdMsg`/`#appendonsend`, and Thunderbird's `div.moz-cite-prefix` containers via `jsoup`.
- It's a heuristic tuned for the common **top-posted** reply — a message with no quoted history at all
  passes through unchanged; interleaved or bottom-posted replies may lose some new content past the cut
  point, which is the accepted trade-off for the token savings.

### `draft` flags

| Flag | Meaning |
|---|---|
| `--to <a,b>` | Recipients (comma/semicolon separated) — required unless `--reply-to` supplies it |
| `--cc <…>` / `--bcc <…>` | Carbon-copy / blind recipients |
| `--subject`, `-s <text>` | Subject |
| `--body`, `-b "<text>"` | Inline body |
| `--body-file <path>` | Read body from a file |
| `--body-stdin` | Read body from standard input |
| `--html` | Treat the body as HTML |
| `--attach <file>` | Attach a file, shown below the message (repeatable) |
| `--inline <img>` | Embed an image **inside** the HTML body, referenced `cid:<filename>` (repeatable) — see below |
| `--reply-to <messageId>` | Make it a reply: inherits thread, recipient, `Re:` subject, and `In-Reply-To`/`References` |
| `--thread <threadId>` | Attach the draft to a specific thread |

### Inline images in an HTML body — `--inline`

Use this when a picture has to appear **in** the message (a comparison table, a screenshot beside the
text) rather than as an attachment a reader may never open.

Reference each image from your HTML by its **plain filename**, prefixed `cid:`. The path you pass is
not the reference; only the file name is:

```bash
uskoag-gmailcli draft -e you@gmail.com --to them@x.com -s "Comparison" \
  --body-file body.html --html \
  --inline ./shots/before.jpg \
  --inline ./shots/after.jpg \
  --attach ./report.pdf
```

```html
<table cellpadding="0" cellspacing="0" border="0" style="border-collapse:collapse;max-width:620px;">
<tr>
  <td width="50%" valign="top" style="padding:8px;border:1px solid #ccc;">
    <img src="cid:before.jpg" width="290" style="display:block;width:100%;max-width:290px;height:auto;border:0;">
    <div style="font:13px/1.45 Arial,sans-serif;padding-top:8px;">Before.</div>
  </td>
  <td width="50%" valign="top" style="padding:8px;border:1px solid #ccc;">
    <img src="cid:after.jpg" width="290" style="display:block;width:100%;max-width:290px;height:auto;border:0;">
    <div style="font:13px/1.45 Arial,sans-serif;padding-top:8px;">After.</div>
  </td>
</tr>
</table>
```

**The MIME it builds**, which is the only shape Gmail will render:

```
multipart/mixed                      (only when --attach is also given)
├── multipart/related                (Content-Type restated on the wrapper part — see below)
│   ├── text/html                    the body, first part
│   ├── image/jpeg   Content-ID: <before.jpg>   Content-Disposition: inline
│   └── image/jpeg   Content-ID: <after.jpg>    Content-Disposition: inline
└── application/pdf  Content-Disposition: attachment
```

**Four things Gmail requires. Miss any one and it silently renders nothing:**

1. `Content-ID` wrapped in angle brackets — `<before.jpg>`, not `before.jpg`.
2. `Content-Disposition: inline`, not `attachment`.
3. Each image part declaring its own `Content-Type: <mime>; name="<file>"`.
4. **When `multipart/related` is nested inside `multipart/mixed`, the wrapper body part must restate
   `Content-Type: multipart/related`.** This is the one that is easy to miss and produces no error —
   just a message with no pictures in it.

All four are handled for you. The reference implementation this follows is
`GmailDocxSender.createEmail_gmailVersion` in `uskoag-reports/bulk_email_sender`; read that before changing
any MIME assembly here.

**Do not reach for `data:` URIs instead** — Gmail does not render them for the recipient.

**Notes**
- Content type is derived from the file name, so a `.pdf` arrives as `application/pdf` and a `.jpg` as
  `image/jpeg` rather than everything defaulting to `application/octet-stream`.
- Keep images small (resize to the width you actually display, e.g. 560–620px). Inline images are
  base64-encoded into the request, so a few 30–80KB images is the right order of magnitude.
- Two files with the same base name collide in the `cid:` namespace — rename before passing them.

### `attachment` flags

| Flag | Meaning |
|---|---|
| `--out <path>` | Output path (defaults to the attachment's filename in the current directory) |

### Label commands

Manage label **definitions** (`label-create`/`label-update`/`label-delete`) and **apply** labels to
mail (`label`/`unlabel`/`modify`).

| Flag | Applies to | Meaning |
|---|---|---|
| `--name <new>` | `label-update` | New name for the label |
| `--text-color <hex>` / `--bg-color <hex>` | `label-create`, `label-update` | Label color. **Both required together**, and only values from Gmail's **fixed palette** are accepted (the API rejects others). On `label-update` the missing side is filled from the current color. |
| `--hide` / `--show` | `label-create`, `label-update` | Hide/show the label in the label list **and** message list |
| `--label-list-visibility <v>` / `--message-list-visibility <v>` | `label-create`, `label-update` | Fine-grained visibility (`show`/`hide`/`unread` for the label list; `show`/`hide` for the message list) |
| `<labels>` | `label`, `unlabel` | Comma-separated label names or ids (first positional). Names match case-insensitively; system ids (`INBOX`, `UNREAD`, `STARRED`, `IMPORTANT`, `SPAM`, `TRASH`, `CATEGORY_*`) work too. |
| `--add <labels>` / `--remove <labels>` | `modify` | Labels to add / remove (comma-separated) |
| `--threads` | `label`, `unlabel`, `modify` | Treat the ids as **thread** (conversation) ids and modify every message in each |
| `--create-missing` | `label`, `modify` | Auto-create any **added** label that doesn't exist yet (otherwise an unknown label is an error) |
| `<id…>` / `--ids-file <path>` / `--ids-stdin` | `label`, `unlabel`, `modify` | Target ids as positionals, one-per-line file, or stdin |

Targets are modified via chunked `gmail.batch()` (≤100 per round trip); a bad id becomes a per-item
error and the process still exits `0` (same convention as the read/search batch commands). `--json`
prints `{target, addLabelIds, removeLabelIds, count, ok, failed, items[]}`.

> **Permission / re-auth.** Label writes need the `gmail.modify` scope. A login saved by an earlier
> read-only build keeps its old scopes — the OAuth library reuses it silently, so the first label write
> returns **403 insufficient permission**. Run **`reauth`** once per account to re-consent (it forgets
> the stored token and opens a fresh browser consent), then label writes work. New logins get the
> scope automatically.

## Examples

```bash
JAR=gmail_cli/target/uskoag-gmailcli.jar
EM="you@gmail.com"

# First-time / re-login
java -jar $JAR auth -e "$EM" -k "my-app-key"

# List configured accounts (local-only: client type + token stores)
java -jar $JAR listprofiles

# ...and verify which are actually logged in for a given app-key (no browser)
java -jar $JAR listprofiles --probe -k "my-app-key"

# Search unread from a sender, JSON for parsing
java -jar $JAR search "from:alice@x.com is:unread" -e "$EM" -k "my-app-key" --json -n 10

# Batch: read 100s of messages in one process (one auth), JSON array echoing each id
java -jar $JAR read-batch --ids-file ids.txt -e "$EM" -k "my-app-key" --json > out.json

# Batch: one query per reference-id, bodies inline, results keyed by query
java -jar $JAR search-batch --queries-file queries.txt -e "$EM" -k "my-app-key" --bodies --json > hits.json

# Read one message (clean body to stdout)
java -jar $JAR read 18f3a2b9c... -e "$EM" -k "my-app-key" 2>/dev/null

# Read a whole thread without the compounding quoted history (token-saver for AI consumers)
java -jar $JAR thread 18f3a2b9c... -e "$EM" -k "my-app-key" --strip-quotes --json

# Draft a reply from a file, with an attachment
java -jar $JAR draft -e "$EM" -k "my-app-key" \
  --reply-to 18f3a2b9c... --body-file reply.txt --attach ./report.pdf

# Download an attachment
java -jar $JAR attachment 18f3a2b9c... ANGjdJ8x... -e "$EM" -k "my-app-key" --out ./report.pdf

# --- Labels ---
# One-time re-consent so the saved login gains the gmail.modify scope
java -jar $JAR reauth -e "$EM" -k "my-app-key"

# Create / rename / recolor / delete a label
java -jar $JAR label-create "Legal/Active"            -e "$EM" -k "$K"
java -jar $JAR label-update "Legal/Active" --name "Legal/Open" --text-color "#ffffff" --bg-color "#16a766" -e "$EM" -k "$K"
java -jar $JAR label-delete "Legal/Open"             -e "$EM" -k "$K"

# Apply labels to messages (auto-create the label if new), then mark them read
java -jar $JAR label "Legal/Open,urgent" 18f3... 18f4... --create-missing -e "$EM" -k "$K"
java -jar $JAR unlabel UNREAD 18f3... 18f4...        -e "$EM" -k "$K"

# Label whole conversations (threads) from a file of ids, JSON summary
java -jar $JAR label "Legal/Open" --threads --ids-file thread_ids.txt --json -e "$EM" -k "$K"

# Add and remove in one call
java -jar $JAR modify 18f3... --add "Legal/Open" --remove INBOX -e "$EM" -k "$K"   # i.e. archive + label
```

## Output convention

- **stdout**: results only (and `SUCCESS:` confirmations for `draft`/`attachment`) — pipe-friendly.
- **stderr**: `[INFO]` (with `-v`) and `[ERROR]` lines.
- **UTF-8 everywhere**: on startup both streams are pinned to UTF-8 and (on Windows) the console code
  page is set to 65001, so unicode mail — non-Latin scripts, curly quotes, em-dashes, emoji — renders in
  a terminal and is captured intact when piped, rather than degrading to `?`/mojibake.

Suppress logs with `2>/dev/null` (bash) or `2>nul` (Windows). Use `--json` for structured results.

## Security

- OAuth tokens encrypted with **AES-256-GCM**; key = SHA-256(app-key) (see the `oauth` module / project README).
- `credentials.json` (OAuth client id/secret) is stored in plaintext by design — it is not the sensitive part.
- Scopes requested: `gmail.modify` + `gmail.compose`. `gmail.modify` covers read/search **and** label
  management (create/update/delete labels; add/remove labels on messages/threads) but **not** permanent
  deletion bypassing Trash. **No `gmail.send`** — sending is still not exposed.

## Notes & limitations

- `list`/`search` hydrate result metadata in a single chunked `gmail.batch()` call (≤100 per round trip); `--bodies` additionally fetches full bodies inline. For many separate lookups, prefer `search-batch` / `read-batch` to amortize JVM start + auth across all of them.
- `--strip-quotes` is a heuristic (see [Quoted-history stripping](#quoted-history-stripping---strip-quotes)) — it favors the common top-posted case and can lose content on interleaved/bottom-posted replies.
- `--reply-to` infers recipient/subject/threading from the original message's headers.
- Label **application** (`label`/`unlabel`/`modify`) mutates mail but only via labels — there is still no trashing or permanent deletion. `modify … --remove INBOX` is how you archive; `--add/--remove UNREAD`, `STARRED`, etc. toggle those states.
- Label **colors** must come from Gmail's fixed palette; an arbitrary hex is rejected by the API with a clear error.
