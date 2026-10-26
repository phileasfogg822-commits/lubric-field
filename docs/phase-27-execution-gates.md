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

Salesperson targets (§157–§158) in domain/sales:
`SalesTarget` (salesperson, type, period, typed goal
fields) + `TargetOps` (validate, progress). Types
are exactly the §157 list; goal shape is validated
per type (value kinds need a positive Money goal,
count kinds a positive count, product needs an item
plus a positive quantity, brand needs a brand plus
a positive Money goal). Progress is unclamped
(overachievement shows above 100), currency
mismatch throws, achieved values stay parameters,
not stored. 8 JVM tests.

Status: ACCEPTED on `1d7f740`.

## Package 27.7 — Salesperson dashboard + shell wiring

Salesperson dashboard (§108, §158) + shell
wiring: `SalespersonDashboardScreen` (stateless,
rows only for non-null summary values, action
buttons only for non-null callbacks) +
`FieldSalesSummaryMapper` (today's sales from the
totals engine over today's non-draft invoices,
today's orders count; collections and target stay
null until their records persist) +
`FieldSalesContent` (derives the summary from
stored documents; Take Order opens the POS counter
— same gates, interim until a fast-order screen
lands; the other five §108 actions stay absent,
not dead, because the module content signature
carries no navigate parameter). §108 visibility
by construction: no profit, cost, bank, P&L, or
other salespeople's data in the screen's inputs.
Registry entry `fieldsales` (orders config, sales
view). 3 mapper + 3 screen + 2 registry tests.

Status: ACCEPTED on `d48ed33`.

## Package 27.8 — Gate

Sweep `2e1be15..d48ed33`: 26 files, +1822/−4
(the 4 deletions are 27.3's PaymentAllocation
refactor — the original loop moved verbatim into
the new overload, not lost). Recount: 55 tests
added, 0 removed (27.1: 9, 27.2: 8, 27.3: 8,
27.4: 6, 27.5: 8, 27.6: 8, 27.7: 3 mapper + 3
screen + 2 registry). All accepted heads are
ancestors in order: 92330ca, e7e6d1e, 80ffe15,
86f8c7b, 639a185, 1d7f740, d48ed33. Every
package accepted on its first head except none —
all seven landed green first try, no fix heads
in the whole phase.

Carryovers: route/collection/schedule/challan/
execution/target persistence (records exist as
pure models; whitelist + record machinery is a
later package's call); collection posting
(payment row, INSERT_ONLY cash movement, journal)
rides the 22.x pattern when it lands; fast-order
screen (27.2's mapper awaits a salesperson flow);
dashboard actions other than Take Order await
cross-module navigation (content signature has
no navigate parameter — a shell-level follow-up);
collections + target dashboard rows await their
records persisting; GPS for visit tracking stays
off by default (§110, owner decision); camera
scanning still open (Phase 16 decision).

Status: PASSED.

27.1 ACCEPTED 2026-10-26 on `92330ca` (trio Native 40350666056 / Compose 40350666044 / Segmented 40350666046, all green, first head): route model in domain/sales, 9 JVM tests.

27.2 ACCEPTED 2026-10-26 on `e7e6d1e` (trio Native 40354231584 / Compose 40354231594 / Segmented 40354232019, all green, first head): fast order as DRAFT sales order mapping in feature/sales, 8 JVM tests.

27.3 ACCEPTED 2026-10-26 on `80ffe15` (trio Native 40358418939 / Compose 40358419003 / Segmented 40358419094, all green, first head): field collection record + allocation via 22.1 core in domain/payment, 8 JVM tests.

27.4 ACCEPTED 2026-10-26 on `86f8c7b` (trio Native 40362103584 / Compose 40362103363 / Segmented 40362103596, all green, first head): delivery scheduling + challan in domain/sales, 6 JVM tests.

27.5 ACCEPTED 2026-10-26 on `639a185` (trio Native 40365928276 / Compose 40365928172 / Segmented 40365928245, all green, first head): delivery execution outcomes + proof in domain/sales, 8 JVM tests.

27.6 ACCEPTED 2026-10-26 on `1d7f740` (trio Native 40369485633 / Compose 40369485609 / Segmented 40369485623, all green, first head): salesperson targets + progress in domain/sales, 8 JVM tests.

27.7 ACCEPTED 2026-10-26 on `d48ed33` (trio Native 40374116014 / Compose 40374116123 / Segmented 40374116049, all green, first head): salesperson dashboard + shell wiring, 3 mapper + 3 screen + 2 registry tests.

Phase 27 CLOSED 2026-10-26: gate 27.8 PASSED (sweep 26 files +1822/−4, recount 55 tests); confirmatory trio pending on the gate head.
