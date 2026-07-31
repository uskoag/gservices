# Using uskoag-gmailcli from Claude Code

## TL;DR

```bash
JAR=gmail_cli/target/uskoag-gmailcli.jar

# Read / search (suppress logs, prefer --json for parsing)
java -jar $JAR search "is:unread newer_than:3d" -e you@gmail.com -k APPKEY --json 2>/dev/null
java -jar $JAR read   <messageId>               -e you@gmail.com -k APPKEY 2>/dev/null

# Draft only (this tool NEVER sends)
java -jar $JAR draft -e you@gmail.com -k APPKEY --to bob@x.com -s "Subject" --body-file body.txt

# Labels: create, then apply to messages (auto-create if new). Threads with --threads.
java -jar $JAR label-create "Triage" -e you@gmail.com -k APPKEY 2>/dev/null
java -jar $JAR label "Triage" <msgId> <msgId> --create-missing -e you@gmail.com -k APPKEY --json 2>/dev/null
java -jar $JAR modify <msgId> --add "Triage" --remove INBOX -e you@gmail.com -k APPKEY 2>/dev/null   # archive + label
```

**Two rules:**
1. Always pass `--email/-e` and `--app-key/-k` (no env vars are read).
2. Add `2>/dev/null` to drop `[INFO]`/`[ERROR]` logs, and `--json` when you want to parse output.

## Why `--json`

Without it, output is human-formatted text (good for a person). For programmatic use, `--json`
emits a stable structure:

```bash
java -jar $JAR search "from:alice" -e you@gmail.com -k APPKEY --json -n 5 2>/dev/null
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
JAR=gmail_cli/target/uskoag-gmailcli.jar
EM=you@gmail.com; K=APPKEY

# 1. find the message
java -jar $JAR search "subject:invoice has:attachment" -e $EM -k $K --json -n 5 2>/dev/null
# 2. read it (grab its id from step 1)
java -jar $JAR read 18f3... -e $EM -k $K --json 2>/dev/null
# 3. download an attachment (attachmentId comes from the read output)
java -jar $JAR attachment 18f3... ANGj... -e $EM -k $K --out ./invoice.pdf 2>/dev/null
# 4. draft a reply (does NOT send — user reviews/sends in Gmail)
java -jar $JAR draft -e $EM -k $K --reply-to 18f3... --body-file reply.txt
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
- ⚠️ **One-time `reauth`**: label writes need the `gmail.modify` scope. An older read-only login 403s on the first write — run `reauth -e <email> -k <key>` once to re-consent (opens a browser), then it works.

## What NOT to do

- ❌ Don't expect a `send` command — by design there is none. Drafts are reviewed/sent by the user in Gmail.
- ❌ Don't use `2>&1` (it merges logs into results). Use `2>/dev/null`.
- ❌ Don't pass huge `-n` values — each result costs one extra metadata fetch. Keep it ≤ ~25.
- ❌ Don't pass arbitrary label colors — only Gmail's fixed palette is accepted, and `--text-color`/`--bg-color` must be set together.

## First run / re-login

If a command reports missing `credentials.json`, place the Google Cloud OAuth *Desktop app* JSON at
`~/uskoag/gservices/gmail_cli/<email>/credentials.json`, then run `auth` once interactively (it opens
a browser). A changed/forgotten app-key simply forces a fresh `auth`. After upgrading to a build with
label writes, run `reauth` once per account to grant the new `gmail.modify` scope.
