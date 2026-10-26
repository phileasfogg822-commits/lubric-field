# Phase 26 execution gates — POS & Cashier Ops

Base: `phase-25-documents-printing` @ `2145ecf` (Phase 25
gate head). Branch `phase-26-pos-cashier-ops`.
Spec: §147 POS Mode, §148 Hold Sale, §149 Walk-In
Customer, §150 Quick Cash Payment, §151 Cash Drawer,
§152 Shift System, §153 Price Override, §154 Discount
Approval. Prep: `agent_notes/phase-26-prep.md` (goal
workspace, not the repo).

## Package 26.1 — POS cart model

Pure, immutable cart in domain/sales (`PosCart.kt`):
`PosCart` (id, party, lines), `PosCartLine` (id, itemId?,
description, quantity, rate, discount), `PosParty`
(WalkIn default per §149 / Named), `PosLineDiscount`
(Percent / Fixed), and `PosCartOps`: addLine (scan
merges same item + rate + discount into quantity,
different rate/discount appends, unidentified lines never
merge), updateQuantity / removeLine (positive quantities
only, unknown ids rejected), applyDiscount /
removeDiscount, setParty, lineAmount (pre-tax gross
rounded 2dp HALF_UP minus discount, clamped at zero),
subtotal (sum of line amounts; empty cart is zero in the
given currency; mixed currencies fail on Money.plus).
No totals stored, no tax in the cart (invoice concern at
conversion), no persistence (hold in 26.2 saves a DRAFT
sales document). 15 JVM tests.

Status: NOT STARTED.

## Package 26.2 — Hold sale

`PosHoldMapper` in feature/sales (§148): a held bill IS
a DRAFT invoice document, saved through the existing
record machinery (saveDocument / saveLine) and resumed by
loading it back — no new table, no whitelist change, no
hold marker. Cart id becomes the document id; lines keep
ids and take positions in cart order; WalkIn maps to a
null partyId, Named to its id value; rates and discount
values are plain BigDecimals in the document layer, so
currency is a mapping parameter, not a stored field;
number and date stay empty until posting. Tax components
do NOT survive hold (the cart carries no tax; tax is
re-derived at invoice conversion) — documented limit.
8 JVM tests, including a full hold/resume round-trip.

Status: NOT STARTED.

## Package 26.3 — Quick cash payment

Denominations + change.

Status: NOT STARTED.

## Package 26.4 — Cash drawer

Open (opening cash) / close (expected / actual /
difference).

Status: NOT STARTED.

## Package 26.5 — Shifts

Open / close + shift summary (sales / returns / cash /
card / expenses).

Status: NOT STARTED.

## Package 26.6 — Overrides

Price override + discount approval via the
manager-override mechanism.

Status: NOT STARTED.

## Package 26.7 — POS counter screen + shell wiring

Scan / Search / Quantity / Discount / Hold / Pay /
Print actions; Pay leads to 25.x print.

Status: NOT STARTED.

## Package 26.8 — Gate

Sweep, recount, carryovers.

Status: NOT STARTED.

Status: ACCEPTED on `094f20f` (trio Native 40306531248 / Compose 40306531352 / Segmented 40306531259, all green; verified first-hand). Package head `88d4d6d` took one fix head: `094f20f` (test-only — inventory's adjustFlowPostsAMovement now waits for the "Adjustment posted" node itself before asserting it, after that test failed three consecutive Compose attempts on `88d4d6d` with zero inventory changes in the diff; the test waited for the data write but asserted the UI text with no wait, so a slow emulator's recomposition lost the race). Native and Segmented were already green on `88d4d6d`. Tests added: 15 JVM (cart model).
