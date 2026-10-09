package com.lubrcapp.lubrcapp.core.store.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lubrcapp.lubrcapp.core.repository.AuthorizationDeniedException
import com.lubrcapp.lubrcapp.core.repository.BusinessSetupSettingKeys
import com.lubrcapp.lubrcapp.core.repository.CreateBusinessRoleRequest
import com.lubrcapp.lubrcapp.core.repository.CreateBusinessUserRequest
import com.lubrcapp.lubrcapp.core.repository.DeviceIdentityProvider
import com.lubrcapp.lubrcapp.core.repository.TenantContext
import com.lubrcapp.lubrcapp.core.repository.usecase.AuthorizationService
import com.lubrcapp.lubrcapp.core.repository.usecase.CreateBusinessRoleUseCase
import com.lubrcapp.lubrcapp.core.store.db.AppDatabase
import com.lubrcapp.lubrcapp.core.store.db.DatabaseTransactionRunner
import com.lubrcapp.lubrcapp.core.store.db.entity.BusinessEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.SettingEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.TenantEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.UserBusinessEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.UserEntity
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.IdGenerator
import com.lubrcapp.lubrcapp.domain.common.RoleId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.UserId
import com.lubrcapp.lubrcapp.domain.user.Capabilities
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoomAccessControlRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomAccessControlRepository
    private val ids = SequenceIdGenerator(
        "user-new", "membership-new", "role-sales", "role-other"
    )

    private val ownerContext = TenantContext(
        tenantId = TenantId("tenant-a"),
        businessId = BusinessId("business-a"),
        userId = UserId("owner-a"),
    )

    @Before
    fun setup() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        repository = RoomAccessControlRepository(
            userDao = database.userDao(),
            accessDao = database.accessDao(),
            settingsDao = database.settingsDao(),
            transactionRunner = DatabaseTransactionRunner(database),
            deviceIdentityProvider = FixedDeviceIdentityProvider(),
            idGenerator = ids,
        )

        seedBusiness("tenant-a", "business-a", "owner-a", "membership-owner-a")
        seedBusiness("tenant-b", "business-b", "owner-b", "membership-owner-b")
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun ownerBootstrapGrantsCompleteCapabilityCatalog() = runBlocking {
        repository.ensureOwnerAccess(TenantId("tenant-a"), BusinessId("business-a"))

        val snapshot = repository.authorization(ownerContext)

        assertEquals(Capabilities.ALL, snapshot.capabilities)
        assertTrue(snapshot.roleIds.isNotEmpty())
        assertTrue(snapshot.allows(Capabilities.USERS_MANAGE))
        assertTrue(snapshot.allows(Capabilities.ACCOUNTING_MANAGE))
    }

    @Test
    fun activeMemberWithoutRoleIsDeniedByDefault() = runBlocking {
        repository.ensureOwnerAccess(TenantId("tenant-a"), BusinessId("business-a"))
        val member = repository.createBusinessUser(
            ownerContext,
            CreateBusinessUserRequest(name = "Sales Person"),
        )

        val context = ownerContext.copy(userId = member.userId)
        val snapshot = repository.authorization(context)

        assertTrue(snapshot.capabilities.isEmpty())
        assertFalse(snapshot.allows(Capabilities.SALES_VIEW))
    }

    @Test
    fun customRoleGrantsOnlyAssignedCapabilities() = runBlocking {
        repository.ensureOwnerAccess(TenantId("tenant-a"), BusinessId("business-a"))
        val member = repository.createBusinessUser(
            ownerContext,
            CreateBusinessUserRequest(name = "Sales Person"),
        )
        val role = repository.createRole(
            ownerContext,
            CreateBusinessRoleRequest(
                name = "Sales",
                capabilities = setOf(
                    Capabilities.SALES_VIEW,
                    Capabilities.SALES_CREATE,
                    Capabilities.PARTIES_VIEW,
                ),
            ),
        )

        repository.assignRoles(
            actor = ownerContext,
            membershipId = member.membershipId,
            roleIds = setOf(role.roleId),
        )

        val snapshot = repository.authorization(
            ownerContext.copy(userId = member.userId)
        )

        assertEquals(
            setOf(
                Capabilities.SALES_VIEW,
                Capabilities.SALES_CREATE,
                Capabilities.PARTIES_VIEW,
            ),
            snapshot.capabilities,
        )
        assertFalse(snapshot.allows(Capabilities.COST_VIEW))
        assertFalse(snapshot.allows(Capabilities.ROLES_MANAGE))
    }

    @Test
    fun roleFromAnotherBusinessCannotBeAssigned() = runBlocking {
        repository.ensureOwnerAccess(TenantId("tenant-a"), BusinessId("business-a"))
        repository.ensureOwnerAccess(TenantId("tenant-b"), BusinessId("business-b"))

        val member = repository.createBusinessUser(
            ownerContext,
            CreateBusinessUserRequest(name = "Member"),
        )

        val otherContext = TenantContext(
            TenantId("tenant-b"),
            BusinessId("business-b"),
            UserId("owner-b"),
        )
        val otherRole = repository.createRole(
            otherContext,
            CreateBusinessRoleRequest(
                name = "Other Business Role",
                capabilities = setOf(Capabilities.REPORTS_VIEW),
            ),
        )

        val failure = runCatching {
            repository.assignRoles(
                ownerContext,
                member.membershipId,
                setOf(otherRole.roleId),
            )
        }

        assertTrue(failure.isFailure)
        assertTrue(
            repository.authorization(ownerContext.copy(userId = member.userId))
                .capabilities.isEmpty()
        )
    }

    @Test
    fun systemOwnerRoleCannotBeManuallyAssignedOrEdited() = runBlocking {
        repository.ensureOwnerAccess(TenantId("tenant-a"), BusinessId("business-a"))
        val ownerRole = repository.observeRoles(
            TenantId("tenant-a"),
            BusinessId("business-a"),
        ).first().single { it.systemTemplate }

        val member = repository.createBusinessUser(
            ownerContext,
            CreateBusinessUserRequest(name = "Member"),
        )

        assertTrue(
            runCatching {
                repository.assignRoles(
                    ownerContext,
                    member.membershipId,
                    setOf(ownerRole.roleId),
                )
            }.isFailure
        )

        assertTrue(
            runCatching {
                repository.setRoleCapabilities(
                    ownerContext,
                    ownerRole.roleId,
                    setOf(Capabilities.SALES_VIEW),
                )
            }.isFailure
        )
    }

    @Test
    fun managementUseCaseRejectsActorWithoutCapability() = runBlocking {
        repository.ensureOwnerAccess(TenantId("tenant-a"), BusinessId("business-a"))
        val member = repository.createBusinessUser(
            ownerContext,
            CreateBusinessUserRequest(name = "Unprivileged"),
        )
        val unprivileged = ownerContext.copy(userId = member.userId)

        val useCase = CreateBusinessRoleUseCase(
            repository = repository,
            authorization = AuthorizationService(repository),
        )

        val failure = runCatching {
            useCase(
                unprivileged,
                CreateBusinessRoleRequest(
                    name = "Forbidden",
                    capabilities = setOf(Capabilities.SALES_VIEW),
                ),
            )
        }

        assertTrue(failure.exceptionOrNull() is AuthorizationDeniedException)
    }

    private suspend fun seedBusiness(
        tenantId: String,
        businessId: String,
        ownerUserId: String,
        membershipId: String,
    ) {
        database.tenantDao().upsert(
            TenantEntity(
                id = tenantId,
                name = tenantId,
                createdAt = 1L,
                updatedAt = 1L,
                deviceId = "device-1",
                syncStatus = "LOCAL_ONLY",
            )
        )
        database.businessDao().upsert(
            BusinessEntity(
                id = businessId,
                tenantId = tenantId,
                name = businessId,
                businessType = "General",
                country = "US",
                currencyCode = "USD",
                timezoneId = "UTC",
                createdAt = 1L,
                updatedAt = 1L,
                deviceId = "device-1",
                syncStatus = "LOCAL_ONLY",
            )
        )
        database.userDao().upsert(
            UserEntity(
                id = ownerUserId,
                name = ownerUserId,
                createdAt = 1L,
                updatedAt = 1L,
                deviceId = "device-1",
                syncStatus = "LOCAL_ONLY",
            )
        )
        database.accessDao().upsertUserBusiness(
            UserBusinessEntity(
                id = membershipId,
                tenantId = tenantId,
                businessId = businessId,
                userId = ownerUserId,
                createdAt = 1L,
                updatedAt = 1L,
                deviceId = "device-1",
                syncStatus = "LOCAL_ONLY",
            )
        )
        database.settingsDao().insert(
            SettingEntity(
                id = "owner-setting-$businessId",
                tenantId = tenantId,
                businessId = businessId,
                settingKey = BusinessSetupSettingKeys.OWNER_USER_ID,
                value = ownerUserId,
                createdAt = 1L,
                updatedAt = 1L,
                deviceId = "device-1",
                syncStatus = "LOCAL_ONLY",
            )
        )
    }

    private class FixedDeviceIdentityProvider : DeviceIdentityProvider {
        override suspend fun getOrCreate(): DeviceId = DeviceId("device-1")
    }

    private class SequenceIdGenerator(
        vararg values: String,
    ) : IdGenerator {
        private val values = ArrayDeque(values.toList())

        override fun generate(): String =
            values.removeFirstOrNull() ?: "generated-" + System.nanoTime()
    }
}
