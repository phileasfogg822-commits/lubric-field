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

Automation evaluators (§104) in domain/business:
pure functions over injected snapshots —
low stock (strictly below threshold), payment
overdue (reusing the §103 strict-past-due
rule), stock expiry (today or past, no warning
window invented), purchase due (expected date
reached, unreceived), credit limit warning
(strictly above limit, same currency required
or it throws) — plus an `evaluateAll`
combiner. No repository reads, no posting, no
money or stock movement. 8 JVM tests.

Status: ACCEPTED on `06553fd`.

## Package 29.5 — Notification center screens + shell wiring

Notification center (§165) end to end: a new
`feature/notifications` module with
NotificationCenterScreen (title, unread count,
scrolling rows, empty state, tap-to-mark-read
only when a callback is supplied), a shell
NotificationsMapper (overdue = non-draft INVOICE
with a past due date, party name or document
fallback), and NotificationsContent wiring real
documents into the screen. Registry entry
reuses the existing notifications config key
with a new NOTIFICATIONS_VIEW capability;
the Compose workflow runs the new module's
emulator tests. 11 tests (4 screen emulator,
5 mapper JVM, 2 registry JVM).

Only overdue invoices are wired today: low
stock needs a stock-quantity surface the shell
does not have, and expiry, purchase-due and
credit inputs need their record surfaces —
named follow-ups, not fake rows. Mark-read
state is in-memory only, since derived
notifications are not stored.

Status: ACCEPTED on `449480c`.

## Package 29.6 — Gate

Sweep `e67bd56..73483c5`: 26 files, +1222/−1.
Recount 43 tests (29.1: 8, 29.2: 8, 29.3: 8,
29.4: 8, 29.5: 11), 0 removed. All accepted
heads verified ancestors in order: f82914d,
5055ed4, 0a893a7, 06553fd, 449480c (29.5's
first fix head 346fa1c is also an ancestor;
449480c is itself the test-fix head).

Carryovers: background scheduling execution
(WorkManager/alarms) for §103/§125; system
notification posting via NotificationManager
(permission already declared); reminder
persistence as typed records (the `reminders`
table is already whitelisted); WhatsApp/SMS
reminder delivery (§101 sharing is
documents-only today); low-stock, expiry,
purchase-due and credit center rows (need
their record surfaces — the center shows
overdue invoices only today); in-memory
mark-read (derived notifications are not
stored); stock-quantity surface for low-stock
evaluation at the shell.

Status: GATE PASSED.

29.1 ACCEPTED 2026-10-27 on `f82914d` (trio Native 40443611765 / Compose 40443611613 / Segmented 40443611671, all green, after fix head f82914d): notification model in domain/business, 8 JVM tests.

29.2 ACCEPTED 2026-10-27 on `5055ed4` (trio Native 40448372443 / Compose 40448372510 / Segmented 40448372507, all green, first head): reminder schedule rules in domain/business, 8 JVM tests.

29.3 ACCEPTED 2026-10-27 on `0a893a7` (trio Native 40452584648 / Compose 40452584722 / Segmented 40452584646, all green, first head): reminder templates in domain/business, 8 JVM tests.

29.4 ACCEPTED 2026-10-27 on `06553fd` (trio Native 40456825255 / Compose 40456825285 / Segmented 40456825279, all green, first head): automation evaluators in domain/business, 8 JVM tests.

29.5 ACCEPTED 2026-10-27 on `449480c` (trio Native 40463981958 / Compose 40463981888 / Segmented 40463981874, all green, after fix heads 346fa1c and 449480c): notification center screens + shell wiring, 11 tests.

29.6 GATE PASSED 2026-10-27: sweep e67bd56..73483c5 (26 files, +1222/-1), recount 43 tests, all accepted heads ancestors in order. Phase 29 CLOSED pending confirmatory trio on this gate head.
