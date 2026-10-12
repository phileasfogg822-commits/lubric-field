package com.lubrcapp.lubrcapp.core.store.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lubrcapp.lubrcapp.core.repository.GetEntityAuditUseCase
import com.lubrcapp.lubrcapp.core.repository.ObserveAuditEventsUseCase
import com.lubrcapp.lubrcapp.core.repository.AuditActions
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
            auditDao = database.auditDao(),
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
    fun deactivatedCustomRoleStopsGrantingCapabilities() = runBlocking {
        repository.ensureOwnerAccess(TenantId("tenant-a"), BusinessId("business-a"))
        val member = repository.createBusinessUser(
            ownerContext,
            CreateBusinessUserRequest(name = "Stock User"),
        )
        val role = repository.createRole(
            ownerContext,
            CreateBusinessRoleRequest(
                name = "Stock",
                capabilities = setOf(Capabilities.INVENTORY_ADJUST),
            ),
        )
        repository.assignRoles(
            ownerContext,
            member.membershipId,
            setOf(role.roleId),
        )

        val memberContext = ownerContext.copy(userId = member.userId)
        assertTrue(
            repository.authorization(memberContext)
                .allows(Capabilities.INVENTORY_ADJUST)
        )

        repository.setRoleActive(ownerContext, role.roleId, false)

        assertFalse(
            repository.authorization(memberContext)
                .allows(Capabilities.INVENTORY_ADJUST)
        )
    }

    @Test
    fun directRepositoryMutationStillDeniesUnprivilegedActor() = runBlocking {
        repository.ensureOwnerAccess(TenantId("tenant-a"), BusinessId("business-a"))
        val member = repository.createBusinessUser(
            ownerContext,
            CreateBusinessUserRequest(name = "Unprivileged Direct"),
        )
        val unprivileged = ownerContext.copy(userId = member.userId)

        val failure = runCatching {
            repository.createRole(
                unprivileged,
                CreateBusinessRoleRequest(
                    name = "Should Fail",
                    capabilities = setOf(Capabilities.REPORTS_VIEW),
                ),
            )
        }

        assertTrue(failure.exceptionOrNull() is AuthorizationDeniedException)
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


    @Test
    fun creatingUserWritesRedactedAuditEntry() = runBlocking {
        repository.ensureOwnerAccess(TenantId("tenant-a"), BusinessId("business-a"))

        val member = repository.createBusinessUser(
            ownerContext,
            CreateBusinessUserRequest(
                name = "Sensitive Person",
                phone = "+123456789",
                email = "sensitive@example.test",
            ),
        )

        val events = database.auditDao().getForEntity(
            "tenant-a",
            "business-a",
            "USER_MEMBERSHIP",
            member.membershipId.value,
        )

        assertEquals(1, events.size)
        val event = events.single()
        assertEquals(AuditActions.USER_CREATED, event.action)
        assertEquals("owner-a", event.actorUserId)
        assertTrue(event.afterJson.orEmpty().contains(member.userId.value))
        assertFalse(event.afterJson.orEmpty().contains("Sensitive Person"))
        assertFalse(event.afterJson.orEmpty().contains("+123456789"))
        assertFalse(event.afterJson.orEmpty().contains("sensitive@example.test"))
    }

    @Test
    fun deniedRoleMutationWritesAuthorizationAudit() = runBlocking {
        repository.ensureOwnerAccess(TenantId("tenant-a"), BusinessId("business-a"))
        val member = repository.createBusinessUser(
            ownerContext,
            CreateBusinessUserRequest(name = "No Privilege"),
        )
        val unprivileged = ownerContext.copy(userId = member.userId)

        val failure = runCatching {
            repository.createRole(
                unprivileged,
                CreateBusinessRoleRequest(
                    name = "Forbidden",
                    capabilities = setOf(Capabilities.REPORTS_VIEW),
                ),
            )
        }

        assertTrue(failure.exceptionOrNull() is AuthorizationDeniedException)
        val events = database.auditDao().getForEntity(
            "tenant-a",
            "business-a",
            "CAPABILITY",
            Capabilities.ROLES_MANAGE.value,
        )
        assertEquals(1, events.size)
        assertEquals(AuditActions.AUTHORIZATION_DENIED, events.single().action)
        assertEquals(member.userId.value, events.single().actorUserId)
    }

    @Test
    fun auditHistoryRemainsBusinessScoped() = runBlocking {
        repository.ensureOwnerAccess(TenantId("tenant-a"), BusinessId("business-a"))
        val role = repository.createRole(
            ownerContext,
            CreateBusinessRoleRequest(
                name = "Scoped",
                capabilities = setOf(Capabilities.REPORTS_VIEW),
            ),
        )

        val ownEvents = database.auditDao().getForEntity(
            "tenant-a",
            "business-a",
            "ROLE",
            role.roleId.value,
        )
        val otherEvents = database.auditDao().getForEntity(
            "tenant-b",
            "business-b",
            "ROLE",
            role.roleId.value,
        )

        assertEquals(1, ownEvents.size)
        assertTrue(otherEvents.isEmpty())
    }


    @Test
    fun auditUseCasesDenyMemberWithoutAuditView() = runBlocking {
        repository.ensureOwnerAccess(TenantId("tenant-a"), BusinessId("business-a"))
        val member = repository.createBusinessUser(
            ownerContext,
            CreateBusinessUserRequest(name = "Audit Denied"),
        )
        val unprivileged = ownerContext.copy(userId = member.userId)
        val auditRepository = RoomAuditRepository(database.auditDao())
        val authorization = AuthorizationService(repository)

        val entityRead = runCatching {
            GetEntityAuditUseCase(auditRepository, authorization)(
                unprivileged, "USER_MEMBERSHIP", member.membershipId.value,
            )
        }
        val streamRead = runCatching {
            ObserveAuditEventsUseCase(auditRepository, authorization)(unprivileged)
        }

        assertTrue(entityRead.exceptionOrNull() is AuthorizationDeniedException)
        assertTrue(streamRead.exceptionOrNull() is AuthorizationDeniedException)
    }

    @Test
    fun auditUseCasesDoNotLeakOtherTenantRecords() = runBlocking {
        repository.ensureOwnerAccess(TenantId("tenant-a"), BusinessId("business-a"))
        repository.ensureOwnerAccess(TenantId("tenant-b"), BusinessId("business-b"))
        val role = repository.createRole(
            ownerContext,
            CreateBusinessRoleRequest(
                name = "Confidential",
                capabilities = setOf(Capabilities.REPORTS_VIEW),
            ),
        )
        val auditRepository = RoomAuditRepository(database.auditDao())
        val authorization = AuthorizationService(repository)
        val getAudit = GetEntityAuditUseCase(auditRepository, authorization)
        val otherOwner = TenantContext(
            TenantId("tenant-b"), BusinessId("business-b"), UserId("owner-b"),
        )

        assertEquals(1, getAudit(ownerContext, "ROLE", role.roleId.value).size)
        assertTrue(getAudit(otherOwner, "ROLE", role.roleId.value).isEmpty())
        assertTrue(
            ObserveAuditEventsUseCase(auditRepository, authorization)(otherOwner)
                .first().none { it.entityId == role.roleId.value }
        )
    }


    @Test
    fun transactionRunnerRollsBackWritesWhenLaterOperationFails() = runBlocking {
        val original = database.userDao().getById("user-new")
        assertTrue(original == null)
        val failure = runCatching {
            DatabaseTransactionRunner(database).run {
                database.userDao().upsert(
                    UserEntity(
                        id = "user-new",
                        name = "Should Roll Back",
                        createdAt = 1L,
                        updatedAt = 1L,
                        deviceId = "device-1",
                        syncStatus = "LOCAL_ONLY",
                    )
                )
                error("simulated audit persistence failure")
            }
        }
        assertTrue(failure.isFailure)
        assertTrue(database.userDao().getById("user-new") == null)
    }


    @Test
    fun revokedMembershipImmediatelyLosesPreviouslyGrantedCapabilities() = runBlocking {
        repository.ensureOwnerAccess(TenantId("tenant-a"), BusinessId("business-a"))
        val member = repository.createBusinessUser(
            ownerContext, CreateBusinessUserRequest(name = "Temporary Staff"),
        )
        val role = repository.createRole(
            ownerContext,
            CreateBusinessRoleRequest(
                name = "Temporary Role",
                capabilities = setOf(Capabilities.SALES_VIEW),
            ),
        )
        repository.assignRoles(ownerContext, member.membershipId, setOf(role.roleId))
        val memberContext = ownerContext.copy(userId = member.userId)
        assertTrue(repository.authorization(memberContext).allows(Capabilities.SALES_VIEW))

        repository.setMembershipActive(ownerContext, member.membershipId, false)

        assertFalse(repository.authorization(memberContext).allows(Capabilities.SALES_VIEW))
        assertTrue(repository.authorization(memberContext).capabilities.isEmpty())
    }

    @Test
    fun auditPayloadNeverContainsProvisionedContactDetails() = runBlocking {
        repository.ensureOwnerAccess(TenantId("tenant-a"), BusinessId("business-a"))
        val member = repository.createBusinessUser(
            ownerContext,
            CreateBusinessUserRequest(
                name = "Confidential Person",
                phone = "+10000000001",
                email = "private-audit@example.test",
            ),
        )
        val entries = database.auditDao().getForEntity(
            "tenant-a", "business-a", "USER_MEMBERSHIP", member.membershipId.value,
        )
        assertEquals(1, entries.size)
        val payload = listOfNotNull(
            entries.single().beforeJson,
            entries.single().afterJson,
            entries.single().reason,
        ).joinToString(" ")
        assertFalse(payload.contains("Confidential Person"))
        assertFalse(payload.contains("+10000000001"))
        assertFalse(payload.contains("private-audit@example.test"))
    }


    @Test
    fun auditInsertFailureRollsBackBusinessUserAndMembership() = runBlocking {
        repository.ensureOwnerAccess(TenantId("tenant-a"), BusinessId("business-a"))
        database.openHelper.writableDatabase.execSQL(
            """CREATE TRIGGER fail_audit_insert BEFORE INSERT ON audit_logs
               BEGIN SELECT RAISE(ABORT, 'audit write unavailable'); END"""
        )
        try {
            val result = runCatching {
                repository.createBusinessUser(
                    ownerContext,
                    CreateBusinessUserRequest(name = "Must Not Persist"),
                )
            }
            assertTrue("The audit insertion must fail", result.isFailure)
            assertTrue("User insertion must roll back", database.userDao().getById("user-new") == null)
            assertTrue(
                "Membership insertion must roll back",
                database.accessDao().getMembershipById(
                    "tenant-a", "business-a", "membership-new",
                ) == null,
            )
            assertTrue(
                database.auditDao().getForEntity(
                    "tenant-a", "business-a", "USER_MEMBERSHIP", "membership-new",
                ).isEmpty()
            )
        } finally {
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS fail_audit_insert")
        }
    }


    @Test
    fun auditDaoExposesNoUpdateOrDeleteOperation() {
        val methods = com.lubrcapp.lubrcapp.core.store.db.dao.AuditDao::class.java
            .declaredMethods.map { it.name.lowercase() }
        assertTrue(methods.none {
            it.startsWith("update") || it.startsWith("delete") ||
                it.startsWith("remove") || it.startsWith("clear") ||
                it.startsWith("upsert")
        })
    }


    @Test
    fun auditPayloadDoesNotEchoSecretLikeUserInputs() = runBlocking {
        repository.ensureOwnerAccess(TenantId("tenant-a"), BusinessId("business-a"))
        val secrets = listOf(
            "password=secret-password-marker",
            "pin=729184",
            "access_token=access-token-marker",
            "refresh_token=refresh-token-marker",
        )
        val member = repository.createBusinessUser(
            ownerContext,
            CreateBusinessUserRequest(
                name = secrets[0],
                phone = secrets[1],
                email = secrets[2],
            ),
        )
        val events = database.auditDao().getForEntity(
            "tenant-a", "business-a", "USER_MEMBERSHIP", member.membershipId.value,
        )
        assertEquals(1, events.size)
        val audit = events.single().toString()
        secrets.forEach { secret ->
            assertFalse("Audit must not contain input secret", audit.contains(secret))
        }
    }


    @Test
    fun revokedMemberCannotExecuteProtectedRepositoryOperation() = runBlocking {
        repository.ensureOwnerAccess(TenantId("tenant-a"), BusinessId("business-a"))
        val member = repository.createBusinessUser(
            ownerContext, CreateBusinessUserRequest(name = "Former Manager"),
        )
        val role = repository.createRole(
            ownerContext,
            CreateBusinessRoleRequest(
                name = "User Manager",
                capabilities = setOf(Capabilities.USERS_MANAGE),
            ),
        )
        repository.assignRoles(ownerContext, member.membershipId, setOf(role.roleId))
        val actor = ownerContext.copy(userId = member.userId)
        assertTrue(repository.authorization(actor).allows(Capabilities.USERS_MANAGE))
        repository.setMembershipActive(ownerContext, member.membershipId, false)
        val attempt = runCatching {
            repository.createBusinessUser(
                actor, CreateBusinessUserRequest(name = "Unauthorized User"),
            )
        }
        assertTrue(attempt.exceptionOrNull() is AuthorizationDeniedException)
        assertTrue(database.userDao().getById("generated-unauthorized") == null)
    }


    @Test
    fun directSqlAuditTamperingIsCurrentlyPossibleAndNeedsHardening() = runBlocking {
        repository.ensureOwnerAccess(TenantId("tenant-a"), BusinessId("business-a"))
        val member = repository.createBusinessUser(
            ownerContext, CreateBusinessUserRequest(name = "Audit Integrity Probe"),
        )
        val events = database.auditDao().getForEntity(
            "tenant-a", "business-a", "USER_MEMBERSHIP", member.membershipId.value,
        )
        assertEquals(1, events.size)
        val id = events.single().id
        val db = database.openHelper.writableDatabase
        db.execSQL("UPDATE audit_logs SET reason = 'tampered' WHERE id = ?", arrayOf(id))
        val changed = database.auditDao().getForEntity(
            "tenant-a", "business-a", "USER_MEMBERSHIP", member.membershipId.value,
        )
        assertEquals("tampered", changed.single().reason)
        db.execSQL("DELETE FROM audit_logs WHERE id = ?", arrayOf(id))
        assertTrue(
            database.auditDao().getForEntity(
                "tenant-a", "business-a", "USER_MEMBERSHIP", member.membershipId.value,
            ).isEmpty()
        )
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
