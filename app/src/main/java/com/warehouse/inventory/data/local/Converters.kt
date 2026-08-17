package com.warehouse.inventory.data.local

import androidx.room.TypeConverter
import com.warehouse.inventory.data.local.entity.UserRole

class Converters {
    @TypeConverter
    fun fromRole(role: UserRole): String = role.name

    @TypeConverter
    fun toRole(value: String): UserRole = runCatching { UserRole.valueOf(value) }.getOrDefault(UserRole.VIEWER)
}
