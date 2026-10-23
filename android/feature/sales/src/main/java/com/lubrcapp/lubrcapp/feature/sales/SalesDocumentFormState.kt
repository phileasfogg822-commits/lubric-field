package com.lubrcapp.lubrcapp.feature.sales

import com.lubrcapp.lubrcapp.domain.accounting.DocumentTotals
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import java.math.BigDecimal

/**
 * Phase 18.4 — the document editor's form state, in the
 * established feature pattern (a pure, unit-tested holder the
 * screen binds to). All numeric fields are TEXT while editing;
 * parsing happens at build time so a half-typed value never
 * crashes the form. A line's tax snapshot is built from the
 * tax name + percent fields: one component, named exactly
 * what the user typed (the form pre-fills "Tax", a visible,
 * editable default) — multi-component scheme records do not
 * exist yet, and the snapshot model already carries them when
 * a scheme home lands.
 */
data class SalesLineDraft(
    val id: String,
    val itemId: String? = null,
    val description: String = "",
    val quantity: String = "1",
    val rate: String = "",
    val discountKind: String? = null,
    val discountValue: String = "",
    val taxName: String = "Tax",
    val taxPercent: String = "",
    val fulfilledQuantity: BigDecimal = BigDecimal.ZERO,
) {
    fun toLine(documentId: String, position: Int): SalesDocumentLine? {
        val qty = quantity.trim().toBigDecimalOrNull() ?: return null
        val rateValue = rate.trim().toBigDecimalOrNull() ?: return null
        val discount = discountValue.trim().toBigDecimalOrNull()
        val tax = taxPercent.trim().toBigDecimalOrNull()
        return SalesDocumentLine(
            id = id,
            orderId = documentId,
            position = position,
            itemId = itemId,
            description = description.trim(),
            quantity = qty,
            rate = rateValue,
            discountKind = if (discount != null) discountKind else null,
            discountValue = discount,
            taxInclusive = false,
            taxComponents = if (tax != null && tax.signum() > 0 && taxName.isNotBlank()) {
                listOf(SalesTaxComponent(taxName.trim(), tax))
            } else {
                emptyList()
            },
            fulfilledQuantity = fulfilledQuantity,
        )
    }
}

data class SalesDocumentFormState(
    val existingId: String? = null,
    val type: SalesDocumentType = SalesDocumentType.QUOTATION,
    val number: String = "",
    val partyId: String? = null,
    val date: String = "",
    val dueDate: String = "",
    val status: String = "DRAFT",
    val notes: String = "",
    val lines: List<SalesLineDraft> = emptyList(),
) {
    /** Every line must parse; a document with no lines is a legal draft. */
    val isValid: Boolean
        get() = lines.all { it.toLine("", 0) != null }

    fun toDocument(id: String): SalesDocument = SalesDocument(
        id = id,
        type = type,
        number = number,
        partyId = partyId,
        date = date.trim(),
        dueDate = dueDate.trim().ifBlank { null },
        status = status,
        notes = notes.trim(),
    )

    fun toLines(documentId: String): List<SalesDocumentLine> =
        lines.mapIndexed { index, draft -> draft.toLine(documentId, index) }
            .filterNotNull()

    /** Live totals for the preview, or null while any line is unparseable. */
    fun totalsOrNull(currency: CurrencyCode): DocumentTotals? {
        if (!isValid) return null
        return SalesDocumentEngine.totals(toLines("preview"), currency)
    }

    companion object {
        fun fromDocument(document: SalesDocument, lines: List<SalesDocumentLine>): SalesDocumentFormState =
            SalesDocumentFormState(
                existingId = document.id,
                type = document.type,
                number = document.number,
                partyId = document.partyId,
                date = document.date,
                dueDate = document.dueDate ?: "",
                status = document.status,
                notes = document.notes,
                lines = lines.sortedBy { it.position }.map { line ->
                    SalesLineDraft(
                        id = line.id,
                        itemId = line.itemId,
                        description = line.description,
                        quantity = line.quantity.toPlainString(),
                        rate = line.rate.toPlainString(),
                        discountKind = line.discountKind,
                        discountValue = line.discountValue?.toPlainString() ?: "",
                        taxName = line.taxComponents.firstOrNull()?.name ?: "Tax",
                        taxPercent = line.taxComponents.firstOrNull()?.ratePercent?.toPlainString() ?: "",
                        fulfilledQuantity = line.fulfilledQuantity,
                    )
                },
            )
    }
}
