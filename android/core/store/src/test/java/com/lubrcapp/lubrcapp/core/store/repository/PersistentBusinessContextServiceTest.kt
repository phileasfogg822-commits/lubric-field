package com.lubrcapp.lubrcapp.core.store.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lubrcapp.lubrcapp.core.repository.BusinessSetupSettingKeys
import com.lubrcapp.lubrcapp.core.repository.BusinessSwitchResult
import com.lubrcapp.lubrcapp.core.repository.TenantContext
import com.lubrcapp.lubrcapp.core.store.db.AppDatabase
import com.lubrcapp.lubrcapp.core.store.db.entity.BusinessEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.SettingEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.TenantEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.UserBusinessEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.UserEntity
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.UserId
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PersistentBusinessContextServiceTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase

    private val userId = UserId("user-1")

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        if (database.isOpen) database.close()
    }

    @Test
    fun availableBusinessesAreMembershipScopedAcrossTenants() = runTest {
        seedBusinesses()
        val service = service(
            createDataStore(
                File(Files.createTempDirectory("context-list").toFile(), "context.preferences_pb"),
                backgroundScope,
            )
        )

        val available = service.observeAvailableBusinesses(userId).first()

        assertEquals(listOf("business-a", "business-b"), available.map { it.context.businessId.value })
        assertEquals(listOf("en", "ar"), available.map { it.languageCode })
        assertTrue(available.none { it.context.businessId.value == "business-private" })
    }

    @Test
    fun switchPersistsAcrossServiceRecreationAndTenantBoundariesStayIntact() = runTest {
        seedBusinesses()
        val file = File(Files.createTempDirectory("context-switch").toFile(), "context.preferences_pb")
        val dataStore = createDataStore(file, backgroundScope)
        val first = service(dataStore)

        val initial = first.ensureCurrent(
            userId = userId,
            fallback = TenantContext(
                tenantId = TenantId("tenant-a"),
                businessId = BusinessId("business-a"),
                userId = userId,
            ),
        )
        assertEquals("business-a", initial?.context?.businessId?.value)

        val switched = first.switchBusiness(
            userId = userId,
            tenantId = TenantId("tenant-b"),
            businessId = BusinessId("business-b"),
        )
        assertTrue(switched is BusinessSwitchResult.Switched)
        assertEquals("ar", (switched as BusinessSwitchResult.Switched).business.languageCode)

        val recreated = service(dataStore)
        val restored = recreated.current(userId)

        assertEquals("tenant-b", restored?.context?.tenantId?.value)
        assertEquals("business-b", restored?.context?.businessId?.value)
        assertEquals("AED", restored?.currency?.value)

        val wrongTenant = recreated.switchBusiness(
            userId = userId,
            tenantId = TenantId("tenant-a"),
            businessId = BusinessId("business-b"),
        )
        assertTrue(wrongTenant is BusinessSwitchResult.Unavailable)

        val stillCurrent = recreated.current(userId)
        assertEquals("tenant-b", stillCurrent?.context?.tenantId?.value)
        assertEquals("business-b", stillCurrent?.context?.businessId?.value)
    }

    @Test
    fun unauthorizedBusinessCannotBecomeCurrent() = runTest {
        seedBusinesses()
        val service = service(
            createDataStore(
                File(Files.createTempDirectory("context-denied").toFile(), "context.preferences_pb"),
                backgroundScope,
            )
        )

        service.ensureCurrent(
            userId,
            TenantContext(
                tenantId = TenantId("tenant-a"),
                businessId = BusinessId("business-a"),
                userId = userId,
            ),
        )

        val denied = service.switchBusiness(
            userId = userId,
            tenantId = TenantId("tenant-private"),
            businessId = BusinessId("business-private"),
        )

        assertTrue(denied is BusinessSwitchResult.AccessDenied)
        assertEquals("business-a", service.current(userId)?.context?.businessId?.value)
    }

    @Test
    fun revokedPersistedMembershipIsRejectedAndFallsBackToAccessibleBusiness() = runTest {
        seedBusinesses()
        val file = File(Files.createTempDirectory("context-revoke").toFile(), "context.preferences_pb")
        val dataStore = createDataStore(file, backgroundScope)
        val service = service(dataStore)

        service.ensureCurrent(
            userId,
            TenantContext(
                tenantId = TenantId("tenant-a"),
                businessId = BusinessId("business-a"),
                userId = userId,
            ),
        )
        service.switchBusiness(
            userId = userId,
            tenantId = TenantId("tenant-b"),
            businessId = BusinessId("business-b"),
        )

        database.accessDao().upsertUserBusiness(
            membership(
                id = "membership-b",
                tenantId = "tenant-b",
                businessId = "business-b",
                active = false,
            )
        )

        val recreated = service(dataStore)
        assertNull(recreated.current(userId))

        val recovered = recreated.ensureCurrent(userId)
        assertEquals("business-a", recovered?.context?.businessId?.value)
        assertEquals("tenant-a", recovered?.context?.tenantId?.value)
    }

    @Test
    fun anotherUserCannotReusePersistedContextWithoutMembership() = runTest {
        seedBusinesses()
        val file = File(Files.createTempDirectory("context-user").toFile(), "context.preferences_pb")
        val service = service(createDataStore(file, backgroundScope))

        service.ensureCurrent(
            userId,
            TenantContext(
                tenantId = TenantId("tenant-a"),
                businessId = BusinessId("business-a"),
                userId = userId,
            ),
        )

        val otherUser = UserId("user-2")
        database.userDao().upsert(user("user-2"))

        assertNull(service.current(otherUser))
        assertNull(service.ensureCurrent(otherUser))

        val denied = service.switchBusiness(
            userId = otherUser,
            tenantId = TenantId("tenant-a"),
            businessId = BusinessId("business-a"),
        )
        assertTrue(denied is BusinessSwitchResult.AccessDenied)
    }

    private suspend fun seedBusinesses() {
        database.userDao().upsert(user(userId.value))

        insertTenant("tenant-a", "Tenant A")
        insertTenant("tenant-b", "Tenant B")
        insertTenant("tenant-private", "Private Tenant")

        insertBusiness(
            id = "business-a",
            tenantId = "tenant-a",
            name = "Alpha Business",
            currency = "USD",
        )
        insertBusiness(
            id = "business-b",
            tenantId = "tenant-b",
            name = "Beta Business",
            currency = "AED",
        )
        insertBusiness(
            id = "business-private",
            tenantId = "tenant-private",
            name = "Private Business",
            currency = "EUR",
        )

        database.accessDao().upsertUserBusiness(
            membership("membership-a", "tenant-a", "business-a")
        )
        database.accessDao().upsertUserBusiness(
            membership("membership-b", "tenant-b", "business-b")
        )

        insertLanguage("setting-a", "tenant-a", "business-a", "en")
        insertLanguage("setting-b", "tenant-b", "business-b", "ar")
        insertLanguage("setting-private", "tenant-private", "business-private", "ur")
    }

    private fun service(
        dataStore: DataStore<Preferences>,
    ): PersistentBusinessContextService =
        PersistentBusinessContextService(
            businessDao = database.businessDao(),
            accessDao = database.accessDao(),
            settingsDao = database.settingsDao(),
            dataStore = dataStore,
        )

    private fun createDataStore(
        file: File,
        scope: CoroutineScope,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )

    private suspend fun insertTenant(id: String, name: String) {
        database.tenantDao().upsert(
            TenantEntity(
                id = id,
                name = name,
                createdAt = 1L,
                updatedAt = 1L,
                deviceId = "device-1",
                syncStatus = "LOCAL_ONLY",
            )
        )
    }

    private suspend fun insertBusiness(
        id: String,
        tenantId: String,
        name: String,
        currency: String,
    ) {
        database.businessDao().upsert(
            BusinessEntity(
                id = id,
                tenantId = tenantId,
                name = name,
                businessType = "General",
                country = "US",
                currencyCode = currency,
                timezoneId = "UTC",
                createdAt = 1L,
                updatedAt = 1L,
                deviceId = "device-1",
                syncStatus = "LOCAL_ONLY",
            )
        )
    }

    private suspend fun insertLanguage(
        id: String,
        tenantId: String,
        businessId: String,
        language: String,
    ) {
        database.settingsDao().insert(
            SettingEntity(
                id = id,
                tenantId = tenantId,
                businessId = businessId,
                settingKey = BusinessSetupSettingKeys.LOCALE_LANGUAGE,
                value = language,
                createdAt = 1L,
                updatedAt = 1L,
                deviceId = "device-1",
                syncStatus = "LOCAL_ONLY",
            )
        )
    }

    private fun user(id: String) = UserEntity(
        id = id,
        name = id,
        createdAt = 1L,
        updatedAt = 1L,
        deviceId = "device-1",
        syncStatus = "LOCAL_ONLY",
    )

    private fun membership(
        id: String,
        tenantId: String,
        businessId: String,
        active: Boolean = true,
    ) = UserBusinessEntity(
        id = id,
        tenantId = tenantId,
        businessId = businessId,
        userId = userId.value,
        isActive = active,
        createdAt = 1L,
        updatedAt = 1L,
        deviceId = "device-1",
        syncStatus = "LOCAL_ONLY",
    )
}
