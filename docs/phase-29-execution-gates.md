# Phase 29 — Notifications / Reminders / Automation — execution gates

Base: `phase-28-search-export-backup` @ `e67bd56`.

## Package 29.1 — Notification model

Notification model (§104, §165) in domain/business:
`NotificationType` (exactly the §104 ten types, in
spec order) + `Notification` (id, type, title,
detail, date, read flag) + `NotificationOps`
(unread filter/count, by-type, mark read,
mark-all-read, newest-first by date then id).
Pure model — no repository, no system posting,
no money or stock movement. One fix head:
`f82914d` (test helper gained the `detail`
parameter the mark-read test passes). 8 JVM tests.

Status: ACCEPTED on `f82914d`.

## Package 29.2 — Reminder schedule rules

Reminder schedule rules (§103) in domain/business:
`ReminderRule` (id, partyId, per-customer enabled
flag) + `ReminderSchedule` (cadence −3, 0, +3,
+7, then every 7 days; `isReminderDate`,
`nextReminderAfter` strictly after a given
date, `isOverdue` strictly past due). Pure date
math over ISO text; malformed input throws
rather than computing silently. 8 JVM tests.

Status: ACCEPTED on `5055ed4`.

## Package 29.3 — Reminder templates

Reminder templates (§102) in domain/business:
`ReminderTemplate` (id, body) + `ReminderTemplateOps`
(`placeholders` extraction, `render` substituting
`{name}` values; unsupplied placeholders left
intact, never dropped; spaced braces literal).
Templates are data, not hardcoded strings —
§102 says editable. 8 JVM tests.

Status: ACCEPTED on `0a893a7`.

## Package 29.4 — Automation evaluators

Status: NOT STARTED.

## Package 29.5 — Notification center screens + shell wiring

Status: NOT STARTED.

## Package 29.6 — Gate

Sweep, recount, carryovers.

Status: NOT STARTED.

29.1 ACCEPTED 2026-10-27 on `f82914d` (trio Native 40443611765 / Compose 40443611613 / Segmented 40443611671, all green, after fix head f82914d): notification model in domain/business, 8 JVM tests.

29.2 ACCEPTED 2026-10-27 on `5055ed4` (trio Native 40448372443 / Compose 40448372510 / Segmented 40448372507, all green, first head): reminder schedule rules in domain/business, 8 JVM tests.

29.3 ACCEPTED 2026-10-27 on `0a893a7` (trio Native 40452584648 / Compose 40452584722 / Segmented 40452584646, all green, first head): reminder templates in domain/business, 8 JVM tests.
