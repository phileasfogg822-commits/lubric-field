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

Status: NOT STARTED.

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
