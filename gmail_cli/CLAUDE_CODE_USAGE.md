# Using uskoag-gmailcli from Claude Code

## TL;DR

```bash
# Read / search (suppress logs, prefer --json for parsing)
uskoag-gmailcli search "is:unread newer_than:3d" -e you@gmail.com --json 2>/dev/null
uskoag-gmailcli read   <messageId>               -e you@gmail.com 2>/dev/null

# Draft only (this tool NEVER sends)
uskoag-gmailcli draft -e you@gmail.com --to bob@x.com -s "Subject" --body-file body.txt

# Draft with images IN the body plus a file attached
uskoag-gmailcli draft -e you@gmail.com --to bob@x.com -s "Comparison" \
  --body-file body.html --html --inline ./a.jpg --inline ./b.jpg --attach ./report.pdf

# Delete drafts (labelling the draft's MESSAGE as TRASH does NOT remove the draft)
uskoag-gmailcli draft-delete <draftId> <draftId> -e you@gmail.com

# Labels: create, then apply to messages (auto-create if new). Threads with --threads.
uskoag-gmailcli label-create "Triage" -e you@gmail.com 2>/dev/null
uskoag-gmailcli label "Triage" <msgId> <msgId> --create-missing -e you@gmail.com --json 2>/dev/null
uskoag-gmailcli modify <msgId> --add "Triage" --remove INBOX -e you@gmail.com 2>/dev/null   # archive + label
```

**Three rules:**
1. Pass `--email/-e` and **nothing else for credentials**. `--app-key`/`-k` is GONE — the USK OAG
   GServices Wallet holds every token and this tool reaches it over loopback. A secret on argv lands in
   shell history and AI transcripts, which is why it was removed. **The wallet must be running and
   unlocked**; "wallet is locked" is not an error to work around, it means unlock it and re-run.
2. Add `2>/dev/null` to drop `[INFO]`/`[ERROR]` logs, and `--json` when you want to parse output.
3. Verify what you built. `read <msgId> --json` lists `attachments[]`; `read <msgId> --raw` shows the
   MIME headers. For anything with images, check both before telling the user it is ready.

## Images inside the HTML body — `--inline`

Reach for this when the picture has to be **in** the message (a comparison table, a screenshot beside the
argument) rather than an attachment the reader may never open. `--attach` puts a file below the message;
`--inline` puts an image in the body.

Reference each image by its **plain file name**, prefixed `cid:`. The path is not the reference:

```bash
--inline ./shots/before.jpg     ->     <img src="cid:before.jpg">
```

```html
<table cellpadding="0" cellspacing="0" border="0" style="border-collapse:collapse;max-width:620px;">
<tr>
  <td width="50%" valign="top" style="padding:8px;border:1px solid #ccc;">
    <img src="cid:before.jpg" width="290" style="display:block;width:100%;max-width:290px;height:auto;border:0;">
    <div style="font:13px/1.45 Arial,sans-serif;padding-top:8px;">Caption for this cell.</div>
  </td>
  <td width="50%" valign="top" style="padding:8px;border:1px solid #ccc;">
    <img src="cid:after.jpg" width="290" style="display:block;width:100%;max-width:290px;height:auto;border:0;">
    <div style="font:13px/1.45 Arial,sans-serif;padding-top:8px;">Caption for this cell.</div>
  </td>
</tr>
</table>
```

**Gmail renders inline images in exactly one MIME shape, and the tool builds it for you:**
`multipart/mixed` → `multipart/related` (html first, then each image with a **bracketed** `Content-ID`
and `Content-Disposition: inline`) → attachments. The trap, which fails silently with no error and no
pictures, is that the wrapper body part of the nested `multipart/related` must **restate** its own
`Content-Type`. Reference implementation: `GmailDocxSender.createEmail_gmailVersion` in
`uskoag-reports/bulk_email_sender` — read it before changing MIME assembly.

- ❌ **Do not use `data:` URIs** as a shortcut. Gmail does not render them for the recipient.
- Resize images to the width you display (560–620px, 30–80KB each). They are base64-encoded into the request.
- Two files with the same base name collide in the `cid:` namespace — rename first.
- Email HTML means **inline styles and tables**. No CSS classes, no stylesheets, no flexbox.

## Why `--json`

Without it, output is human-formatted text (good for a person). For programmatic use, `--json`
emits a stable structure:

```bash
uskoag-gmailcli search "from:alice" -e you@gmail.com --json -n 5 2>/dev/null
```
```json
[
  {
    "id": "18f...",
    "threadId": "18f...",
    "from": "Alice <alice@x.com>",
    "to": "you@gmail.com",
    "date": "Mon, 10 Jun 2026 09:15:00 +0000",
    "subject": "Re: proposal",
    "snippet": "Thanks, looks good …",
    "labels": ["INBOX", "UNREAD"]
  }
]
```

`read --json` adds `body` and an `attachments[]` array (`filename`, `mimeType`, `attachmentId`, `size`).

## Typical Claude Code flow

```bash
EM=you@gmail.com

# 1. find the message
uskoag-gmailcli search "subject:invoice has:attachment" -e $EM --json -n 5 2>/dev/null
# 2. read it (grab its id from step 1)
uskoag-gmailcli read 18f3... -e $EM --json 2>/dev/null
# 3. download an attachment (attachmentId comes from the read output)
uskoag-gmailcli attachment 18f3... ANGj... -e $EM --out ./invoice.pdf 2>/dev/null
# 4. draft a reply (does NOT send — user reviews/sends in Gmail)
uskoag-gmailcli draft -e $EM --reply-to 18f3... --body-file reply.txt
# 5. verify before calling it ready
uskoag-gmailcli read <newMsgId> -e $EM --json 2>/dev/null   # attachments[] present?
uskoag-gmailcli read <newMsgId> -e $EM --raw  2>/dev/null   # Content-ID / inline / multipart correct?
```

## Gmail query cheatsheet (`-q` / `search`)

| Query | Matches |
|---|---|
| `is:unread` / `is:read` / `is:starred` | state |
| `from:alice@x.com` / `to:me` | participants |
| `subject:invoice` | subject words |
| `has:attachment` / `filename:pdf` | attachments |
| `label:work` / `in:inbox` / `in:sent` | location |
| `newer_than:7d` / `older_than:1y` / `after:2026/01/01` | time |
| combine: `from:alice is:unread has:attachment` | AND by default |

## Labels

- `labels` lists them. `label-create`/`label-update`/`label-delete` manage definitions; `label`/`unlabel`/`modify` apply them to messages (or whole threads with `--threads`).
- `<labels>` is comma-separated **names or ids**, case-insensitive; system ids work (`UNREAD`, `STARRED`, `IMPORTANT`, `INBOX`, …). Use `--create-missing` to auto-create added labels.
- Apply runs over many ids in one auth (chunked `gmail.batch()`); a bad id is a per-item error and exit stays `0`. `--json` returns `{count, ok, failed, items[]}`.
- ⚠️ **Writes need the `gmail.modify` scope.** A read-only token 403s on the first write. With the wallet
  holding credentials, re-consent is `uskoag-walletcli login <email> --org <org>` — it requests every
  tool's scopes in one browser round trip. **Google also expires refresh tokens that are never used**, so a
  write token added months ago and never exercised will fail with `invalid_grant / "Token has been expired
  or revoked."` The client-side message for this is the useless `Error writing request body to server` —
  always read `~\uskoag\wallet\wallet.log` for the real cause.

## What NOT to do

- ❌ Don't expect a `send` command — by design there is none. Drafts are reviewed/sent by the user in Gmail.
- ❌ Don't try to remove a draft with `modify --add TRASH`. That labels the underlying **message** and the
  **draft record survives and keeps listing** — which is how superseded drafts end up sitting beside the
  ones meant to be sent. Use `draft-delete`.
- ❌ Don't pass `-k`/`--app-key`. It no longer exists; the wallet holds the credentials.
- ❌ Don't use `2>&1` (it merges logs into results). Use `2>/dev/null`.
- ❌ Don't pass huge `-n` values — each result costs one extra metadata fetch. Keep it ≤ ~25.
- ❌ Don't pass arbitrary label colors — only Gmail's fixed palette is accepted, and `--text-color`/`--bg-color` must be set together.

## First run / re-login

Credentials live in the wallet, so first-run setup is a wallet operation, not a gmailcli one:

```bash
uskoag-walletcli status                                  # running? unlocked?
uskoag-walletcli accounts                                # which accounts, which scope groups, useCount
uskoag-walletcli org add <org> --file credentials.json   # once per organisation
uskoag-walletcli login <email> --org <org>               # browser consent, requests all tool scopes
```

`accounts` is worth reading before you debug anything: a scope group with `useCount: 0` and an old
`addedAt` is a token Google has probably already expired.
