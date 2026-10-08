package com.lubrcapp.lubrcapp.domain.common

import java.util.UUID

interface DomainId {
    val value: String
}

private fun validateId(value: String) {
    require(value.isNotBlank()) { "Domain IDs cannot be blank" }
}

@JvmInline value class TenantId(override val value: String) : DomainId { init { validateId(value) } }
@JvmInline value class BusinessId(override val value: String) : DomainId { init { validateId(value) } }
@JvmInline value class BranchId(override val value: String) : DomainId { init { validateId(value) } }
@JvmInline value class WarehouseId(override val value: String) : DomainId { init { validateId(value) } }
@JvmInline value class UserId(override val value: String) : DomainId { init { validateId(value) } }
@JvmInline value class UserBusinessId(override val value: String) : DomainId { init { validateId(value) } }
@JvmInline value class RoleId(override val value: String) : DomainId { init { validateId(value) } }
@JvmInline value class PermissionId(override val value: String) : DomainId { init { validateId(value) } }
@JvmInline value class PartyId(override val value: String) : DomainId { init { validateId(value) } }
@JvmInline value class PartyGroupId(override val value: String) : DomainId { init { validateId(value) } }
@JvmInline value class ItemId(override val value: String) : DomainId { init { validateId(value) } }
@JvmInline value class CategoryId(override val value: String) : DomainId { init { validateId(value) } }
@JvmInline value class BrandId(override val value: String) : DomainId { init { validateId(value) } }
@JvmInline value class UnitId(override val value: String) : DomainId { init { validateId(value) } }
@JvmInline value class TransactionId(override val value: String) : DomainId { init { validateId(value) } }
@JvmInline value class TransactionLineId(override val value: String) : DomainId { init { validateId(value) } }
@JvmInline value class InvoiceId(override val value: String) : DomainId { init { validateId(value) } }
@JvmInline value class OrderId(override val value: String) : DomainId { init { validateId(value) } }
@JvmInline value class PurchaseId(override val value: String) : DomainId { init { validateId(value) } }
@JvmInline value class PaymentId(override val value: String) : DomainId { init { validateId(value) } }
@JvmInline value class PaymentMethodId(override val value: String) : DomainId { init { validateId(value) } }
@JvmInline value class AccountId(override val value: String) : DomainId { init { validateId(value) } }
@JvmInline value class JournalEntryId(override val value: String) : DomainId { init { validateId(value) } }
@JvmInline value class JournalLineId(override val value: String) : DomainId { init { validateId(value) } }
@JvmInline value class DeviceId(override val value: String) : DomainId { init { validateId(value) } }
@JvmInline value class SyncOperationId(override val value: String) : DomainId { init { validateId(value) } }

fun interface IdGenerator {
    fun generate(): String
}

object UuidIdGenerator : IdGenerator {
    override fun generate(): String = UUID.randomUUID().toString()
}

fun IdGenerator.tenantId() = TenantId(generate())
fun IdGenerator.businessId() = BusinessId(generate())
fun IdGenerator.branchId() = BranchId(generate())
fun IdGenerator.warehouseId() = WarehouseId(generate())
fun IdGenerator.userId() = UserId(generate())
fun IdGenerator.userBusinessId() = UserBusinessId(generate())
fun IdGenerator.roleId() = RoleId(generate())
fun IdGenerator.permissionId() = PermissionId(generate())
fun IdGenerator.partyId() = PartyId(generate())
fun IdGenerator.partyGroupId() = PartyGroupId(generate())
fun IdGenerator.itemId() = ItemId(generate())
fun IdGenerator.categoryId() = CategoryId(generate())
fun IdGenerator.brandId() = BrandId(generate())
fun IdGenerator.unitId() = UnitId(generate())
fun IdGenerator.transactionId() = TransactionId(generate())
fun IdGenerator.transactionLineId() = TransactionLineId(generate())
fun IdGenerator.invoiceId() = InvoiceId(generate())
fun IdGenerator.orderId() = OrderId(generate())
fun IdGenerator.purchaseId() = PurchaseId(generate())
fun IdGenerator.paymentId() = PaymentId(generate())
fun IdGenerator.paymentMethodId() = PaymentMethodId(generate())
fun IdGenerator.accountId() = AccountId(generate())
fun IdGenerator.journalEntryId() = JournalEntryId(generate())
fun IdGenerator.journalLineId() = JournalLineId(generate())
fun IdGenerator.deviceId() = DeviceId(generate())
fun IdGenerator.syncOperationId() = SyncOperationId(generate())
