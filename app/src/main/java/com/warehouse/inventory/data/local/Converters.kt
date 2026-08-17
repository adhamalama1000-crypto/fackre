package com.warehouse.inventory.data.local

import androidx.room.TypeConverter
import com.warehouse.inventory.data.local.entity.AdjustmentReason
import com.warehouse.inventory.data.local.entity.AuditAction
import com.warehouse.inventory.data.local.entity.ProductUnit
import com.warehouse.inventory.data.local.entity.TransactionType
import com.warehouse.inventory.data.local.entity.UserRole

/**
 * Enums are persisted by `name`, not ordinal, so reordering an enum can never silently
 * remap existing rows. Unknown values decode to a safe default (or null) instead of
 * throwing, which keeps a database written by a newer build readable by an older one.
 */
class Converters {
    @TypeConverter
    fun fromRole(role: UserRole): String = role.name

    @TypeConverter
    fun toRole(value: String): UserRole =
        runCatching { UserRole.valueOf(value) }.getOrDefault(UserRole.VIEWER)

    @TypeConverter
    fun fromTransactionType(type: TransactionType): String = type.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType =
        runCatching { TransactionType.valueOf(value) }.getOrDefault(TransactionType.ADJUSTMENT)

    @TypeConverter
    fun fromAdjustmentReason(reason: AdjustmentReason?): String? = reason?.name

    @TypeConverter
    fun toAdjustmentReason(value: String?): AdjustmentReason? =
        value?.let { runCatching { AdjustmentReason.valueOf(it) }.getOrDefault(AdjustmentReason.OTHER) }

    @TypeConverter
    fun fromProductUnit(unit: ProductUnit): String = unit.name

    @TypeConverter
    fun toProductUnit(value: String): ProductUnit =
        runCatching { ProductUnit.valueOf(value) }.getOrDefault(ProductUnit.PIECE)

    @TypeConverter
    fun fromAuditAction(action: AuditAction): String = action.name

    @TypeConverter
    fun toAuditAction(value: String): AuditAction =
        runCatching { AuditAction.valueOf(value) }.getOrDefault(AuditAction.PERMISSION_DENIED)
}
