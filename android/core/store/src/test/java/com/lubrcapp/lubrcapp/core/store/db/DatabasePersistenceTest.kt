package com.lubrcapp.lubrcapp.core.store.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lubrcapp.lubrcapp.core.store.db.entity.BusinessEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.TenantEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DatabasePersistenceTest {
    private val databaseName = "phase3-reopen-test.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun recordsSurviveDatabaseCloseAndReopen() = runBlocking {
        val first = openDatabase()
        first.tenantDao().upsert(
            TenantEntity(
                id = "tenant-a",
                name = "Tenant A",
                createdAt = 1_000L,
                updatedAt = 1_000L,
                deviceId = "device-1",
                syncStatus = "LOCAL_ONLY",
            )
        )
        first.businessDao().upsert(
            BusinessEntity(
                id = "business-a",
                tenantId = "tenant-a",
                name = "Business A",
                businessType = "OTHER",
                currencyCode = "PKR",
                timezoneId = "Asia/Karachi",
                createdAt = 1_000L,
                updatedAt = 1_000L,
                deviceId = "device-1",
                syncStatus = "LOCAL_ONLY",
            )
        )
        first.close()

        val second = openDatabase()
        assertNotNull(second.tenantDao().getById("tenant-a"))
        assertNotNull(second.businessDao().getById("tenant-a", "business-a"))
        second.close()
    }

    private fun openDatabase(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(*DatabaseMigrations.ALL)
            .allowMainThreadQueries()
            .build()
}
