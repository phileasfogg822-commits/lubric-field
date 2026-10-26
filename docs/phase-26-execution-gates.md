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

`QuickCash` in domain/payment (§150): pure tender math.
`settle(total, tendered)` returns `CashSettlement.Paid`
(change, possibly zero) or `CashSettlement.Shortfall`
(remaining due); currency mismatch throws on Money.minus.
`buttonAmounts(total)` returns Exact (the total itself)
followed by the denomination set in the total's currency
— defaulting to the spec's 500 / 1000 / 5000, with the
caller free to supply its own. Denominations below the
total are kept: tapping one is a partial tender, reported
as a shortfall. "Other" is free input on the screen, not
a model value. No posting, no persistence — the payment
rides 22.x machinery at Pay (26.7). 7 JVM tests.

Status: NOT STARTED.

## Package 26.4 — Cash drawer

`CashDrawer` in domain/payment (§151): pure session
math. `openSession` records opening cash; `recordMovement`
appends IN / OUT movements with an optional note;
`expectedCash` is opening plus in minus out; `closeSession`
returns `DrawerClose` (expected / actual / difference,
actual minus expected — positive over, negative short).
Movements are not categorized here (kinds are a 26.5
concern); all amounts share one currency, mixing throws
on Money.plus. No posting, no persistence — postings ride
22.x machinery at shift close (26.5). 7 JVM tests.

Status: NOT STARTED.

## Package 26.5 — Shifts

`ShiftOps` in domain/payment (§152, optional): a shift
owns one 26.4 drawer session plus categorized entries.
`openShift` opens the drawer; `recordEntry` adds SALES /
RETURNS / CARD / EXPENSES entries; `recordDrawerMovement`
delegates to the drawer; `summary` totals each category
with cash read from the drawer (never entered separately,
so cash cannot drift from the count); `closeShift`
reports cash actual and difference. All amounts share the
drawer's currency, mixing throws. No posting, no
persistence — postings ride 22.x machinery at shift close
(26.7 wiring). 6 JVM tests.

Status: NOT STARTED.

## Package 26.6 — Overrides

`ManagerOverride` in domain/sales (§153 price override,
§154 discount approval): `evaluate(request,
hasOverrideCapability)` returns Allowed or
RequiresManagerApproval. No PIN store is invented: the
existing mechanism is capabilities — the discount side
already had `invoice.discount.override`, and 26.6 adds
the missing price side (`invoice.price.override`,
constant + DEFINITIONS entry in domain/user, ALL
derives automatically). The caller maps grants to the
boolean, or a manager approval collected out of band at
26.7. Without it the cart edit must not apply. 4 JVM
tests.

Status: NOT STARTED.

## Package 26.7 — POS counter screen + shell wiring

`PosCounterScreen` in feature/sales (§147): the cart's
lines with quantity controls and per-line discount,
search field with priced results, party row, running
subtotal, and Hold / Pay buttons — stateless over the
cart, every action a callback. Shell wiring in app:
`PosCounterContent` owns the cart state (quantity edits
through 26.1 ops, decrease at 1 a no-op; search filters
the sales item flow to priced name matches, tapping one
adds a unit at its sale rate; Hold saves a DRAFT invoice
through 26.2's mapper and clears the counter; Pay renders
the cart through the 25.x print chain via
`cartToPrintable` — net line amounts, tax and discount
both zero so nothing double-counts, same trap as 25.7).
Registry entry `pos` reuses the orders config key and
the sales view capability (no new flag, no roster
change); the three hand-kept test lists that pin module
order gain POS_ID. Scan is NOT built: the Phase 16 camera
decision is still the owner's call. Payment posting rides
22.x when the held invoice is finalized from sales —
the counter does not post. 6 emulator tests (screen) +
3 JVM (mapper) + 2 JVM (registry).

Status: NOT STARTED.

## Package 26.8 — Gate

Sweep, recount, carryovers.

Status: NOT STARTED.

Status: ACCEPTED on `094f20f` (trio Native 40306531248 / Compose 40306531352 / Segmented 40306531259, all green; verified first-hand). Package head `88d4d6d` took one fix head: `094f20f` (test-only — inventory's adjustFlowPostsAMovement now waits for the "Adjustment posted" node itself before asserting it, after that test failed three consecutive Compose attempts on `88d4d6d` with zero inventory changes in the diff; the test waited for the data write but asserted the UI text with no wait, so a slow emulator's recomposition lost the race). Native and Segmented were already green on `88d4d6d`. Tests added: 15 JVM (cart model).

Status: ACCEPTED on `144576c` (trio Native 40308915628 / Compose 40308915673 / Segmented 40308915722, all green; verified first-hand). Package head `144576c` green on the first head, no fix heads. Tests added: 8 JVM (hold mapper, including a full hold/resume round-trip).

Status: ACCEPTED on `bca5a49` (trio Native 40311743898 / Compose 40311743686 / Segmented 40311743650, all green; verified first-hand). Package head `bca5a49` green on the first head, no fix heads. Tests added: 7 JVM (quick cash math).

Status: ACCEPTED on `16c75a2` (trio Native 40314568994 / Compose 40314569019 / Segmented 40314569870, all green; verified first-hand). Package head `16c75a2` green on the first head, no fix heads. Tests added: 7 JVM (cash drawer session math).

Status: ACCEPTED on `171b678` (trio Native 40316754054 / Compose 40316754049 / Segmented 40316754109, all green; verified first-hand). Package head `171b678` green on the first head, no fix heads. Tests added: 6 JVM (shift session and summary).

Status: ACCEPTED on `658a278` (trio Native 40319703765 / Compose 40319703855 / Segmented 40319703755, all green; verified first-hand). Package head `658a278` green on the first head, no fix heads. Tests added: 4 JVM (manager override decision).
