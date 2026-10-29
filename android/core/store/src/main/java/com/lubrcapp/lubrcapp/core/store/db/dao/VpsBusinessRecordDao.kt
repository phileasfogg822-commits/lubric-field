package com.lubrcapp.lubrcapp.core.store.db.dao
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import com.lubrcapp.lubrcapp.core.store.db.entity.VpsBusinessRecordEntity

@Dao
interface VpsBusinessRecordDao {
    @Query("SELECT * FROM vps_business_records WHERE tenantId = :tenantId AND businessId = :businessId AND entityType = :table ORDER BY entityId")
    fun observeByType(tenantId: String, businessId: String, table: String): Flow<List<VpsBusinessRecordEntity>>

    @Query("SELECT * FROM vps_business_records WHERE tenantId = :tenantId AND businessId = :businessId AND entityType = :table ORDER BY entityId")
    suspend fun listByType(tenantId: String, businessId: String, table: String): List<VpsBusinessRecordEntity>

    @Upsert suspend fun upsert(row: VpsBusinessRecordEntity)
    @Query("SELECT * FROM vps_business_records WHERE tenantId = :tenantId AND businessId = :businessId AND entityType = :table AND entityId = :uuid LIMIT 1")
    suspend fun get(tenantId: String, businessId: String, table: String, uuid: String): VpsBusinessRecordEntity?

    @Query("DELETE FROM vps_business_records WHERE tenantId = :tenantId AND businessId = :businessId")
    suspend fun deleteByScope(tenantId: String, businessId: String): Int
}
