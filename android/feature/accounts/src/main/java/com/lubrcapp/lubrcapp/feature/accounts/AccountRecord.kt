package com.lubrcapp.lubrcapp.feature.accounts

import com.lubrcapp.lubrcapp.domain.accounting.Account
import com.lubrcapp.lubrcapp.domain.accounting.AccountType
import com.lubrcapp.lubrcapp.domain.common.AccountId
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.EntityMetadata
import com.lubrcapp.lubrcapp.domain.common.TenantId
import java.time.Instant
import java.util.UUID

/**
 * Phase 20.3 — the typed chart-of-accounts record, stored as
 * an "accounts" record. That table IS sync-whitelisted (and
 * NOT insert-only), so accounts sync like other master data
 * — unlike the local-only journal entries of 20.1.
 *
 * The type is the spec §74 class: ASSET, LIABILITY, EQUITY,
 * INCOME, COST_OF_GOODS_SOLD, EXPENSE, OTHER_INCOME. Custom
 * accounts are ordinary records — there is no separate
 * "system" flag; the default chart is a seed set, not a
 * protected class.
 *
 * Accounts are master data and are never deleted: removal is
 * deactivation (isActive = false), which keeps historical
 * journal lines resolvable. The codec maps a missing or
 * unknown type to OTHER_INCOME — the catch-all class — and
 * falls back to the legacy Phase 12 "description" field for
 * the name, so pre-20.3 rows keep displaying instead of
 * going blank; both defaults are documented, never silent.
 */
data class AccountRecord(
    val id: String,
    val code: String = "",
    val name: String = "",
    val type: AccountType = AccountType.OTHER_INCOME,
    val parentId: String? = null,
    val isActive: Boolean = true,
    /** ISO instant the account was recorded. */
    val createdAt: String = "",
) {
    /**
     * The domain/accounting form of this account. Tenant,
     * business and device context are the caller's (the
     * Phase 19.1 / 20.1 caller-supplies-context pattern).
     */
    fun toDomainAccount(
        tenantId: TenantId,
        businessId: BusinessId,
        deviceId: DeviceId,
        now: Instant,
    ): Account = Account(
        id = AccountId(id),
        tenantId = tenantId,
        businessId = businessId,
        code = code,
        name = name,
        type = type,
        parentId = parentId?.let { AccountId(it) },
        isActive = isActive,
        metadata = EntityMetadata.newLocal(now, deviceId),
    )

    companion object {
        /** Build an account with a fresh id and timestamp. */
        fun of(
            code: String,
            name: String,
            type: AccountType,
            parentId: String? = null,
        ): AccountRecord = AccountRecord(
            id = UUID.randomUUID().toString(),
            code = code,
            name = name,
            type = type,
            parentId = parentId,
            createdAt = Instant.now().toString(),
        )
    }
}
