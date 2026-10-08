package com.lubrcapp.lubrcapp.domain.inventory

import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.EntityMetadata
import com.lubrcapp.lubrcapp.domain.common.ItemId
import com.lubrcapp.lubrcapp.domain.common.Quantity
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.WarehouseId
import java.time.LocalDate

data class StockLevel(
    val tenantId: TenantId,
    val businessId: BusinessId,
    val itemId: ItemId,
    val warehouseId: WarehouseId,
    val quantity: Quantity = Quantity.ZERO,
    val reservedQuantity: Quantity = Quantity.ZERO,
    val batchNumber: String? = null,
    val serialNumber: String? = null,
    val expiryDate: LocalDate? = null,
    val metadata: EntityMetadata,
) {
    val availableQuantity: Quantity
        get() = quantity - reservedQuantity
}

data class Warehouse(
    val id: WarehouseId,
    val tenantId: TenantId,
    val businessId: BusinessId,
    val name: String,
    val isDefault: Boolean = false,
    val isActive: Boolean = true,
    val metadata: EntityMetadata,
)
