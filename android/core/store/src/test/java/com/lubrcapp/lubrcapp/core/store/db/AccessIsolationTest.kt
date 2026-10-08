package com.lubrcapp.lubrcapp.core.store.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lubrcapp.lubrcapp.core.store.db.entity.BusinessEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.PermissionEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.RoleEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.RolePermissionEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.TenantEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.UserBusinessEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.UserBusinessRoleEntity
import com.lubrcapp.lubrcapp.core.store.db.entity.UserEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AccessIsolationTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun rolePermissionsCannotLeakAcrossBusinesses() = runBlocking {
        val now = 1_000L
        database.tenantDao().upsert(tenant("tenant-a", now))
        database.businessDao().upsert(business("business-a", "tenant-a", now))
        database.businessDao().upsert(business("business-b", "tenant-a", now))
        database.userDao().upsert(user("user-1", now))

        val access = database.accessDao()
        access.upsertPermission(PermissionEntity("perm-profit", "profit.view"))
        access.upsertPermission(PermissionEntity("perm-sales", "sales.view"))

        access.upsertUserBusiness(membership("ub-a", "business-a", now))
        access.upsertUserBusiness(membership("ub-b", "business-b", now))
        access.upsertRole(role("role-a", "business-a", "Manager", now))
        access.upsertRole(role("role-b", "business-b", "Manager", now))

        access.addUserBusinessRole(
            UserBusinessRoleEntity("tenant-a", "business-a", "ub-a", "role-a")
        )
        access.addUserBusinessRole(
            UserBusinessRoleEntity("tenant-a", "business-b", "ub-b", "role-b")
        )
        access.addRolePermission(
            RolePermissionEntity("tenant-a", "business-a", "role-a", "perm-profit")
        )
        access.addRolePermission(
            RolePermissionEntity("tenant-a", "business-b", "role-b", "perm-sales")
        )

        assertEquals(
            listOf("profit.view"),
            access.getPermissionKeys("tenant-a", "business-a", "user-1"),
        )
        assertEquals(
            listOf("sales.view"),
            access.getPermissionKeys("tenant-a", "business-b", "user-1"),
        )
    }

    private fun tenant(id: String, now: Long) = TenantEntity(
        id = id,
        name = id,
        createdAt = now,
        updatedAt = now,
        deviceId = "device-1",
        syncStatus = "LOCAL_ONLY",
    )

    private fun business(id: String, tenantId: String, now: Long) = BusinessEntity(
        id = id,
        tenantId = tenantId,
        name = id,
        businessType = "OTHER",
        currencyCode = "PKR",
        timezoneId = "Asia/Karachi",
        createdAt = now,
        updatedAt = now,
        deviceId = "device-1",
        syncStatus = "LOCAL_ONLY",
    )

    private fun user(id: String, now: Long) = UserEntity(
        id = id,
        name = id,
        createdAt = now,
        updatedAt = now,
        deviceId = "device-1",
        syncStatus = "LOCAL_ONLY",
    )

    private fun membership(id: String, businessId: String, now: Long) = UserBusinessEntity(
        id = id,
        tenantId = "tenant-a",
        businessId = businessId,
        userId = "user-1",
        createdAt = now,
        updatedAt = now,
        deviceId = "device-1",
        syncStatus = "LOCAL_ONLY",
    )

    private fun role(id: String, businessId: String, name: String, now: Long) = RoleEntity(
        id = id,
        tenantId = "tenant-a",
        businessId = businessId,
        name = name,
        createdAt = now,
        updatedAt = now,
        deviceId = "device-1",
        syncStatus = "LOCAL_ONLY",
    )
}
