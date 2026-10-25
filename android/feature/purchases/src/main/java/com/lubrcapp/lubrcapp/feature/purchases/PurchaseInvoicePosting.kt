package com.lubrcapp.lubrcapp.feature.purchases

import com.lubrcapp.lubrcapp.domain.accounting.Discount
import com.lubrcapp.lubrcapp.domain.accounting.DocumentLineInput
import com.lubrcapp.lubrcapp.domain.accounting.DocumentPosting
import com.lubrcapp.lubrcapp.domain.accounting.DocumentTotalsCalculator
import com.lubrcapp.lubrcapp.domain.accounting.PartyBalanceDirection
import com.lubrcapp.lubrcapp.domain.accounting.PartyLedgerEffect
import com.lubrcapp.lubrcapp.domain.accounting.PostingJournalLine
import com.lubrcapp.lubrcapp.domain.accounting.TaxComponent
import com.lubrcapp.lubrcapp.domain.accounting.TaxScheme
import com.lubrcapp.lubrcapp.domain.common.AccountId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.Percentage
import com.lubrcapp.lubrcapp.domain.common.TransactionId
import com.lubrcapp.lubrcapp.domain.purchase.PurchaseInvoice
import com.lubrcapp.lubrcapp.domain.purchase.PurchaseInvoiceLine

/**
 * Phase 23.2 — the purchase invoice posting builder,
 * the vendor mirror of 21.3's SalesInvoicePosting.
 * A purchase invoice posts money only: Dr Expense /
 * Dr Tax / Cr Payable, all POSITIVE amounts (the 21.4
 * mirror principle — no negative journal lines), with
 * a PAYABLE effect for the full grand total. Stock
 * movement is NOT part of posting: receiving owns
 * stock (supervisor decision 3), so this builder emits
 * no stock movements at all.
 *
 * Totals are RECOMPUTED from the lines through the
 * shared DocumentTotalsCalculator — never trusted
 * from the invoice's stored totals — so the posting
 * satisfies PostingValidator by construction: the
 * calculator's grand total, tax total, and taxable
 * base are exactly what the validator recomputes.
 * Line tax comes from the line's flat taxRate, mapped
 * to a single-component exclusive scheme (purchase
 * lines carry no inclusive flag, so exclusive is the
 * rule). The invoice's stored total supplies only the
 * currency hint; any disagreement between stored and
 * computed totals is a record-data issue, not a
 * posting input.
 *
 * Posting does not change the purchase status (the
 * 21.5 principle): status moves through
 * receiving/payment flows, not through the journal.
 */
data class PurchasePostingAccounts(
    val payableAccountId: String,
    val expenseAccountId: String,
    val taxAccountId: String? = null,
)

object PurchaseInvoicePosting {

    fun invoiceToPosting(
        invoice: PurchaseInvoice,
        lines: List<PurchaseInvoiceLine>,
        accounts: PurchasePostingAccounts,
    ): DocumentPosting {
        require(lines.isNotEmpty()) {
            "A purchase posting needs at least one line: ${invoice.id.value}"
        }
        require(lines.all { it.quantity.amount.signum() >= 0 }) {
            "Purchase lines must not be negative; returns to suppliers are debit notes: ${invoice.id.value}"
        }
        val inputs = lines.map { line ->
            DocumentLineInput(
                quantity = line.quantity,
                rate = line.unitPrice,
                discount = if (line.discount.isZero) null else Discount.Fixed(line.discount),
                taxScheme = if (line.taxRate == Percentage.ZERO) {
                    null
                } else {
                    TaxScheme(
                        name = "Purchase Tax",
                        inclusive = false,
                        components = listOf(TaxComponent("Tax", line.taxRate)),
                    )
                },
            )
        }
        val totals = DocumentTotalsCalculator.totalDocument(
            lines = inputs,
            invoiceDiscount = if (invoice.discount.isZero) {
                null
            } else {
                Discount.Fixed(invoice.discount)
            },
            currencyHint = invoice.total,
        )
        val zero = Money.zero(totals.grandTotal.currency)
        val journalLines = buildList {
            val expenseAmount = if (accounts.taxAccountId != null) {
                totals.grandTotal - totals.taxTotal
            } else {
                totals.grandTotal
            }
            if (!expenseAmount.isZero) {
                add(PostingJournalLine(AccountId(accounts.expenseAccountId), expenseAmount, zero))
            }
            if (accounts.taxAccountId != null && !totals.taxTotal.isZero) {
                add(PostingJournalLine(AccountId(accounts.taxAccountId), totals.taxTotal, zero))
            }
            if (!totals.grandTotal.isZero) {
                add(PostingJournalLine(AccountId(accounts.payableAccountId), zero, totals.grandTotal))
            }
        }
        return DocumentPosting(
            transactionId = TransactionId(invoice.id.value),
            documentType = "PURCHASE_INVOICE",
            documentNumber = invoice.invoiceNumber,
            partyId = invoice.partyId,
            totals = totals,
            stockMovements = emptyList(),
            journalLines = journalLines,
            partyEffect = PartyLedgerEffect(
                invoice.partyId,
                PartyBalanceDirection.PAYABLE,
                totals.grandTotal,
            ),
        )
    }
}
