package com.lubrcapp.lubrcapp.core.store.db.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Typed envelope for VPS operational rows. Never overwrites unrelated Room foundation
 * entities; domain repositories can consume validated records from this replica.
 */
@Entity(
    tableName = "vps_business_records",
    primaryKeys = ["tenantId", "businessId", "entityType", "entityId"],
    indices = [Index(value = ["tenantId", "businessId", "entityType"])],
)
data class VpsBusinessRecordEntity(
    val tenantId: String,
    val businessId: String,
    val entityType: String,
    val entityId: String,
    val payloadJson: String,
    val updatedAt: Long,
)
