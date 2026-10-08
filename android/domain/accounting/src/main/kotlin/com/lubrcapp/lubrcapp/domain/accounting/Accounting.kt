package com.lubrcapp.lubrcapp.domain.accounting

import com.lubrcapp.lubrcapp.domain.common.AccountId
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.EntityMetadata
import com.lubrcapp.lubrcapp.domain.common.JournalEntryId
import com.lubrcapp.lubrcapp.domain.common.JournalLineId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.TenantId
import java.time.LocalDate

data class Account(
    val id: AccountId,
    val tenantId: TenantId,
    val businessId: BusinessId,
    val code: String,
    val name: String,
    val type: AccountType,
    val parentId: AccountId? = null,
    val isActive: Boolean = true,
    val metadata: EntityMetadata,
)

data class JournalEntry(
    val id: JournalEntryId,
    val tenantId: TenantId,
    val businessId: BusinessId,
    val date: LocalDate,
    val description: String,
    val referenceNumber: String? = null,
    val lines: List<JournalLine> = emptyList(),
    val metadata: EntityMetadata,
)

data class JournalLine(
    val id: JournalLineId,
    val entryId: JournalEntryId,
    val accountId: AccountId,
    val debit: Money,
    val credit: Money,
    val memo: String? = null,
) {
    init {
        require(debit.currency == credit.currency) {
            "Journal line debit and credit must use the same currency"
        }
    }
}

enum class AccountType {
    ASSET,
    LIABILITY,
    EQUITY,
    INCOME,
    COST_OF_GOODS_SOLD,
    EXPENSE,
    OTHER_INCOME,
}
