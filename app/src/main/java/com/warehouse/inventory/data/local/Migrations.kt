package com.warehouse.inventory.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema history.
 *
 * The app previously used `fallbackToDestructiveMigration()`, which silently wipes a
 * warehouse's entire stock record on upgrade. That is unacceptable for real data, so it
 * has been removed and every schema change now ships a real migration.
 *
 * The DDL below is written to match Room's generated schema exactly (column order and
 * types, `IF NOT EXISTS`, Room's `index_<table>_<columns>` naming, and
 * `ON UPDATE NO ACTION` on every foreign key). Room re-reads the schema with PRAGMA after
 * migrating and throws if anything differs, so these statements and the entity
 * declarations have to agree column for column.
 */

/**
 * Generates an RFC-4122 version-4 UUID in SQLite, matching the format
 * `UUID.randomUUID().toString()` produces for rows inserted by Kotlin. Used so rows
 * created by the migration carry a `syncId` in the same shape as every other row and the
 * future Firebase sync layer needs no special case for them.
 */
private const val SQL_UUID =
    "lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || " +
        "substr(lower(hex(randomblob(2))), 2) || '-' || " +
        "substr('89ab', abs(random()) % 4 + 1, 1) || " +
        "substr(lower(hex(randomblob(2))), 2) || '-' || lower(hex(randomblob(6)))"

/**
 * v1 -> v2: suppliers, employees, warehouses, per-warehouse stock, the unified
 * inventory-transaction history and the audit log; product identification (SKU, barcode,
 * unit) and costing.
 *
 * Existing data is preserved: a default warehouse is created, every product's current
 * quantity is moved into it as a `product_stock` row, and every `stock_out` row is copied
 * into `inventory_transactions` as a STOCK_OUT before the old table is dropped.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {

        // ---------- New reference tables ----------

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `suppliers` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `phone` TEXT NOT NULL,
                `email` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `syncId` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `synced` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_suppliers_name` ON `suppliers` (`name`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `employees` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `code` TEXT,
                `phone` TEXT NOT NULL,
                `department` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `active` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `syncId` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `synced` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_employees_code` ON `employees` (`code`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_employees_name` ON `employees` (`name`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `warehouses` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `code` TEXT NOT NULL,
                `location` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `isDefault` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `syncId` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `synced` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_warehouses_code` ON `warehouses` (`code`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_warehouses_name` ON `warehouses` (`name`)")

        // ---------- Product identification, units and costing ----------
        // `unit` is NOT NULL, so it needs a DEFAULT to be addable to existing rows. The
        // entity declares the same default via @ColumnInfo(defaultValue = "PIECE") so
        // Room's post-migration schema check sees matching defaults on both sides.
        db.execSQL("ALTER TABLE `products` ADD COLUMN `sku` TEXT")
        db.execSQL("ALTER TABLE `products` ADD COLUMN `barcode` TEXT")
        db.execSQL("ALTER TABLE `products` ADD COLUMN `unit` TEXT NOT NULL DEFAULT 'PIECE'")
        db.execSQL("ALTER TABLE `products` ADD COLUMN `purchaseCost` REAL")
        db.execSQL("ALTER TABLE `products` ADD COLUMN `sellingPrice` REAL")
        // Unique where present: SQLite treats NULLs as distinct, so products without a
        // code are unaffected while a given code can only belong to one product.
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_products_sku` ON `products` (`sku`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_products_barcode` ON `products` (`barcode`)")

        // ---------- Per-warehouse stock ----------

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `product_stock` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `productId` INTEGER NOT NULL,
                `warehouseId` INTEGER NOT NULL,
                `quantity` INTEGER NOT NULL,
                `syncId` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `synced` INTEGER NOT NULL,
                FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`warehouseId`) REFERENCES `warehouses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_product_stock_productId_warehouseId` " +
                "ON `product_stock` (`productId`, `warehouseId`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_stock_warehouseId` ON `product_stock` (`warehouseId`)")

        // ---------- Unified inventory history ----------

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `inventory_transactions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `type` TEXT NOT NULL,
                `productId` INTEGER,
                `productName` TEXT NOT NULL,
                `productSku` TEXT,
                `unit` TEXT NOT NULL,
                `quantity` INTEGER NOT NULL,
                `previousQuantity` INTEGER,
                `newQuantity` INTEGER,
                `difference` INTEGER,
                `warehouseId` INTEGER,
                `warehouseName` TEXT NOT NULL,
                `counterpartWarehouseId` INTEGER,
                `counterpartWarehouseName` TEXT NOT NULL,
                `transferGroupId` TEXT,
                `supplierId` INTEGER,
                `supplierName` TEXT NOT NULL,
                `employeeId` INTEGER,
                `employeeName` TEXT NOT NULL,
                `reason` TEXT,
                `invoiceNumber` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `date` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `userId` INTEGER,
                `userName` TEXT NOT NULL,
                `syncId` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `synced` INTEGER NOT NULL,
                FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(`warehouseId`) REFERENCES `warehouses`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(`supplierId`) REFERENCES `suppliers`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(`employeeId`) REFERENCES `employees`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        listOf(
            "productId", "warehouseId", "supplierId", "employeeId", "date", "type", "transferGroupId"
        ).forEach { column ->
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_inventory_transactions_$column` " +
                    "ON `inventory_transactions` (`$column`)"
            )
        }

        // ---------- Audit log ----------

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `audit_logs` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `userId` INTEGER,
                `userName` TEXT NOT NULL,
                `userEmail` TEXT NOT NULL,
                `action` TEXT NOT NULL,
                `targetType` TEXT NOT NULL,
                `targetId` INTEGER,
                `targetName` TEXT NOT NULL,
                `details` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `syncId` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `synced` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_timestamp` ON `audit_logs` (`timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_action` ON `audit_logs` (`action`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_userId` ON `audit_logs` (`userId`)")

        // ---------- Carry existing data forward ----------

        val now = System.currentTimeMillis()

        // One default warehouse to own all stock that existed before warehouses did.
        db.execSQL(
            """
            INSERT INTO `warehouses`
                (`name`, `code`, `location`, `notes`, `isDefault`, `createdAt`, `syncId`, `updatedAt`, `synced`)
            VALUES ('المخزن الرئيسي', 'MAIN', '', '', 1, $now, $SQL_UUID, $now, 0)
            """.trimIndent()
        )

        // Move every product's existing total into that warehouse. products.quantity stays
        // as-is and now reads as the cached sum of these rows, so it needs no rewrite.
        db.execSQL(
            """
            INSERT INTO `product_stock` (`productId`, `warehouseId`, `quantity`, `syncId`, `updatedAt`, `synced`)
            SELECT p.`id`,
                   (SELECT `id` FROM `warehouses` WHERE `isDefault` = 1 LIMIT 1),
                   p.`quantity`,
                   $SQL_UUID,
                   $now,
                   0
            FROM `products` p
            """.trimIndent()
        )

        // Fold the stock-out-only log into the unified history.
        //
        // previousQuantity / newQuantity are left NULL because schema v1 never recorded
        // them; the UI shows "—" for these rows rather than inventing numbers. productId
        // is nulled when the product it referenced has since been deleted (v1 had no
        // foreign key there), which keeps the new foreign key satisfied while the
        // denormalized productName preserves what moved.
        db.execSQL(
            """
            INSERT INTO `inventory_transactions` (
                `type`, `productId`, `productName`, `productSku`, `unit`, `quantity`,
                `previousQuantity`, `newQuantity`, `difference`,
                `warehouseId`, `warehouseName`, `counterpartWarehouseName`,
                `supplierName`, `employeeName`, `invoiceNumber`, `notes`,
                `date`, `createdAt`, `userName`, `syncId`, `updatedAt`, `synced`
            )
            SELECT 'STOCK_OUT',
                   CASE WHEN p.`id` IS NULL THEN NULL ELSE s.`productId` END,
                   s.`productName`,
                   p.`sku`,
                   COALESCE(p.`unit`, 'PIECE'),
                   s.`quantity`,
                   NULL, NULL, -s.`quantity`,
                   (SELECT `id` FROM `warehouses` WHERE `isDefault` = 1 LIMIT 1),
                   'المخزن الرئيسي', '',
                   '', s.`employeeName`, '', s.`notes`,
                   s.`date`, s.`createdAt`, '', s.`syncId`, s.`updatedAt`, s.`synced`
            FROM `stock_out` s
            LEFT JOIN `products` p ON p.`id` = s.`productId`
            """.trimIndent()
        )

        db.execSQL("DROP TABLE `stock_out`")
    }
}

/** Every migration the database ships, in order. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
