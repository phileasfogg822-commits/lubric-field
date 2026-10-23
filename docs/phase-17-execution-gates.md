# Phase 17 — Transaction / Tax / Numbering / Posting Contract Engine (Roadmap V2)

Branch: `phase-17-transaction-engine` (from the Phase 16 candidate,
`phase-16-branches-pricing-batch` at `f760ce1`). Draft PR
to follow via the connector. Packages are accepted only with green-CI
proof on the exact SHA, as in Phases 12–16.

## Authoritative scope

Master spec §8.2 (generic tax architecture: Tax Scheme, Tax
Registration Number, Tax Rate, Tax Inclusive / Exclusive, Tax
Components; never hard-code GST as a mandatory architectural
assumption), §14 (transaction timeline), §51 (automatic numbering:
independent sequences per document type; configurable prefix, suffix,
starting number, reset yearly/monthly, branch prefix), §52 (invoice
item row), §53 (discounts: line % or fixed, invoice-level % or fixed,
schemes), §54 (additional charges, each taxable or non-taxable).

This phase is the CONTRACT engine: pure rules and types that Phases
18/21 (documents), 19 (inventory ledger), 20 (accounting core) and 22
(party ledger) consume. No documents, no ledgers, no UI screens beyond
what proves the contracts. The engine fills the domain scaffolds for
the first time (domain/accounting, domain/business) — pure Kotlin/JVM
modules over domain/common, zero new Gradle wiring.

Standing limitations, stated plainly: numbering counters are
device-local in LOCAL_ONLY mode — cross-device uniqueness in CLOUD
mode is a Phase 30 concern, and the "numbering_sequences" table is not
in the Phase 12 sync whitelist (nor are tax-scheme records), so engine
records are local-only until the sync layer learns them; the sync
whitelists are NOT edited in this phase. Scheme discounts (Buy 10 Get
1) are deferred to the document phases 18/21 — they are a line
transform needing promotion configuration. The invoice-level discount
is a bottom-line discount applied after tax and does not recompute
tax (see 17.3). The transaction record shape (§14) gets its first
writer in Phase 18; see 17.5.

## Package split

- **17.1** Tax model + computation (§8.2): TaxScheme / TaxComponent /
  TaxResult as data; TaxCalculator — exclusive adds per-component tax
  (HALF_UP 2dp per component), inclusive extracts (base rounded first,
  tax is the exact remainder, component residue absorbed by the
  largest component so parts always sum to the whole).
- **17.2** Numbering (§51): NumberingSequence config + NumberingState
  counter + DocumentNumbering (nextNumber / format / issue); segments
  [branchPrefix, prefix, period, paddedNumber] joined with "-", suffix
  verbatim; first invoice of 2026 = "INV-2026-00001".
- **17.3** Document totals engine (§52/§53/§54): line math (qty ×
  rate, line discount clamping at the gross, per-line tax via 17.1),
  additional charges (taxable or not), invoice-level discount
  (bottom-line policy, above), round-off reported as its own amount,
  per-component tax aggregation across lines and charges.
- **17.4** Posting contract: DocumentPosting — the stock movements
  (Phase 19, "stock_movements"), journal lines (Phase 20,
  "account_transactions") and party-ledger effect (Phase 22,
  "party_ledger") a posted document must emit — plus PostingValidator
  (balanced journal, reconciled totals, valid movements) including
  the §18 credit hard block as a caller-supplied CreditCheck.
- **17.5** Transaction record shape (§14): settled as a contract note
  (see section) — the timeline entry type is defined by its first
  producer in Phase 18; defining a record nothing writes would be
  fabrication-prone dead code.
- **17.6** Phase gate: evidence sweep + close-out + Phase 18 prep.

## Package 17.1 — Tax model + computation

Status: PENDING CI.

## Package 17.2 — Numbering

Status: PENDING CI.

## Package 17.3 — Document totals engine

Status: PENDING CI.

## Package 17.4 — Posting contract + validator

Status: PENDING CI.

## Package 17.5 — Transaction record shape (§14)

Status: DECIDED — contract note only (see package split). The §14
timeline entry (document type, number, party, amount, status, date)
will be defined in Phase 18 alongside its first writer, against the
17.4 posting contract, which already fixes the vocabulary (document
type, document number, party, totals) a timeline entry summarizes.

## Package 17.6 — Phase gate

Status: NOT STARTED.
