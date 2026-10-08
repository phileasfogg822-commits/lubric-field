package com.lubrcapp.lubrcapp.core.store.db

import androidx.room.TypeConverter
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.SyncStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DomainTypeConverters {
    @TypeConverter
    fun bigDecimalToString(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun stringToBigDecimal(value: String?): BigDecimal? = value?.let(::BigDecimal)

    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun localDateToIso(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun isoToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun zoneIdToString(value: ZoneId?): String? = value?.id

    @TypeConverter
    fun stringToZoneId(value: String?): ZoneId? = value?.let(ZoneId::of)

    @TypeConverter
    fun currencyCodeToString(value: CurrencyCode?): String? = value?.value

    @TypeConverter
    fun stringToCurrencyCode(value: String?): CurrencyCode? = value?.let(CurrencyCode::of)

    @TypeConverter
    fun syncStatusToString(value: SyncStatus?): String? = value?.name

    @TypeConverter
    fun stringToSyncStatus(value: String?): SyncStatus? = value?.let(SyncStatus::valueOf)
}
