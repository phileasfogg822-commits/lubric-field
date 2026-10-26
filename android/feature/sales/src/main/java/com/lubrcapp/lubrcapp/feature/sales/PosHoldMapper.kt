package com.lubrcapp.lubrcapp.feature.sales

import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.ItemId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.PartyId
import com.lubrcapp.lubrcapp.domain.common.Percentage
import com.lubrcapp.lubrcapp.domain.common.Quantity
import com.lubrcapp.lubrcapp.domain.sales.PosCart
import com.lubrcapp.lubrcapp.domain.sales.PosCartLine
import com.lubrcapp.lubrcapp.domain.sales.PosLineDiscount
import com.lubrcapp.lubrcapp.domain.sales.PosParty

/**
 * Phase 26.2 — hold sale (§148): a held bill IS a DRAFT
 * invoice document, saved through the existing record
 * machinery (SalesViewModel.saveDocument / saveLine) and
 * resumed by loading it back. No new table, no whitelist
 * change, no hold marker: the POS "held bills" list is the
 * draft-invoice list, and resuming any draft invoice in
 * the counter is a supported flow.
 *
 * Mapping rules, all round-trippable:
 * - Cart id becomes the document id, so hold then resume
 *   finds the same document; lines keep their ids and
 *   take positions in cart order.
 * - WalkIn maps to a null partyId (§149 — no customer
 *   record exists); Named maps to its id value.
 * - Rates and discount values are plain BigDecimals in
 *   the document layer, so the currency is a parameter
 *   of the mapping, not a stored field — the same
 *   currency the totals engine takes at checkout.
 * - Tax components do NOT survive hold: the cart carries
 *   no tax (26.1), and tax is re-derived from item config
 *   at invoice conversion. A line held with tax components
 *   resumes untaxed; that is a documented 26.2 limit, not
 *   a silent edit — the mapper drops them explicitly.
 * - Number and date stay empty until posting: a held bill
 *   is unnumbered and undated, like any fresh draft.
 */
object PosHoldMapper {

    fun cartToDocument(cart: PosCart): SalesDocument = SalesDocument(
        id = cart.id,
        type = SalesDocumentType.INVOICE,
        number = "",
        partyId = when (val party = cart.party) {
            PosParty.WalkIn -> null
            is PosParty.Named -> party.id.value
        },
        date = "",
        status = "DRAFT",
    )

    fun cartToLines(cart: PosCart): List<SalesDocumentLine> =
        cart.lines.mapIndexed { index, line ->
            SalesDocumentLine(
                id = line.id,
                orderId = cart.id,
                position = index,
                itemId = line.itemId?.value,
                description = line.description,
                quantity = line.quantity.amount,
                rate = line.rate.amount,
                discountKind = when (line.discount) {
                    null -> null
                    is PosLineDiscount.Percent -> "PERCENT"
                    is PosLineDiscount.Fixed -> "FIXED"
                },
                discountValue = when (val discount = line.discount) {
                    null -> null
                    is PosLineDiscount.Percent -> discount.percentage.points
                    is PosLineDiscount.Fixed -> discount.amount.amount
                },
            )
        }

    fun documentToCart(
        document: SalesDocument,
        lines: List<SalesDocumentLine>,
        currency: CurrencyCode,
    ): PosCart {
        require(document.type == SalesDocumentType.INVOICE) {
            "Held bills are invoices: ${document.id}"
        }
        return PosCart(
            id = document.id,
            party = document.partyId
                ?.takeIf { it.isNotBlank() }
                ?.let { PosParty.Named(PartyId(it)) }
                ?: PosParty.WalkIn,
            lines = lines.sortedBy { it.position }.map { line ->
                PosCartLine(
                    id = line.id,
                    itemId = line.itemId?.let { ItemId(it) },
                    description = line.description,
                    quantity = Quantity.of(line.quantity),
                    rate = Money.of(line.rate, currency),
                    discount = when (line.discountKind) {
                        "PERCENT" -> line.discountValue
                            ?.let { PosLineDiscount.Percent(Percentage.of(it)) }
                        "FIXED" -> line.discountValue
                            ?.let { PosLineDiscount.Fixed(Money.of(it, currency)) }
                        else -> null
                    },
                )
            },
        )
    }
}
