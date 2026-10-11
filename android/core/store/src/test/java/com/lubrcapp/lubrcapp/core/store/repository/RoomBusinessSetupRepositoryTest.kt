package com.lubrcapp.lubrcapp.core.store.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lubrcapp.lubrcapp.core.repository.AuditActions
import com.lubrcapp.lubrcapp.core.repository.BusinessSetupSettingKeys
import com.lubrcapp.lubrcapp.core.repository.CreateInitialBusinessRequest
import com.lubrcapp.lubrcapp.core.repository.DeviceIdentityProvider
import com.lubrcapp.lubrcapp.core.store.db.AppDatabase
import com.lubrcapp.lubrcapp.core.store.db.DatabaseTransactionRunner
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.IdGenerator
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoomBusinessSetupRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private val databaseName = "phase8-business-setup-test.db"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
        database = openDatabase()
    }

    @After
    fun tearDown() {
        if (database.isOpen) database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun createInitialBusinessPersistsWholeOfflineBootstrap() = runBlocking {
        val repository = repository(
            SequenceIdGenerator(
                "tenant-1",
                "business-1",
                "owner-1",
                "membership-1",
                "setting-owner",
                "setting-language",
                "setting-tax",
            )
        )

        val bootstrap = repository.createInitialBusiness(request())

        assertEquals("tenant-1", bootstrap.tenantId.value)
        assertEquals("business-1", bootstrap.businessId.value)
        assertEquals("owner-1", bootstrap.ownerUserId.value)
        assertEquals("Acme Traders", bootstrap.businessName)
        assertEquals("ur", bootstrap.languageCode)

        val business = database.businessDao().getById("tenant-1", "business-1")
        assertNotNull(business)
        assertEquals("NZ", business?.country)
        assertEquals("NZD", business?.currencyCode)
        assertEquals("Wholesale", business?.businessType)
        assertEquals("GST-42", business?.taxNumber)
        assertEquals("Pacific/Auckland", business?.timezoneId)
        assertEquals("device-phase8", business?.deviceId)

        val owner = database.userDao().getById("owner-1")
        assertEquals("Ayesha Owner", owner?.name)

        val membership = database.accessDao().getMembership(
            "tenant-1",
            "business-1",
            "owner-1",
        )
        assertNotNull(membership)

        assertEquals(
            "owner-1",
            database.settingsDao().get(
                "tenant-1",
                "business-1",
                BusinessSetupSettingKeys.OWNER_USER_ID,
            )?.value,
        )
        assertEquals(
            "ur",
            database.settingsDao().get(
                "tenant-1",
                "business-1",
                BusinessSetupSettingKeys.LOCALE_LANGUAGE,
            )?.value,
        )
        assertEquals(
            "true",
            database.settingsDao().get(
                "tenant-1",
                "business-1",
                BusinessSetupSettingKeys.TAX_ENABLED,
            )?.value,
        )

        val audit = database.auditDao().getForEntity(
            "tenant-1",
            "business-1",
            "BUSINESS",
            "business-1",
        ).single()
        assertEquals(AuditActions.BUSINESS_CREATED, audit.action)
        assertEquals("owner-1", audit.actorUserId)
        assertTrue(audit.afterJson.orEmpty().contains("tenant-1"))
        assertFalse(audit.afterJson.orEmpty().contains("Acme Traders"))
        assertFalse(audit.afterJson.orEmpty().contains("Ayesha Owner"))
        assertFalse(audit.afterJson.orEmpty().contains("office@example.test"))
    }

    @Test
    fun bootstrapSurvivesDatabaseCloseAndReopen() = runBlocking {
        val repository = repository(
            SequenceIdGenerator(
                "tenant-1",
                "business-1",
                "owner-1",
                "membership-1",
                "setting-owner",
                "setting-language",
                "setting-tax",
            )
        )
        val created = repository.createInitialBusiness(request())

        database.close()
        database = openDatabase()

        val reopenedRepository = repository(
            SequenceIdGenerator("unused")
        )
        val restored = reopenedRepository.getBootstrap()

        assertEquals(created, restored)
        assertEquals(created, reopenedRepository.observeBootstrap().first())
    }

    @Test
    fun lateConstraintFailureRollsBackEntireInitialSetup() = runBlocking {
        val repository = repository(
            SequenceIdGenerator(
                "tenant-rollback",
                "business-rollback",
                "user-rollback",
                "membership-rollback",
                "duplicate-setting",
                "duplicate-setting",
                "third-setting",
            )
        )

        val failure = runCatching {
            repository.createInitialBusiness(request())
        }

        assertTrue(failure.isFailure)
        assertNull(database.tenantDao().getById("tenant-rollback"))
        assertNull(database.businessDao().getById("tenant-rollback", "business-rollback"))
        assertNull(database.userDao().getById("user-rollback"))
        assertNull(database.businessDao().getFirstActive())
    }

    @Test
    fun secondInitialBusinessIsRejectedWithoutOverwritingFirst() = runBlocking {
        val first = repository(
            SequenceIdGenerator(
                "tenant-1",
                "business-1",
                "owner-1",
                "membership-1",
                "setting-owner",
                "setting-language",
                "setting-tax",
            )
        )
        val created = first.createInitialBusiness(request())

        val second = repository(
            SequenceIdGenerator(
                "tenant-2",
                "business-2",
                "owner-2",
                "membership-2",
                "setting-owner-2",
                "setting-language-2",
                "setting-tax-2",
            )
        )
        val failure = runCatching {
            second.createInitialBusiness(
                request().copy(businessName = "Second Business")
            )
        }

        assertTrue(failure.isFailure)
        assertEquals(created, first.getBootstrap())
        assertNull(database.tenantDao().getById("tenant-2"))
        assertNull(database.businessDao().getById("tenant-2", "business-2"))
    }

    private fun repository(
        idGenerator: IdGenerator,
    ) = RoomBusinessSetupRepository(
        tenantDao = database.tenantDao(),
        businessDao = database.businessDao(),
        userDao = database.userDao(),
        accessDao = database.accessDao(),
        auditDao = database.auditDao(),
        settingsDao = database.settingsDao(),
        transactionRunner = DatabaseTransactionRunner(database),
        deviceIdentityProvider = FixedDeviceIdentityProvider(),
        idGenerator = idGenerator,
    )

    private fun openDatabase(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()

    private fun request() = CreateInitialBusinessRequest(
        languageCode = "ur",
        businessName = "Acme Traders",
        businessType = "Wholesale",
        phone = "+64 9 555 0100",
        email = "office@example.test",
        address = "1 Example Street",
        countryCode = "NZ",
        postalCode = "1010",
        website = "https://example.test",
        currency = CurrencyCode.of("NZD"),
        timezone = ZoneId.of("Pacific/Auckland"),
        taxEnabled = true,
        taxNumber = "GST-42",
        ownerName = "Ayesha Owner",
        ownerPhone = "+64 21 555 0100",
        ownerEmail = "owner@example.test",
    )

    private class FixedDeviceIdentityProvider : DeviceIdentityProvider {
        override suspend fun getOrCreate(): DeviceId = DeviceId("device-phase8")
    }

    private class SequenceIdGenerator(
        vararg values: String,
    ) : IdGenerator {
        private val iterator = values.iterator()
        override fun generate(): String =
            checkNotNull(if (iterator.hasNext()) iterator.next() else null) {
                "No deterministic ID left in test generator"
            }
    }
}
