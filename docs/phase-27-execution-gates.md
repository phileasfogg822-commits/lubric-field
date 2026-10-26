# Phase 27 — Field Sales / Routes / Collections / Delivery — execution gates

Base: `phase-26-pos-cashier-ops` @ `2e1be15`.

## Package 27.1 — Route model

Day route, ordered stops, visit actions (§109).
`RouteDay` / `RouteStop` / `VisitAction` / `RouteOps` in
domain/sales, pure and immutable like the 26.1 cart:
create, add stop (position follows max + 1), record
visit (throws on unknown stop, overwrite allowed for
corrections), stops in order, pending/completed split.
Visit times (§110) and persistence are later packages;
GPS stays off by default per §110. 9 JVM tests.

Status: ACCEPTED on `92330ca`.

## Package 27.2 — Fast order

Fast order (§40) as a DRAFT sales order mapping in
feature/sales, mirroring the 26.2 hold mapper:
`FastOrderMapper` maps a cart to a SALES_ORDER
document plus lines through the existing record
machinery — no new table, no whitelist change.
Forward mapping requires a named party (the §40
header always shows a customer; walk-in fast orders
are the counter's job, so cartToOrder throws on
WalkIn); resume stays lenient like hold. Same limits
as hold: no tax through the mapping, number/date
empty until posting, balance/credit-limit display
left to the 27.7 screen fed by 22.x outstanding.
8 JVM tests.

Status: ACCEPTED on `e7e6d1e`.

## Package 27.3 — Collections

Field collection (§111) in domain/payment:
`FieldCollection` (party, amount, mode, reference,
receipt number, date) + `FieldCollectionOps`
(validate positive amount; allocate). Allocation
reuses the 22.1 core: a party/amount overload was
added to `PaymentAllocation` and the existing
payment-based entry now delegates to it, so
oldest-first, same-party, collectible-only,
currency-matched, clamped-at-outstanding behavior
stays single-sourced. Outstanding stays derived,
never stored. Posting (payment row, INSERT_ONLY cash
movement, journal) is a later package / shell
decision. Record named `FieldCollection`, not
`Collection`, to avoid shadowing Kotlin's stdlib
type. 8 JVM tests.

Status: ACCEPTED on `80ffe15`.

## Package 27.4 — Delivery scheduling + challan

Delivery scheduling (§46) + challan (§47) in
domain/sales: `DeliverySchedule` (order, party,
expected date, route id, address, vehicle, driver,
notes) and `DeliveryChallan` (number, order, party,
shipping address, date, lines) built from a schedule
via `DeliveryOps.challanFor`, so the challan can
never disagree with its schedule on customer, order
or address. Vehicle and driver stay plain strings
(no typed ids exist in domain/common); the challan
number stays a plain string, empty until assigned —
no DC prefix invented, since NumberingRecord's set
has no DC. Execution outcomes are 27.5's job, not
this package's. 6 JVM tests.

Status: ACCEPTED on `86f8c7b`.

## Package 27.5 — Delivery execution

Delivery execution (§161–§163) in domain/sales:
`DeliveryExecution` (challan id, outcome, reason,
proof) + `DeliveryExecutionOps` (delivered, failed,
partial). Outcome is three states — DELIVERED,
FAILED, PARTIAL — with reasons exactly the §163
list; a reason is required for FAILED/PARTIAL and
forbidden for DELIVERED. Proof (§162) stays
optional and URI-only for signature and photo.
The "today's deliveries" list is a 27.7 screen
concern, not a stored list. 8 JVM tests.

Status: ACCEPTED on `639a185`.

## Package 27.6 — Targets

Status: NOT STARTED.

## Package 27.7 — Salesperson dashboard + shell wiring

Status: NOT STARTED.

## Package 27.8 — Gate

Sweep, recount, carryovers.

Status: NOT STARTED.

27.1 ACCEPTED 2026-10-26 on `92330ca` (trio Native 40350666056 / Compose 40350666044 / Segmented 40350666046, all green, first head): route model in domain/sales, 9 JVM tests.

27.2 ACCEPTED 2026-10-26 on `e7e6d1e` (trio Native 40354231584 / Compose 40354231594 / Segmented 40354232019, all green, first head): fast order as DRAFT sales order mapping in feature/sales, 8 JVM tests.

27.3 ACCEPTED 2026-10-26 on `80ffe15` (trio Native 40358418939 / Compose 40358419003 / Segmented 40358419094, all green, first head): field collection record + allocation via 22.1 core in domain/payment, 8 JVM tests.

27.4 ACCEPTED 2026-10-26 on `86f8c7b` (trio Native 40362103584 / Compose 40362103363 / Segmented 40362103596, all green, first head): delivery scheduling + challan in domain/sales, 6 JVM tests.

27.5 ACCEPTED 2026-10-26 on `639a185` (trio Native 40365928276 / Compose 40365928172 / Segmented 40365928245, all green, first head): delivery execution outcomes + proof in domain/sales, 8 JVM tests.
