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
 * Phase 27.2 — fast order (§40 Salesperson Fast Order
 * Mode): a fast order IS a DRAFT sales order document,
 * saved through the existing record machinery exactly
 * like a held bill (26.2) — no new table, no whitelist
 * change, no order marker. The field salesperson's
 * "take order" list is the draft-order list, and an
 * order converts onward through the existing sales
 * document flow (fulfilment, delivery, invoicing).
 *
 * Mapping rules, mirroring PosHoldMapper:
 * - Cart id becomes the document id; lines keep their
 *   ids and take positions in cart order.
 * - Forward mapping REQUIRES a named party: the §40
 *   header always shows a customer, and a walk-in
 *   fast order is the POS counter's job (26.x), not
 *   field sales — cartToOrder throws on WalkIn.
 * - Backward mapping is lenient like hold: a null or
 *   blank partyId resumes as WalkIn, so a draft order
 *   created elsewhere can still be picked up.
 * - Rates and discount values are plain BigDecimals in
 *   the document layer, so the currency is a parameter
 *   of the mapping, not a stored field.
 * - Tax components do NOT survive: the cart carries no
 *   tax (26.1), and tax is re-derived from item config
 *   at order conversion — the same documented 26.2
 *   limit.
 * - Number and date stay empty until posting: a fast
 *   order is unnumbered and undated, like any fresh
 *   draft. Balance / credit-limit display (§40 header)
 *   is a screen concern (27.7), fed by the 22.x
 *   outstanding computation — not stored here.
 */
object FastOrderMapper {

    fun cartToOrder(cart: PosCart): SalesDocument {
        val party = cart.party
        require(party is PosParty.Named) {
            "Fast orders require a named customer: ${cart.id}"
        }
        return SalesDocument(
            id = cart.id,
            type = SalesDocumentType.SALES_ORDER,
            number = "",
            partyId = party.id.value,
            date = "",
            status = "DRAFT",
        )
    }

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

    fun orderToCart(
        document: SalesDocument,
        lines: List<SalesDocumentLine>,
        currency: CurrencyCode,
    ): PosCart {
        require(document.type == SalesDocumentType.SALES_ORDER) {
            "Fast orders are sales orders: ${document.id}"
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
