# Phase 26 execution gates — POS & Cashier Ops

Base: `phase-25-documents-printing` @ `2145ecf` (Phase 25
gate head). Branch `phase-26-pos-cashier-ops`.
Spec: §147 POS Mode, §148 Hold Sale, §149 Walk-In
Customer, §150 Quick Cash Payment, §151 Cash Drawer,
§152 Shift System, §153 Price Override, §154 Discount
Approval. Prep: `agent_notes/phase-26-prep.md` (goal
workspace, not the repo).

## Package 26.1 — POS cart model

Lines, quantities, discounts, walk-in default.

Status: NOT STARTED.

## Package 26.2 — Hold sale

Hold / resume a bill.

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
