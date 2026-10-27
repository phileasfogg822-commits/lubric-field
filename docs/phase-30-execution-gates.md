# Phase 30 — Cloud Platform / Web Admin / Portal / Store / SaaS / Production

Final roadmap phase. Branch `phase-30-cloud-platform` from Phase 29 closed head `a029d86`.
Prep: `agent_notes/phase-30-prep.md` (goal workspace, not the repo).
Scope: spec Phase 10 build list (Online Store, Customer Portal, Web Admin,
Advanced Sync, Notifications, API) minus Notifications (landed in Phase 29);
§185 Web Admin, §186 Multi-Tenancy, §226 Document Portal, §227 Customer
Portal, §228 API; cross-device numbering (deferred 17/18 item).
Staging pins: GitHub-only development — no live backend, no VPS, no real
endpoints; API contracts are pure types, not a server; no SaaS billing
(plans are data + gating only); no web app built (React/Next.js admin is a
separate codebase); cross-device uniqueness by device-prefix construction,
not server coordination.

## Package 30.1 — Multi-tenancy guards

Multi-tenancy guards (§186) in domain/business:
`TenantScope` (blank tenant or business id
rejected at construction), `TenantScopedRecord`,
and `TenantScopeOps` with `isVisible` (exact
pair match), `requireVisible` (throws outside
scope), and `visible` (filters a list, order
preserved). Pure functions over plain data:
the app-side contract for §186. The spec
requires the BACKEND to enforce tenant
isolation, so this guard is the contract the
backend implements — not the enforcement
itself. 8 JVM tests.

Status: ACCEPTED on `d7e05c8`.

## Package 30.2 — API surface contracts

API surface contracts (§228 + §229) in
domain/business: seven resources in spec
order (/items, /parties, /orders, /invoices,
/payments, /stock, /reports), a 12-route
method table (GET everywhere, POST only for
the five writable resources — stock and
reports are derived views, so read-only),
requests that always carry a 30.1 TenantScope,
and the four webhook event names verbatim
from §229. Pure types, not a server: no
transport, no endpoint, no credentials. 8 JVM
tests.

Status: ACCEPTED on `0965ab2`.

## Package 30.3 — Customer portal models

Customer portal models (§227 + §226) in
domain/business: five sections in spec order
(Orders, Invoices, Statements, Payments,
Catalog), portal account, order/invoice/
payment/catalog rows, and PortalOps with due
(amount minus paid, negative when overpaid),
canPay (positive due only), and dueByCurrency
(totals grouped per currency, never mixed).
Download and Pay from §226 are portal
actions, not model fields, so they stay in
the UI layer. 8 JVM tests.

Status: ACCEPTED on `3c4bf46`.

## Package 30.4 — Online store models

Online store models (spec Phase 10 "Online
Store") in domain/business: published
storefront catalog, store order intake
validation collecting every problem (unknown
item, unpublished item, non-positive
quantity, rate mismatch), and totals that
throw on mixed currencies rather than
summing them. Intake only — no checkout,
no payment, no fulfillment. 8 JVM tests.

Status: ACCEPTED on `c81cca9`.

## Package 30.5 — Web admin + SaaS models

Status: NOT STARTED.

## Package 30.6 — Cross-device numbering

Status: NOT STARTED.

## Package 30.7 — Cloud status surface

Status: NOT STARTED.

## Package 30.8 — Gate

Status: NOT STARTED.

30.1 ACCEPTED 2026-10-27 on `d7e05c8` (trio Native 40479707310 / Compose 40479707312 / Segmented 40479707346, all green, first head): multi-tenancy guards in domain/business, 8 JVM tests.

30.2 ACCEPTED 2026-10-27 on `0965ab2` (trio Native 40484121488 / Compose 40484121644 / Segmented 40484121438, all green, after fix head 0965ab2): API surface contracts in domain/business, 8 JVM tests.

30.3 ACCEPTED 2026-10-27 on `3c4bf46` (trio Native 40486002318 / Compose 40486002301 / Segmented 40486002283, all green, first head): customer portal models in domain/business, 8 JVM tests.

30.4 ACCEPTED 2026-10-27 on `c81cca9` (trio Native 40488480350 / Compose 40488480331 / Segmented 40488480324, all green, first head): online store models in domain/business, 8 JVM tests.
