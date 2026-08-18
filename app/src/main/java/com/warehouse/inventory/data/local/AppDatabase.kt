package com.warehouse.inventory.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.warehouse.inventory.data.local.dao.AuditLogDao
import com.warehouse.inventory.data.local.dao.CategoryDao
import com.warehouse.inventory.data.local.dao.EmployeeDao
import com.warehouse.inventory.data.local.dao.InventoryTransactionDao
import com.warehouse.inventory.data.local.dao.ProductDao
import com.warehouse.inventory.data.local.dao.ProductStockDao
import com.warehouse.inventory.data.local.dao.SupplierDao
import com.warehouse.inventory.data.local.dao.UserDao
import com.warehouse.inventory.data.local.dao.WarehouseDao
import com.warehouse.inventory.data.local.entity.AuditLogEntity
import com.warehouse.inventory.data.local.entity.CategoryEntity
import com.warehouse.inventory.data.local.entity.EmployeeEntity
import com.warehouse.inventory.data.local.entity.InventoryTransactionEntity
import com.warehouse.inventory.data.local.entity.ProductEntity
import com.warehouse.inventory.data.local.entity.ProductStockEntity
import com.warehouse.inventory.data.local.entity.SupplierEntity
import com.warehouse.inventory.data.local.entity.UserEntity
import com.warehouse.inventory.data.local.entity.WarehouseEntity

@Database(
    entities = [
        UserEntity::class,
        CategoryEntity::class,
        ProductEntity::class,
        SupplierEntity::class,
        EmployeeEntity::class,
        WarehouseEntity::class,
        ProductStockEntity::class,
        InventoryTransactionEntity::class,
        AuditLogEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun categoryDao(): CategoryDao
    abstract fun productDao(): ProductDao
    abstract fun supplierDao(): SupplierDao
    abstract fun employeeDao(): EmployeeDao
    abstract fun warehouseDao(): WarehouseDao
    abstract fun productStockDao(): ProductStockDao
    abstract fun inventoryTransactionDao(): InventoryTransactionDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        const val DB_NAME = "warehouse.db"

        /** Bumped whenever the schema changes; see [ALL_MIGRATIONS]. */
        const val VERSION = 2
    }
}
