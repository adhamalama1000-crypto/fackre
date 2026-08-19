package com.warehouse.inventory.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.warehouse.inventory.data.local.ALL_MIGRATIONS
import com.warehouse.inventory.data.local.AppDatabase
import com.warehouse.inventory.data.local.entity.ProductUnit
import com.warehouse.inventory.data.local.entity.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The v1 -> v2 migration, on a database that actually contains v1 data.
 *
 * A v1 file is built here with raw DDL rather than through Room's `MigrationTestHelper`,
 * because that helper loads a historical schema from `schemas/1.json` and no v1 schema was
 * ever exported — v1 shipped with `fallbackToDestructiveMigration()`. Hand-writing the old
 * DDL is what makes the upgrade path testable at all.
 *
 * The strongest assertion here is simply that opening the migrated file through Room
 * succeeds: Room re-reads the schema with PRAGMA afterwards and throws
 * `IllegalStateException` if a single column, type, default or index differs from the
 * entities. Everything after that checks the data survived.
 *
 * That strictness cuts both ways: the v1 DDL below must match the v1 entities exactly, down
 * to columns the migration never touches. An earlier version of this file gave `categories` a
 * `createdAt` column that `CategoryEntity` does not declare, and Room rejected the whole
 * schema — every test in the class failed on a table the migration does not even alter.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MigrationV1ToV2Test {

    private lateinit var context: Context
    private lateinit var dbFile: File

    private val stockOutDate = 1_700_000_000_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = context.getDatabasePath(MIGRATION_DB)
        dbFile.parentFile?.mkdirs()
        dbFile.delete()
        createV1Database()
    }

    @After
    fun tearDown() {
        dbFile.delete()
    }

    /** Builds a schema-v1 database holding one category, two products and one stock-out. */
    private fun createV1Database() {
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        db.execSQL(
            """
            CREATE TABLE `users` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL, `email` TEXT NOT NULL,
                `passwordHash` TEXT NOT NULL, `salt` TEXT NOT NULL, `role` TEXT NOT NULL,
                `syncId` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `synced` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX `index_users_email` ON `users` (`email`)")

        db.execSQL(
            """
            CREATE TABLE `categories` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `syncId` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `synced` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX `index_categories_name` ON `categories` (`name`)")

        // v1 products: no sku, barcode, unit, purchaseCost or sellingPrice.
        db.execSQL(
            """
            CREATE TABLE `products` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL, `categoryId` INTEGER,
                `quantity` INTEGER NOT NULL, `location` TEXT NOT NULL, `notes` TEXT NOT NULL,
                `imagePath` TEXT, `lowStockThreshold` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `syncId` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `synced` INTEGER NOT NULL,
                FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`)
                    ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX `index_products_categoryId` ON `products` (`categoryId`)")
        db.execSQL("CREATE INDEX `index_products_name` ON `products` (`name`)")

        db.execSQL(
            """
            CREATE TABLE `stock_out` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `productId` INTEGER NOT NULL, `productName` TEXT NOT NULL,
                `quantity` INTEGER NOT NULL, `employeeName` TEXT NOT NULL,
                `notes` TEXT NOT NULL, `date` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL,
                `syncId` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `synced` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX `index_stock_out_productId` ON `stock_out` (`productId`)")

        val now = 1_699_000_000_000L
        db.execSQL(
            "INSERT INTO categories (name, syncId, updatedAt, synced) " +
                "VALUES ('مواد خام', 'cat-1', $now, 0)"
        )
        db.execSQL(
            """
            INSERT INTO products
                (name, categoryId, quantity, location, notes, imagePath,
                 lowStockThreshold, createdAt, syncId, updatedAt, synced)
            VALUES ('مسمار', 1, 70, 'رف 1', '', NULL, 5, $now, 'prod-1', $now, 0),
                   ('صاج', 1, 12, 'رف 2', '', NULL, 20, $now, 'prod-2', $now, 0)
            """.trimIndent()
        )
        // Two stock-outs: one for a product that still exists, one for a product id that
        // does not — v1 had no foreign key there, so orphans are realistic.
        db.execSQL(
            """
            INSERT INTO stock_out
                (productId, productName, quantity, employeeName, notes, date, createdAt,
                 syncId, updatedAt, synced)
            VALUES (1, 'مسمار', 30, 'أحمد', 'صرف للورشة', $stockOutDate, $stockOutDate, 'so-1', $stockOutDate, 0),
                   (999, 'منتج محذوف', 4, 'سالم', '', $stockOutDate, $stockOutDate, 'so-2', $stockOutDate, 0)
            """.trimIndent()
        )

        db.version = 1
        db.close()
    }

    /** Opens the file through Room, which runs the migration and validates the result. */
    private fun openMigrated(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, MIGRATION_DB)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

    @Test
    fun `migration runs and the resulting schema satisfies Room`() = runTest {
        val db = openMigrated()
        try {
            // Any query forces the open + migrate + PRAGMA validation to happen. If the
            // migrated DDL disagreed with the entities by even one column, this throws.
            assertEquals(2, db.productDao().listOnce(null, null, false).size)
        } finally {
            db.close()
        }
    }

    @Test
    fun `products keep their quantities and gain v2 columns with defaults`() = runTest {
        val db = openMigrated()
        try {
            val nail = db.productDao().getById(1)!!
            assertEquals("مسمار", nail.name)
            assertEquals(70, nail.quantity)
            // New nullable columns start empty rather than "".
            assertNull(nail.sku)
            assertNull(nail.barcode)
            assertNull(nail.purchaseCost)
            // NOT NULL column added with a SQL default.
            assertEquals(ProductUnit.PIECE, nail.unit)
        } finally {
            db.close()
        }
    }

    @Test
    fun `a default warehouse is created and owns all pre-existing stock`() = runTest {
        val db = openMigrated()
        try {
            val warehouses = db.warehouseDao().getAll()
            assertEquals(1, warehouses.size)
            val main = warehouses.single()
            assertTrue(main.isDefault)
            assertEquals("MAIN", main.code)

            // Every product's v1 total moved into it, so the cached product total and the
            // per-warehouse row agree from the first launch after upgrade.
            assertEquals(70, db.productStockDao().getQuantity(1, main.id))
            assertEquals(12, db.productStockDao().getQuantity(2, main.id))
            assertEquals(70, db.productStockDao().totalForProduct(1))
        } finally {
            db.close()
        }
    }

    @Test
    fun `stock out history is folded into inventory transactions`() = runTest {
        val db = openMigrated()
        try {
            val rows = db.inventoryTransactionDao().observeAll().first()
            assertEquals(2, rows.size)
            assertTrue(rows.all { it.type == TransactionType.STOCK_OUT })

            val migrated = rows.single { it.productName == "مسمار" }
            assertEquals(30, migrated.quantity)
            assertEquals(-30, migrated.difference)
            assertEquals("أحمد", migrated.employeeName)
            assertEquals("صرف للورشة", migrated.notes)
            assertEquals(stockOutDate, migrated.date)
            assertEquals(1L, migrated.productId)
            // v1 never recorded the before/after levels; the UI shows "—" rather than
            // inventing them, so they must stay null.
            assertNull(migrated.previousQuantity)
            assertNull(migrated.newQuantity)
            // syncId is carried over so a future sync layer sees the same row identity.
            assertEquals("so-1", migrated.syncId)
        } finally {
            db.close()
        }
    }

    @Test
    fun `a stock out for a deleted product survives with its productId nulled`() = runTest {
        val db = openMigrated()
        try {
            val orphan = db.inventoryTransactionDao().observeAll().first()
                .single { it.productName == "منتج محذوف" }

            // The new foreign key could not accept id 999, but the movement itself is still
            // part of the warehouse's record, so the name is what preserves it.
            assertNull(orphan.productId)
            assertEquals(4, orphan.quantity)
            assertNotNull(orphan.productName)
        } finally {
            db.close()
        }
    }

    @Test
    fun `the old stock_out table is gone`() = runTest {
        val db = openMigrated()
        try {
            val cursor = db.openHelper.readableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'stock_out'"
            )
            cursor.use { assertEquals(0, it.count) }
        } finally {
            db.close()
        }
    }

    @Test
    fun `migrated rows carry a well formed syncId where one was generated`() = runTest {
        val db = openMigrated()
        try {
            val warehouse = db.warehouseDao().getAll().single()
            // The migration generates UUIDs in SQL; they must match the shape Kotlin's
            // UUID.randomUUID().toString() produces so the sync layer needs no special case.
            assertTrue(
                "unexpected syncId: ${warehouse.syncId}",
                UUID_REGEX.matches(warehouse.syncId)
            )
        } finally {
            db.close()
        }
    }

    private companion object {
        const val MIGRATION_DB = "migration-test.db"
        val UUID_REGEX =
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    }
}
