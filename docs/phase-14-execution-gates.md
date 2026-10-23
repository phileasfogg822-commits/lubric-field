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

## Package 14.2 — party form UI — ACCEPTED

What was wired:

- **Rewritten** `PartiesScreen`: two modes over the ViewModel's typed
  surface only. List mode shows each party's name, type, blocked flag,
  and — when an opening balance exists — its direction line; the form
  mode is the full §16 field set in one scrollable form (name, type
  radio group, business name, contact person, mobile, WhatsApp,
  phone, email, address blocks, tax number, national ID, opening
  balance, credit limit, credit days, payment terms, price level,
  territory, salesperson, blocked, notes). Create/edit/delete flows
  and their error handling carry over; org.json is gone from the
  screen. Strings renamed customer→party where the entity is generic.
- **New** `PartyFormState` (+ `PartyAddressFormState`): text-first
  form state; blank optionals become null, wholly blank address rows
  are dropped, and a non-blank unparseable amount/credit-days value
  blocks the save with a form error instead of writing corrupt data.
  `groupId` is carried through edits with no editor until 14.3 —
  editing a grouped party must not silently drop its group link.
- **New** `PartyDisplay.openingBalanceDirection()`: the recorded
  balance convention — Customer/Both positive = Receivable, negative
  = Payable; Supplier mirrored; Other neutral; zero/absent shows no
  line. This is opening balance only (no ledger until Phase 22) and
  the list says exactly that by naming the figure an opening balance
  or a direction of it.
- The ViewModel's raw methods stay as the low-level store passthrough
  (pinned by `PartiesViewModelTest`); the screen no longer uses them.

Tests:

- **New** `PartyFormStateTest` (4): full load→save round-trip of every
  §16 field incl. both address kinds and the group link; blank
  optionals → null and blank address rows dropped; invalid numbers
  block the save while padded valid numbers parse; reset defaults.
- **New** `PartyDisplayTest` (3): the direction matrix incl. sign
  mirroring for suppliers and the zero/absent no-line cases.
- **Rewritten** `PartiesScreenComposeTest` (7): list + back; create a
  Supplier with an opening balance and see "Payable: 250", then edit
  round-trip; address created in the form lands through the codec;
  invalid credit days show the form error and save nothing; delete
  confirmation flow; empty state; save failure shows the error.

Local evidence: escaped-quote grep = 0 and braces balanced on every
changed Kotlin file. First verification is CI, as before.

Verification (2026-10-23): all three workflows green on the exact
head `6ee6ee2` (pull_request events, PR #23): Native Android CI run
39914565669 — success; Compose UI Emulator Tests run 39914565699 —
success (emulator log: feature:parties 7/7 tests, 0 failed; :app
12/12); Phase 12 Segmented Verification run 39914565716 — success.
Package 14.2 ACCEPTED on `6ee6ee2`. (An interim monitor report
claimed a Compose failure and a recording commit on this head;
both were checked against the repo and the Actions list and neither
exists — the only Compose run on `6ee6ee2` is the success above.)

## Package 14.3 — customer groups — ACCEPTED

What was wired:

- **New** `PartyGroup` + `PartyGroupJson` (§19): name plus the
  group-level defaults — price level, discount percent (BigDecimal,
  string round-trip), payment terms, tax rule, salesperson,
  territory — as its own operational record type "customer_groups"
  in the same scoped store, codec tolerance mirroring `PartyJson`.
- **ViewModel**: typed group surface — `observeGroups` / `loadGroups`
  / `saveGroup` / `deleteGroup`. Delete uses the same tombstone path
  as parties.
- **Orphan policy (decided here)**: deleting a group never edits its
  parties. The `groupId` link is kept and the party list renders it
  unresolved ("Group: —"), because sync may resurrect the group from
  another device; clearing the link on delete would destroy data the
  user never chose to remove. The delete dialog says exactly this.
- **UI**: a Groups mode beside the party list (group list with live
  member counts and price-level line, group form with the §19
  fields, delete confirmation), and a group picker in the party
  form ("No group" + one radio row per group). `PartyFormState`
  groupId is now real form state carried through edits.

Tests:

- **New** `PartyGroupJsonTest` (4): full/minimal round-trips,
  garbage → minimal group, discount as number/string/null.
- **New** `PartyGroupFormStateTest` (2): round-trip; blank optionals
  → null, invalid discount blocks the save.
- **New** `PartiesViewModelGroupsTest` (2): CRUD lands on the
  "customer_groups" table through the codec; legacy payload decodes.
- **Compose** `PartiesScreenComposeTest` (8 now; the fake repository
  is per-table): full lifecycle — create group, assign a party to
  it, member count goes 0 → 1, delete the group, the party survives
  and shows "Group: —".

Local evidence: escaped-quote grep = 0 and braces balanced on every
changed Kotlin file. First verification is CI, as before.

Verification (2026-10-23): all three workflows green on the exact
head `b5e2039` (pull_request events, PR #23): Native Android CI run
39916981618 — success; Compose UI Emulator Tests run 39916981633 —
success (the group lifecycle test ran on the emulator: create group,
assign party, member count 0 → 1, delete group, party survives
showing "Group: —"); Phase 12 Segmented Verification run
39916981588 — success. Package 14.3 ACCEPTED on `b5e2039`.

## Package 14.4 — credit rules + profile header — ACCEPTED

What was wired:

- **New** `PartyCredit` (pure rules, §18): outstanding is the
  receivable side of the opening balance alone — the documented
  stand-in until the Phase 22 ledger exists; payable/neutral
  balances are not exposure. Statuses: BLOCKED (takes precedence),
  NO_LIMIT (no usable limit set), WITHIN_LIMIT, NEAR_LIMIT,
  OVER_LIMIT. **Threshold decision**: near-limit fires at
  utilization >= 95% — the spec §18 example's own number (95 of a
  100 limit warns) — rather than an invented 90%; over-limit at
  >= 100%. Sale-time hard blocking stays with Phases 17/21; this
  package ships data and warnings only.
- **New** `PartyProfileMode` (§17 header): a read-only profile
  surface per party — name, type, group (unresolved shown as "—"),
  the balance-direction line, and the credit block (outstanding,
  credit limit or "No credit limit set", credit days, status text;
  blocked/over-limit in error color) plus payment terms, mobile,
  email, notes, and an Edit jump into the form. §17's tabs and
  Invoice/Order/Payment/Statement actions are deliberately absent:
  they need Phases 17–22 data, and dead buttons would fake
  capability.
- List items gain a "View" button into the profile; `labelRes` is
  now shared between the screen and the profile.

Tests:

- **New** `PartyCreditTest` (4): the boundary matrix pinned exactly
  (94.99 within, 95 near, 100 over), blocked precedence, zero/absent
  limit → NO_LIMIT, and only the receivable side counting as
  outstanding (incl. the supplier-advance case).
- **Compose** `PartiesScreenComposeTest` (9 now): profile flow —
  create a party at 96/100, open its profile, see "Outstanding: 96",
  "Credit limit: 100" and "Near credit limit", back to the list.

Local evidence: escaped-quote grep = 0 and braces balanced on every
changed Kotlin file. First verification is CI, as before.

Verification (2026-10-23): all three workflows green on the exact
head `7be6ebc` (pull_request events, PR #23): Native Android CI run
39918960967 — success; Compose UI Emulator Tests run 39918961042 —
success (the profile flow ran on the emulator: party at 96/100
credit shows "Outstanding: 96", "Credit limit: 100" and
"Near credit limit"); Phase 12 Segmented Verification run
39918960933 — success. Package 14.4 ACCEPTED on `7be6ebc`.
