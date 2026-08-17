package com.warehouse.inventory.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.warehouse.inventory.data.local.ALL_MIGRATIONS
import com.warehouse.inventory.data.local.AppDatabase
import com.warehouse.inventory.data.local.dao.AuditLogDao
import com.warehouse.inventory.data.local.dao.CategoryDao
import com.warehouse.inventory.data.local.dao.EmployeeDao
import com.warehouse.inventory.data.local.dao.InventoryTransactionDao
import com.warehouse.inventory.data.local.dao.ProductDao
import com.warehouse.inventory.data.local.dao.ProductStockDao
import com.warehouse.inventory.data.local.dao.SupplierDao
import com.warehouse.inventory.data.local.dao.UserDao
import com.warehouse.inventory.data.local.dao.WarehouseDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            // Real migrations only. fallbackToDestructiveMigration() used to be set here,
            // which would erase a warehouse's entire stock history on a schema change.
            .addMigrations(*ALL_MIGRATIONS)
            // Pinned rather than left to Room's per-device default so backup always has a
            // WAL to checkpoint; DatabaseBackupManager relies on that being true.
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()

    @Provides
    fun provideUserDao(db: AppDatabase): UserDao = db.userDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideProductDao(db: AppDatabase): ProductDao = db.productDao()

    @Provides
    fun provideSupplierDao(db: AppDatabase): SupplierDao = db.supplierDao()

    @Provides
    fun provideEmployeeDao(db: AppDatabase): EmployeeDao = db.employeeDao()

    @Provides
    fun provideWarehouseDao(db: AppDatabase): WarehouseDao = db.warehouseDao()

    @Provides
    fun provideProductStockDao(db: AppDatabase): ProductStockDao = db.productStockDao()

    @Provides
    fun provideInventoryTransactionDao(db: AppDatabase): InventoryTransactionDao =
        db.inventoryTransactionDao()

    @Provides
    fun provideAuditLogDao(db: AppDatabase): AuditLogDao = db.auditLogDao()
}
