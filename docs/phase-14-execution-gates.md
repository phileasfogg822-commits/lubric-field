# Phase 14 — Party Master Data (Roadmap V2)

Branch: `phase-14-party-master-data` (from the Phase 13 candidate,
`phase-13-app-shell` at `6cb2bb5`). Draft PR to follow via the
connector. Packages are accepted only with green-CI proof on the exact
SHA, as in Phases 12–13.

## Authoritative scope

Master spec §15 (party system: one generic Party model; types Customer /
Supplier / Both / Other), §16 (party fields), §17 (customer profile),
§18 (customer credit), §19 (customer groups). Parties live in the
Phase 12 operational store as "customers" records — offline CRUD, sync
and tombstones already exist; Phase 14 layers the typed model and UI
over that store and does not replace it, and does not change delete
semantics (customers are nonfinancial master data, soft-deleted by
tombstone per the Phase 12 sync contract).

Standing limitation: no customer ledger exists until Phase 22, so the
only balance fact is Opening Balance. Profile balances and credit
warnings are computed from opening balance alone and must say so —
never invent ledger numbers. Sale-time credit enforcement belongs to
the sales phases (17/21); Phase 14 ships the data and the pure rules.

## Package split

- **14.1** Typed Party model + codec + typed ViewModel surface.
- **14.2** Party form UI (full §16 field set, type selector, multiple
  addresses) and list facts that exist today.
- **14.3** Customer groups (own record type, typed model + CRUD,
  Party.groupId link).
- **14.4** Credit rules (pure) + profile header (name, type, balance
  from opening balance, warning chips).
- **14.5** Phase gate: evidence sweep + close-out + Phase 15 prep.

## Package 14.1 — typed Party model + codec — ACCEPTED

What was wired:

- **New** `Party` (feature/parties): the §16 field set — name, type
  (the domain module's `PartyType` enum, reused so the codebase keeps
  one type vocabulary), business name, contact person, mobile,
  WhatsApp, phone, email, addresses, tax number, national ID, opening
  balance, credit limit, credit days, payment terms, price level,
  group link, territory, salesperson, blocked flag, notes. Money is
  plain `BigDecimal` in the business's base currency (§16 defines no
  per-party currency). The record id is the store's id and scoping
  stays in `OperationalScope`, so neither is duplicated into the
  payload model. The domain module's scope-carrying `Party` scaffold
  is a separate model and is untouched.
- **New** `PartyAddress`: kind (Billing/Shipping) + line1/line2/city/
  region/postalCode/country; a party holds a list of them (§16
  multiple addresses).
- **New** `PartyJson` codec (manual `JsonObject` mapping over
  kotlinx-serialization-json 1.6.2 — the version the app module already
  pins; no compiler plugin): amounts round-trip as plain strings so
  `BigDecimal` precision is exact. Decode is tolerant by design, since
  the table predates the model: unknown/missing fields default,
  unparseable payloads decode to a minimal party (id only), the
  store's record id always wins over a legacy in-payload "uuid",
  legacy "description" maps to notes, a legacy flat
  address/city/country triple folds into one billing address, and
  numbers written as strings decode as numbers.
- **Modified** `PartiesViewModel`: typed surface beside the raw one —
  `observeParties` / `loadParties` (decode through the codec) and
  `saveParty` (encode through the codec, same "customers" table,
  revision passthrough). The raw methods stay until 14.2 migrates the
  screen. No UI change in this package.
- **Dependency added**: `kotlinx-serialization-json` in
  feature/parties (one line).

Tests:

- **New** `PartyJsonTest` (10 plain-JUnit tests): full and minimal
  round-trips; legacy name-only decode (record id beats payload uuid);
  description→notes; blank/garbage/non-object payloads → minimal
  party; string-and-number amount decoding; unknown fields ignored;
  legacy flat-address fold; explicit JSON nulls default; type parsing
  case-tolerant with Customer default.
- **New** `PartiesViewModelTypedTest` (3 tests, fake repository):
  loadParties decodes legacy + typed records side by side;
  observeParties maps through the codec; saveParty encodes through
  the codec into the "customers" table.

Local evidence: new files carry no escaped-quote literals (grep = 0)
and are brace-balanced. Android cannot compile locally (no SDK in this
environment); first verification is CI. The JVM unit tests exercise
the codec directly — no org.json anywhere in the new code, so the
Phase 12 JVM-stub failure mode does not apply.

Verification (2026-10-23): all three workflows green on the exact
head `adccf4d` (pull_request events, PR #23): Native Android CI run
39913036599 — success; Compose UI Emulator Tests run 39913036596 —
success; Phase 12 Segmented Verification run 39913036669 — success.
Package 14.1 ACCEPTED on `adccf4d`.
