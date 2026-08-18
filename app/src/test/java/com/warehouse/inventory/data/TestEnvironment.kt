package com.warehouse.inventory.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.warehouse.inventory.data.local.AppDatabase
import com.warehouse.inventory.data.local.entity.AuditLogEntity
import com.warehouse.inventory.data.local.entity.InventoryTransactionEntity
import com.warehouse.inventory.data.local.entity.ProductEntity
import com.warehouse.inventory.data.local.entity.ProductStockEntity
import com.warehouse.inventory.data.local.entity.TransactionType
import com.warehouse.inventory.data.local.entity.UserEntity
import com.warehouse.inventory.data.local.entity.UserRole
import com.warehouse.inventory.data.local.entity.WarehouseEntity
import com.warehouse.inventory.data.repository.AuditLogRepository
import com.warehouse.inventory.data.repository.InventoryRepository
import com.warehouse.inventory.data.repository.PermissionChecker
import com.warehouse.inventory.data.repository.ProductRepository
import com.warehouse.inventory.data.repository.UserRepository
import com.warehouse.inventory.data.repository.WarehouseRepository
import com.warehouse.inventory.data.session.SessionManager
import com.warehouse.inventory.data.session.SessionUser
import com.warehouse.inventory.util.PasswordHasher
import kotlinx.coroutines.flow.first

/**
 * A whole data layer wired against an in-memory Room database.
 *
 * The repositories are constructed by hand rather than through Hilt: these tests are about
 * the SQL and the transaction boundaries, and a real database with hand-wired dependencies
 * exercises both without the cost of a Hilt test graph. Room runs on the JVM here via
 * Robolectric, so the guarded UPDATEs that prevent negative stock are executed by actual
 * SQLite — mocked DAOs would prove nothing about them.
 *
 * [signInAs] swaps the acting user, which is how the permission tests flip between an admin
 * and a viewer.
 */
class TestEnvironment private constructor(
    val db: AppDatabase,
    private val session: SessionManager
) {
    val userDao = db.userDao()
    val productDao = db.productDao()
    val productStockDao = db.productStockDao()
    val warehouseDao = db.warehouseDao()
    val transactionDao = db.inventoryTransactionDao()
    val auditLogDao = db.auditLogDao()
    val categoryDao = db.categoryDao()

    val permissionChecker = PermissionChecker(session, userDao, auditLogDao)
    val auditLogRepository = AuditLogRepository(auditLogDao, permissionChecker)

    val inventoryRepository = InventoryRepository(
        db = db,
        productDao = productDao,
        productStockDao = productStockDao,
        transactionDao = transactionDao,
        warehouseDao = warehouseDao,
        supplierDao = db.supplierDao(),
        employeeDao = db.employeeDao(),
        auditLogDao = auditLogDao,
        permissionChecker = permissionChecker
    )

    val productRepository = ProductRepository(
        db = db,
        productDao = productDao,
        productStockDao = productStockDao,
        transactionDao = transactionDao,
        warehouseDao = warehouseDao,
        permissionChecker = permissionChecker,
        auditLogRepository = auditLogRepository
    )

    val warehouseRepository = WarehouseRepository(
        db = db,
        warehouseDao = warehouseDao,
        productStockDao = productStockDao,
        permissionChecker = permissionChecker,
        auditLogRepository = auditLogRepository
    )

    val userRepository = UserRepository(
        userDao = userDao,
        categoryDao = categoryDao,
        warehouseRepository = warehouseRepository,
        permissionChecker = permissionChecker,
        auditLogRepository = auditLogRepository
    )

    // ------------------------------------------------------------- fixtures

    /** Inserts a user and returns its row id. */
    suspend fun createUser(
        name: String,
        email: String,
        role: UserRole,
        password: String = "secret123"
    ): Long {
        val salt = PasswordHasher.generateSalt()
        return userDao.insert(
            UserEntity(
                name = name,
                email = email,
                passwordHash = PasswordHasher.hash(password, salt),
                salt = salt,
                role = role
            )
        )
    }

    /** Makes [userId] the acting user for subsequent permission checks. */
    suspend fun signInAs(userId: Long) {
        val user = userDao.getById(userId) ?: error("no user $userId")
        session.signIn(
            SessionUser(id = user.id, name = user.name, email = user.email, role = user.role)
        )
    }

    suspend fun signOut() = session.signOut()

    suspend fun createWarehouse(
        name: String,
        code: String,
        isDefault: Boolean = false
    ): Long = warehouseDao.insert(
        WarehouseEntity(name = name, code = code, isDefault = isDefault)
    )

    /**
     * Inserts a product with an opening balance written straight into `product_stock`,
     * bypassing the repository so a test can set up state without depending on the
     * operation it is about to exercise.
     */
    suspend fun createProduct(
        name: String,
        warehouseId: Long,
        quantity: Int = 0,
        lowStockThreshold: Int = 5,
        sku: String? = null,
        barcode: String? = null
    ): Long {
        val id = productDao.insert(
            ProductEntity(
                name = name,
                categoryId = null,
                quantity = quantity,
                lowStockThreshold = lowStockThreshold,
                sku = sku,
                barcode = barcode
            )
        )
        productStockDao.insertIgnore(
            ProductStockEntity(
                productId = id,
                warehouseId = warehouseId,
                quantity = quantity,
                updatedAt = System.currentTimeMillis()
            )
        )
        return id
    }

    /**
     * Every history row, newest first, optionally narrowed to one product.
     *
     * Wraps the DAO's nine-argument filter with "no filter" values so a test asserting on
     * history does not have to restate them: all types, no date bounds, no search term.
     */
    suspend fun history(productId: Long? = null): List<InventoryTransactionEntity> =
        transactionDao.filterOnce(
            types = TransactionType.entries.map { it.name },
            productId = productId,
            employeeId = null,
            warehouseId = null,
            supplierId = null,
            categoryId = null,
            from = 0L,
            to = Long.MAX_VALUE,
            query = ""
        )

    /** Audit rows, newest first. The DAO only exposes a Flow, so this snapshots it. */
    suspend fun auditLog(limit: Int = 200): List<AuditLogEntity> =
        auditLogDao.observeRecent(limit).first()

    suspend fun productCount(): Int = productDao.listOnce(null, null, false).size

    suspend fun quantityIn(productId: Long, warehouseId: Long): Int =
        productStockDao.getQuantity(productId, warehouseId) ?: 0

    /** The cached total on the product row, which must stay in step with the stock rows. */
    suspend fun productTotal(productId: Long): Int =
        productDao.getById(productId)?.quantity ?: error("no product $productId")

    fun close() = db.close()

    companion object {
        fun create(): TestEnvironment {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                // TRUNCATE rather than the production WAL setting: an in-memory database
                // has no sidecar files to checkpoint, and WAL adds nothing here.
                .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
                .allowMainThreadQueries()
                .build()
            // The real SessionManager: its DataStore file lives under Robolectric's
            // per-test temp directory, so no fake is needed and the production session code
            // is exercised as-is.
            return TestEnvironment(db, SessionManager(context))
        }
    }
}
