package com.lubrcapp.lubrcapp.core.store.repository

import com.lubrcapp.lubrcapp.core.repository.AuditActions
import com.lubrcapp.lubrcapp.core.repository.BusinessBootstrap
import com.lubrcapp.lubrcapp.core.repository.BusinessSetupRepository
import com.lubrcapp.lubrcapp.core.repository.BusinessSetupSettingKeys
import com.lubrcapp.lubrcapp.core.repository.CreateInitialBusinessRequest
import com.lubrcapp.lubrcapp.core.repository.DeviceIdentityProvider
import com.lubrcapp.lubrcapp.core.store.db.DatabaseTransactionRunner
import com.lubrcapp.lubrcapp.core.store.db.dao.AccessDao
import com.lubrcapp.lubrcapp.core.store.db.dao.AuditDao
import com.lubrcapp.lubrcapp.core.store.db.dao.BusinessDao
import com.lubrcapp.lubrcapp.core.store.db.dao.SettingsDao
import com.lubrcapp.lubrcapp.core.store.db.dao.TenantDao
import com.lubrcapp.lubrcapp.core.store.db.dao.UserDao
import com.lubrcapp.lubrcapp.core.store.db.entity.AuditLogEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.BusinessEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.SettingEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.TenantEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.UserBusinessEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.UserEntity
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.SettingId
import com.lubrcapp.lubrcapp.domain.common.SyncStatus
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.UserId
import com.lubrcapp.lubrcapp.domain.common.IdGenerator
import com.lubrcapp.lubrcapp.domain.common.businessId
import com.lubrcapp.lubrcapp.domain.common.settingId
import com.lubrcapp.lubrcapp.domain.common.tenantId
import com.lubrcapp.lubrcapp.domain.common.userBusinessId
import com.lubrcapp.lubrcapp.domain.common.userId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomBusinessSetupRepository @Inject constructor(
    private val tenantDao: TenantDao,
    private val businessDao: BusinessDao,
    private val userDao: UserDao,
    private val accessDao: AccessDao,
    private val auditDao: AuditDao,
    private val settingsDao: SettingsDao,
    private val transactionRunner: DatabaseTransactionRunner,
    private val deviceIdentityProvider: DeviceIdentityProvider,
    private val idGenerator: IdGenerator,
) : BusinessSetupRepository {

    override fun observeBootstrap(): Flow<BusinessBootstrap?> =
        businessDao.observeFirstActive().map { business ->
            business?.let { buildBootstrap(it) }
        }

    override suspend fun getBootstrap(): BusinessBootstrap? =
        businessDao.getFirstActive()?.let { buildBootstrap(it) }

    override suspend fun createInitialBusiness(
        request: CreateInitialBusinessRequest,
    ): BusinessBootstrap {
        val deviceId = deviceIdentityProvider.getOrCreate()
        val tenantId = idGenerator.tenantId()
        val businessId = idGenerator.businessId()
        val ownerUserId = idGenerator.userId()
        val membershipId = idGenerator.userBusinessId()
        val now = Instant.now()
        val nowMillis = now.toEpochMilli()
        val syncStatus = SyncStatus.LOCAL_ONLY.name

        transactionRunner.run {
            check(businessDao.getFirstActive() == null) {
                "Initial business already exists"
            }

            tenantDao.upsert(
                TenantEntity(
                    id = tenantId.value,
                    name = request.businessName.trim(),
                    createdAt = nowMillis,
                    createdBy = ownerUserId.value,
                    updatedAt = nowMillis,
                    updatedBy = ownerUserId.value,
                    deviceId = deviceId.value,
                    syncStatus = syncStatus,
                )
            )

            businessDao.upsert(
                BusinessEntity(
                    id = businessId.value,
                    tenantId = tenantId.value,
                    name = request.businessName.trim(),
                    businessType = request.businessType.trim(),
                    phone = request.phone.trim(),
                    email = request.email.trim(),
                    address = request.address.trim(),
                    country = request.countryCode.trim().uppercase(),
                    postalCode = request.postalCode.trim(),
                    website = request.website.trim(),
                    taxNumber = request.taxNumber?.trim()?.takeIf(String::isNotEmpty),
                    currencyCode = request.currency.value,
                    timezoneId = request.timezone.id,
                    createdAt = nowMillis,
                    createdBy = ownerUserId.value,
                    updatedAt = nowMillis,
                    updatedBy = ownerUserId.value,
                    deviceId = deviceId.value,
                    syncStatus = syncStatus,
                )
            )

            userDao.upsert(
                UserEntity(
                    id = ownerUserId.value,
                    name = request.ownerName.trim(),
                    phone = request.ownerPhone.trim(),
                    email = request.ownerEmail?.trim()?.takeIf(String::isNotEmpty),
                    createdAt = nowMillis,
                    createdBy = ownerUserId.value,
                    updatedAt = nowMillis,
                    updatedBy = ownerUserId.value,
                    deviceId = deviceId.value,
                    syncStatus = syncStatus,
                )
            )

            accessDao.upsertUserBusiness(
                UserBusinessEntity(
                    id = membershipId.value,
                    tenantId = tenantId.value,
                    businessId = businessId.value,
                    userId = ownerUserId.value,
                    createdAt = nowMillis,
                    createdBy = ownerUserId.value,
                    updatedAt = nowMillis,
                    updatedBy = ownerUserId.value,
                    deviceId = deviceId.value,
                    syncStatus = syncStatus,
                )
            )

            val settings = listOf(
                settingEntity(
                    id = idGenerator.settingId(),
                    tenantId = tenantId,
                    businessId = businessId,
                    key = BusinessSetupSettingKeys.OWNER_USER_ID,
                    value = ownerUserId.value,
                    nowMillis = nowMillis,
                    ownerUserId = ownerUserId,
                    deviceId = deviceId.value,
                    syncStatus = syncStatus,
                ),
                settingEntity(
                    id = idGenerator.settingId(),
                    tenantId = tenantId,
                    businessId = businessId,
                    key = BusinessSetupSettingKeys.LOCALE_LANGUAGE,
                    value = request.languageCode.trim().lowercase(),
                    nowMillis = nowMillis,
                    ownerUserId = ownerUserId,
                    deviceId = deviceId.value,
                    syncStatus = syncStatus,
                ),
                settingEntity(
                    id = idGenerator.settingId(),
                    tenantId = tenantId,
                    businessId = businessId,
                    key = BusinessSetupSettingKeys.TAX_ENABLED,
                    value = request.taxEnabled.toString(),
                    nowMillis = nowMillis,
                    ownerUserId = ownerUserId,
                    deviceId = deviceId.value,
                    syncStatus = syncStatus,
                ),
            )
            for (setting in settings) {
                settingsDao.insert(setting)
            }

            auditDao.insert(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    tenantId = tenantId.value,
                    businessId = businessId.value,
                    actorUserId = ownerUserId.value,
                    deviceId = deviceId.value,
                    entityType = "BUSINESS",
                    entityId = businessId.value,
                    action = AuditActions.BUSINESS_CREATED,
                    reason = "initial_setup",
                    afterJson = "{\"tenantId\":\"" + tenantId.value + "\",\"ownerUserId\":\"" + ownerUserId.value + "\"}",
                    createdAt = nowMillis,
                    syncStatus = syncStatus,
                )
            )
        }

        return BusinessBootstrap(
            tenantId = tenantId,
            businessId = businessId,
            ownerUserId = ownerUserId,
            businessName = request.businessName.trim(),
            languageCode = request.languageCode.trim().lowercase(),
        )
    }

    private suspend fun buildBootstrap(
        business: BusinessEntity,
    ): BusinessBootstrap? {
        val ownerSetting = settingsDao.get(
            business.tenantId,
            business.id,
            BusinessSetupSettingKeys.OWNER_USER_ID,
        ) ?: return null
        val languageSetting = settingsDao.get(
            business.tenantId,
            business.id,
            BusinessSetupSettingKeys.LOCALE_LANGUAGE,
        ) ?: return null

        return BusinessBootstrap(
            tenantId = TenantId(business.tenantId),
            businessId = BusinessId(business.id),
            ownerUserId = UserId(ownerSetting.value),
            businessName = business.name,
            languageCode = languageSetting.value,
        )
    }

    private fun settingEntity(
        id: SettingId,
        tenantId: TenantId,
        businessId: BusinessId,
        key: String,
        value: String,
        nowMillis: Long,
        ownerUserId: UserId,
        deviceId: String,
        syncStatus: String,
    ) = SettingEntity(
        id = id.value,
        tenantId = tenantId.value,
        businessId = businessId.value,
        settingKey = key,
        value = value,
        createdAt = nowMillis,
        createdBy = ownerUserId.value,
        updatedAt = nowMillis,
        updatedBy = ownerUserId.value,
        deviceId = deviceId,
        syncStatus = syncStatus,
    )
}
