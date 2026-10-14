package com.lubrcapp.lubrcapp.core.store.db.dao
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lubrcapp.lubrcapp.core.store.db.entity.VpsBusinessRecordEntity

@Dao
interface VpsBusinessRecordDao {
    @Upsert suspend fun upsert(row: VpsBusinessRecordEntity)
    @Query("SELECT * FROM vps_business_records WHERE tenantId = :tenantId AND businessId = :businessId AND entityType = :table AND entityId = :uuid LIMIT 1")
    suspend fun get(tenantId: String, businessId: String, table: String, uuid: String): VpsBusinessRecordEntity?
}
