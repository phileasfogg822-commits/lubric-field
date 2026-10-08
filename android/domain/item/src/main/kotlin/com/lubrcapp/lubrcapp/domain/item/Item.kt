package com.lubrcapp.lubrcapp.domain.item

import com.lubrcapp.lubrcapp.domain.common.BrandId
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.CategoryId
import com.lubrcapp.lubrcapp.domain.common.EntityMetadata
import com.lubrcapp.lubrcapp.domain.common.ItemId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.Percentage
import com.lubrcapp.lubrcapp.domain.common.Quantity
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.UnitId

data class Item(
    val id: ItemId,
    val tenantId: TenantId,
    val businessId: BusinessId,
    val name: String,
    val sku: String? = null,
    val barcode: String? = null,
    val description: String? = null,
    val categoryId: CategoryId? = null,
    val brandId: BrandId? = null,
    val unitId: UnitId? = null,
    val purchasePrice: Money? = null,
    val salePrice: Money? = null,
    val taxRate: Percentage = Percentage.ZERO,
    val hasBatchTracking: Boolean = false,
    val hasSerialTracking: Boolean = false,
    val reorderLevel: Quantity = Quantity.ZERO,
    val isStockTracked: Boolean = true,
    val isActive: Boolean = true,
    val metadata: EntityMetadata,
)
