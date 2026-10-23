package com.lubrcapp.lubrcapp.feature.items

import java.math.BigDecimal

/**
 * Phase 15.1 — the typed item (master spec §20/§21/§26) as stored in the
 * "products" operational-record table.
 *
 * This is the payload-level model: the operational store scopes every
 * record by tenant/business/device externally (OperationalScope), and
 * the record id is the store's id, so neither lives inside this class.
 * The domain module's own Item scaffold is a separate, scope-carrying
 * model; it defines no item-type enum, so [ItemType] lives here as the
 * feature's §21 vocabulary.
 *
 * Money fields are plain [BigDecimal] amounts in the business's base
 * currency — §20/§26 define no per-item currency, and inventing one
 * would fabricate data. [openingStock] is a stored quantity only: the
 * inventory ledger is Phase 19, and no computed stock exists until
 * then. [taxPercent] is likewise data only; the tax engine is
 * Phase 17. [unit] is a free-text unit name in this phase — the §25
 * Unit entity and its conversions belong to Phase 16. Image
 * references (§22) arrive with Package 15.4.
 */
data class Item(
    val id: String,
    val name: String,
    val type: ItemType = ItemType.PRODUCT,
    val sku: String? = null,
    val barcode: String? = null,
    val categoryId: String? = null,
    val brandId: String? = null,
    val unit: String? = null,
    val purchaseRate: BigDecimal? = null,
    val saleRate: BigDecimal? = null,
    val wholesaleRate: BigDecimal? = null,
    val dealerRate: BigDecimal? = null,
    val minimumRate: BigDecimal? = null,
    val mrp: BigDecimal? = null,
    val taxPercent: BigDecimal? = null,
    val openingStock: BigDecimal? = null,
    val description: String? = null,
)

/** Item types (§21). */
enum class ItemType {
    PRODUCT,
    SERVICE,
    COMBO,
    BUNDLE,
    MANUFACTURED,
    EXPENSE,
}
