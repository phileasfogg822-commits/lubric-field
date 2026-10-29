package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.feature.accounts.AccountsViewModel
import com.lubrcapp.lubrcapp.feature.accounts.PartyLedgerBalance
import com.lubrcapp.lubrcapp.feature.accounts.PartyLedgerProfile
import com.lubrcapp.lubrcapp.feature.parties.PartyItemRow
import com.lubrcapp.lubrcapp.feature.parties.PartyLedgerRow
import com.lubrcapp.lubrcapp.feature.parties.PartyLedgerTabsData
import com.lubrcapp.lubrcapp.feature.parties.PartyLiveBalance
import com.lubrcapp.lubrcapp.feature.parties.PartyOrderRow
import com.lubrcapp.lubrcapp.feature.parties.PartyStatementRow
import com.lubrcapp.lubrcapp.feature.parties.combineLiveBalance
import com.lubrcapp.lubrcapp.feature.sales.SalesDocumentEngine
import com.lubrcapp.lubrcapp.feature.sales.SalesViewModel

/**
 * Phase 22.5 — the app-layer mapping from the built
 * ledger view to the parties screen's plain tab data.
 * This is the shell's job (the 21.5 precedent): the
 * parties screen cannot depend on feature/accounts or
 * feature/sales, so it takes a suspend provider lambda
 * and this object implements it. Amounts are rendered
 * as plain strings here — the screen displays text, it
 * never formats Money.
 *
 * Currency is derived, never invented: the profile is
 * built in the currency of the party's earliest ledger
 * row (rows arrive chronological), and the orders/items
 * totals use the same currency. A party with no ledger
 * rows yet has no known currency, so orders/items are
 * totaled under CurrencyCode "XXX" — ISO 4217's "no
 * currency" code, used here as a computation-only tag:
 * the engine needs a currency to construct Money, but
 * only the plain numbers reach the display and nothing
 * is persisted, so the tag is invisible to the user.
 * A multi-currency party's tabs show only its earliest
 * currency's rows (the builder's per-currency rule);
 * cross-currency profiles are not a 22.5 scope.
 */
object PartyLedgerTabsMapping {

    suspend fun tabsFor(
        scope: OperationalScope,
        partyId: String,
        salesViewModel: SalesViewModel,
        accountsViewModel: AccountsViewModel,
    ): PartyLedgerTabsData {
        val rows = accountsViewModel.loadLedgerRowsForParty(scope, partyId)
        val currency = rows.firstOrNull()?.currency?.let { CurrencyCode.of(it) }
            ?: CurrencyCode.of("XXX")
        val profile = PartyLedgerProfile.build(rows, partyId, currency)
        val transactions = profile.transactions.map { row ->
            PartyLedgerRow(row.date, row.documentNumber, row.description, row.amount.toPlainString())
        }
        val statement = profile.statement.map { entry ->
            PartyStatementRow(
                entry.row.date,
                entry.row.documentNumber,
                entry.row.description,
                entry.row.amount.toPlainString(),
                entry.runningBalance.amount.toPlainString(),
            )
        }
        val payments = profile.payments.map { row ->
            PartyLedgerRow(row.date, row.documentNumber, row.description, row.amount.toPlainString())
        }
        val documents = (
            salesViewModel.loadInvoices(scope) +
                salesViewModel.loadReturns(scope) +
                salesViewModel.loadCreditNotes(scope)
            ).filter { it.partyId == partyId }
            .sortedWith(compareBy({ it.date }, { it.number }))
        val orders = documents.map { document ->
            val lines = salesViewModel.loadLines(scope, document.id).sortedBy { it.position }
            val total = SalesDocumentEngine.totals(lines, currency).grandTotal.amount.toPlainString()
            PartyOrderRow(document.number, document.date, document.status, total)
        }
        val items = documents.flatMap { document ->
            val lines = salesViewModel.loadLines(scope, document.id).sortedBy { it.position }
            val lineTotals = SalesDocumentEngine.totals(lines, currency).lines
            lines.zip(lineTotals) { line, totals ->
                PartyItemRow(line.description, line.quantity.toPlainString(), totals.lineTotal.amount.toPlainString())
            }
        }
        return PartyLedgerTabsData(transactions, statement, payments, orders, items)
    }

    /**
     * Phase 32.2 — the same ledger balance the profile's statement ends
     * on, without the document/item lists: the khata list needs one
     * number per party, not the full tab bundle. Currency follows the
     * 22.5 rule (the party's earliest ledger row); a party with no rows
     * is zero on both sides.
     */
    suspend fun balanceFor(
        scope: OperationalScope,
        partyId: String,
        accountsViewModel: AccountsViewModel,
    ): PartyLiveBalance {
        val rows = accountsViewModel.loadLedgerRowsForParty(scope, partyId)
        val currency = rows.firstOrNull()?.currency?.let { CurrencyCode.of(it) }
            ?: CurrencyCode.of("XXX")
        val receivableNet = PartyLedgerBalance.receivableBalance(rows, partyId, currency).amount
        val payableNet = PartyLedgerBalance.payableBalance(rows, partyId, currency).amount
        return combineLiveBalance(receivableNet, payableNet)
    }
}
