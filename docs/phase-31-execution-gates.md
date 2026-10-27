# Phase 31 execution gates — Field Operations Pack

Branch `phase-31-field-operations` from post-gate hotfix `8234472`.
Prep: `agent_notes/phase-31-prep.md` (goal workspace).
Each package is accepted only with a green trio on its exact head.
PR stays draft; no merge without the owner's approval.

## Package 31.1 — Delivery on orders

Sales-order editor gains a Delivery section for CONFIRMED and beyond:
per-line delivered/ordered counts, a quantity field and a Deliver button
wired to `SalesViewModel.recordDelivery` (21.2), so the order status
re-derives to PARTIALLY_FULFILLED / FULFILLED. Recording a delivery
posts no stock movement — movements post at invoice time, never twice.
Tests: JVM orchestration (partial then full delivery moves status) and
an emulator test driving the new controls. Head `5b1454d`.

## Package 31.2 — Receive/pay money

## Package 31.3 — Cash in/out

## Package 31.4 — Pending orders view

## Package 31.5 — Overdue alerts

## Package 31.6 — Gate

31.1 ACCEPTED at `5b1454d`: Native 40563706397 / Compose 40563706406 / Segmented 40563706375, all success.
