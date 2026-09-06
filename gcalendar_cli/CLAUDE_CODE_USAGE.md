# Using uskoag-gcalendarcli from Claude Code

## TL;DR

```bash
EM=you@x.com

# See what calendars exist, then list events on one (bounded window, default next 30 days)
uskoag-gcalendarcli calendars -e $EM --json 2>/dev/null
uskoag-gcalendarcli list -e $EM --calendar primary --days 14 --json 2>/dev/null

# Create an event and invite people (Google emails the invite; this tool never sends mail itself)
uskoag-gcalendarcli create -e $EM --summary "Sync" --start 2026-09-10T14:00:00+03:00 \
  --end 2026-09-10T14:30:00+03:00 --attendees alice@x.com,bob@x.com

# Modify, respond, delete
uskoag-gcalendarcli update <eventId> -e $EM --add-attendees carol@x.com
uskoag-gcalendarcli respond <eventId> accepted -e $EM
uskoag-gcalendarcli delete <eventId> -e $EM

# Share a calendar (destructive tier in the wallet — granting visibility is the standing-risk direction)
uskoag-gcalendarcli share <calendarId> dave@x.com --role reader -e $EM
```

**Two rules, same as every other gservices CLI here:**
1. Pass `--email/-e` and **nothing else for credentials** — the USK OAG GServices Wallet holds every
   token over loopback. **The wallet must be running and unlocked**; "no valid wallet grant" means
   unlock it and re-run, not a bug to work around.
2. Add `2>/dev/null` to drop `[INFO]`/`[ERROR]` logs, and `--json` when parsing output.

## Recurring events — read this before `list`

`list` defaults to `singleEvents=false` (the Calendar API's own default), so an infinitely-recurring
series is **one row**, carrying its `recurrence` RRULE — never one row per future occurrence. Pass
`--expand` to see individual instances in the window instead; each expanded row carries
`recurringEventId` so it is still visibly the same series, not an unrelated event. Always bounded by
`--from`/`--to` (or `--days`, default 30 from now) — there is no "list everything" mode, by design.

## Dates

`--start`/`--end`/`--from`/`--to` take either:
- `2026-09-10` — an all-day date, or
- `2026-09-10T14:00:00+03:00` — RFC 3339 date-time with an explicit offset.

`--timezone <IANA tz>` on `create`/`update` sets the event's own timezone field (affects how it displays
to attendees in other zones); it does not change what you typed for `--start`/`--end`.

## Invites and notifications

- `create --attendees a,b` and `update --add-attendees a,b` / `--remove-attendees a,b` manage the
  attendee list; Google sends the actual invite/update email, this tool does not.
- `--send-updates all|externalOnly|none` controls who gets notified (default `all`); `--no-notify` is
  shorthand for `--send-updates none` — use it for a quiet edit nobody needs to be told about.

## Calendars vs your calendar list — a real trap

`calendars` lists your **calendar list** (owned + subscribed, `users/me/calendarList`). Two very
different-looking operations both use the word "delete" and do NOT mean the same thing:
- `calendar-delete <id>` destroys the calendar itself and **every event on it**. Irreversible, no trash.
- Removing a subscribed calendar from your own sidebar (unsubscribing) is a different, much smaller
  operation that this CLI does not currently expose — `calendar-delete` is real deletion, always.

## Sharing

`share`/`unshare`/`acl` manage a calendar's ACL. `share` defaults to `--role reader`; pass `--domain`
to share with an entire Google Workspace domain (the argument becomes a domain, not an email) or
`--public` to make it public. `unshare` accepts either the ACL ruleId or the email it was granted to.

## What NOT to do

- ❌ Don't expect an unbounded `list` — pass `--from`/`--to`/`--days`, or accept the 30-day default.
- ❌ Don't pass `-k`/`--app-key`. It doesn't exist here either; the wallet holds the credentials.
- ❌ Don't confuse `calendar-delete` (destroys the calendar) with anything about your own calendar list.
- ❌ Don't use `2>&1` (merges logs into results). Use `2>/dev/null`.

## First run / re-login

Credentials live in the wallet:

```bash
uskoag-walletcli status                                  # running? unlocked?
uskoag-walletcli org add <org> --file credentials.json   # once per organisation
uskoag-walletcli login <email> --org <org>               # browser consent
```

This tool requests the full `calendar` scope (not just `calendar.events`), because calendar-level
operations here — creating/deleting calendars, sharing (ACL) — need it; `calendar.events` alone would
cover only the event commands.
