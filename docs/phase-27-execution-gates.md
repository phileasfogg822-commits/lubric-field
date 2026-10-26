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

Status: NOT STARTED.

## Package 27.4 — Delivery scheduling + challan

Status: NOT STARTED.

## Package 27.5 — Delivery execution

Status: NOT STARTED.

## Package 27.6 — Targets

Status: NOT STARTED.

## Package 27.7 — Salesperson dashboard + shell wiring

Status: NOT STARTED.

## Package 27.8 — Gate

Sweep, recount, carryovers.

Status: NOT STARTED.

27.1 ACCEPTED 2026-10-26 on `92330ca` (trio Native 40350666056 / Compose 40350666044 / Segmented 40350666046, all green, first head): route model in domain/sales, 9 JVM tests.

27.2 ACCEPTED 2026-10-26 on `e7e6d1e` (trio Native 40354231584 / Compose 40354231594 / Segmented 40354232019, all green, first head): fast order as DRAFT sales order mapping in feature/sales, 8 JVM tests.
