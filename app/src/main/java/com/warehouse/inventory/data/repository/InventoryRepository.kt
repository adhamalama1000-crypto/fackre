package com.warehouse.inventory.data.repository

import androidx.room.withTransaction
import com.warehouse.inventory.data.local.AppDatabase
import com.warehouse.inventory.data.local.dao.AuditLogDao
import com.warehouse.inventory.data.local.dao.EmployeeDao
import com.warehouse.inventory.data.local.dao.InventoryTransactionDao
import com.warehouse.inventory.data.local.dao.ProductDao
import com.warehouse.inventory.data.local.dao.ProductStockDao
import com.warehouse.inventory.data.local.dao.SupplierDao
import com.warehouse.inventory.data.local.dao.WarehouseDao
import com.warehouse.inventory.data.local.entity.AdjustmentReason
import com.warehouse.inventory.data.local.entity.AuditAction
import com.warehouse.inventory.data.local.entity.AuditLogEntity
import com.warehouse.inventory.data.local.entity.InventoryTransactionEntity
import com.warehouse.inventory.data.local.entity.ProductEntity
import com.warehouse.inventory.data.local.entity.ProductStockEntity
import com.warehouse.inventory.data.local.entity.TransactionType
import com.warehouse.inventory.data.local.entity.UserEntity
import com.warehouse.inventory.data.local.entity.WarehouseEntity
import com.warehouse.inventory.data.local.entity.WarehouseStock
import com.warehouse.inventory.util.DateUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * The only place in the app that changes stock.
 *
 * Every operation holds to the same four rules:
 *
 * - **Atomic.** The per-warehouse level, the cached product total and the history row are
 *   written in one Room transaction. A partial stock movement cannot be observed or left
 *   behind, and a transfer moves both legs or neither.
 * - **Never negative.** Removals go through guarded SQL UPDATEs
 *   (`WHERE quantity >= :amount`), so the check and the write are one statement and no
 *   concurrent caller can slip between them. 0 rows updated means "not enough" and the
 *   transaction unwinds.
 * - **Never silent.** No path changes a quantity without also inserting an
 *   [InventoryTransactionEntity] carrying the before, after and difference.
 * - **Admin-only.** Enforced by [PermissionChecker] against the database role, above the
 *   ViewModel layer, so a viewer reaching these functions still gets
 *   [StockResult.NotAuthorized].
 */
@Singleton
class InventoryRepository @Inject constructor(
    private val db: AppDatabase,
    private val productDao: ProductDao,
    private val productStockDao: ProductStockDao,
    private val transactionDao: InventoryTransactionDao,
    private val warehouseDao: WarehouseDao,
    private val supplierDao: SupplierDao,
    private val employeeDao: EmployeeDao,
    private val auditLogDao: AuditLogDao,
    private val permissionChecker: PermissionChecker
) {

    // ------------------------------------------------------------------ reads

    fun observeAll(): Flow<List<InventoryTransactionEntity>> = transactionDao.observeAll()

    fun observeRecent(limit: Int = 10): Flow<List<InventoryTransactionEntity>> =
        transactionDao.observeRecent(limit)

    fun observeForProduct(productId: Long): Flow<List<InventoryTransactionEntity>> =
        transactionDao.observeForProduct(productId)

    fun observeFiltered(filter: HistoryFilter): Flow<List<InventoryTransactionEntity>> =
        transactionDao.filter(
            types = filter.typeNames,
            productId = filter.productId,
            employeeId = filter.employeeId,
            warehouseId = filter.warehouseId,
            supplierId = filter.supplierId,
            categoryId = filter.categoryId,
            from = filter.from,
            to = filter.to,
            query = filter.query.trim()
        )

    suspend fun listFiltered(filter: HistoryFilter): List<InventoryTransactionEntity> =
        transactionDao.filterOnce(
            types = filter.typeNames,
            productId = filter.productId,
            employeeId = filter.employeeId,
            warehouseId = filter.warehouseId,
            supplierId = filter.supplierId,
            categoryId = filter.categoryId,
            from = filter.from,
            to = filter.to,
            query = filter.query.trim()
        )

    /** Per-warehouse breakdown of where one product's stock sits. */
    fun observeStockByWarehouse(productId: Long): Flow<List<WarehouseStock>> =
        productStockDao.observeForProduct(productId)

    suspend fun quantityIn(productId: Long, warehouseId: Long): Int =
        productStockDao.getQuantity(productId, warehouseId) ?: 0

    // ---- Dashboard aggregates (today) ----

    fun observeStockInToday(): Flow<Int> {
        val (start, end) = DateUtils.todayRange()
        return transactionDao.observeQuantityInRange(TransactionType.STOCK_IN.name, start, end)
    }

    fun observeStockOutToday(): Flow<Int> {
        val (start, end) = DateUtils.todayRange()
        return transactionDao.observeQuantityInRange(TransactionType.STOCK_OUT.name, start, end)
    }

    fun observeStockOutCountToday(): Flow<Int> {
        val (start, end) = DateUtils.todayRange()
        return transactionDao.observeCountInRange(TransactionType.STOCK_OUT.name, start, end)
    }

    // ------------------------------------------------------------------ writes

    /**
     * Receives stock: `new = current + quantity` in the chosen warehouse.
     *
     * @param date business date of the receipt; must not be in the future.
     * @param invoiceNumber supplier invoice / shipment reference, free-form.
     */
    suspend fun recordStockIn(
        productId: Long,
        warehouseId: Long,
        quantity: Int,
        supplierId: Long?,
        employeeId: Long?,
        date: Long,
        invoiceNumber: String,
        notes: String
    ): StockResult {
        if (quantity <= 0) return StockResult.InvalidQuantity
        if (!DateUtils.isValidBusinessDate(date)) return StockResult.InvalidDate
        val actor = permissionChecker.requireAdmin("stock_in") ?: return StockResult.NotAuthorized

        return runOperation {
            db.withTransaction {
                val product = productDao.getById(productId)
                    ?: return@withTransaction StockResult.ProductNotFound
                val warehouse = warehouseDao.getById(warehouseId)
                    ?: return@withTransaction StockResult.WarehouseNotFound

                val now = System.currentTimeMillis()
                ensureStockRow(productId, warehouseId, now)

                val previous = productStockDao.getQuantity(productId, warehouseId) ?: 0
                if (productStockDao.increase(productId, warehouseId, quantity, now) == 0) {
                    return@withTransaction StockResult.Failed("increase_failed")
                }
                productDao.refreshTotalQuantity(productId, now)

                val supplier = supplierId?.let { supplierDao.getById(it) }
                val employee = employeeId?.let { employeeDao.getById(it) }

                transactionDao.insert(
                    baseTransaction(
                        type = TransactionType.STOCK_IN,
                        product = product,
                        warehouse = warehouse,
                        quantity = quantity,
                        previous = previous,
                        newQuantity = previous + quantity,
                        date = date,
                        notes = notes,
                        actor = actor
                    ).copy(
                        supplierId = supplier?.id,
                        supplierName = supplier?.name.orEmpty(),
                        employeeId = employee?.id,
                        employeeName = employee?.name.orEmpty(),
                        invoiceNumber = invoiceNumber.trim()
                    )
                )

                audit(
                    actor, AuditAction.STOCK_IN, product,
                    "استلام ${quantity} إلى ${warehouse.name} • الرصيد ${previous} ← ${previous + quantity}" +
                        supplier?.let { " • المورّد ${it.name}" }.orEmpty()
                )
                StockResult.Success
            }
        }
    }

    /**
     * Issues stock: `new = current - quantity`, refused if the warehouse holds less than
     * [quantity].
     */
    suspend fun recordStockOut(
        productId: Long,
        warehouseId: Long,
        quantity: Int,
        employeeId: Long?,
        date: Long,
        notes: String
    ): StockResult {
        if (quantity <= 0) return StockResult.InvalidQuantity
        if (!DateUtils.isValidBusinessDate(date)) return StockResult.InvalidDate
        val actor = permissionChecker.requireAdmin("stock_out") ?: return StockResult.NotAuthorized

        return runOperation {
            db.withTransaction {
                val product = productDao.getById(productId)
                    ?: return@withTransaction StockResult.ProductNotFound
                val warehouse = warehouseDao.getById(warehouseId)
                    ?: return@withTransaction StockResult.WarehouseNotFound

                val now = System.currentTimeMillis()
                val previous = productStockDao.getQuantity(productId, warehouseId) ?: 0

                // Guarded UPDATE: 0 rows means the warehouse held less than `quantity`
                // (or holds none at all), so nothing is written and we unwind.
                if (productStockDao.decrease(productId, warehouseId, quantity, now) == 0) {
                    return@withTransaction StockResult.InsufficientStock
                }
                productDao.refreshTotalQuantity(productId, now)

                val employee = employeeId?.let { employeeDao.getById(it) }

                transactionDao.insert(
                    baseTransaction(
                        type = TransactionType.STOCK_OUT,
                        product = product,
                        warehouse = warehouse,
                        quantity = quantity,
                        previous = previous,
                        newQuantity = previous - quantity,
                        date = date,
                        notes = notes,
                        actor = actor
                    ).copy(
                        employeeId = employee?.id,
                        employeeName = employee?.name.orEmpty()
                    )
                )

                audit(
                    actor, AuditAction.STOCK_OUT, product,
                    "صرف ${quantity} من ${warehouse.name} • الرصيد ${previous} ← ${previous - quantity}" +
                        employee?.let { " • الموظف ${it.name}" }.orEmpty()
                )
                StockResult.Success
            }
        }
    }

    /**
     * Corrects the system quantity to a counted physical quantity.
     *
     * [newQuantity] is absolute, not a delta: the caller enters what was actually counted
     * and the signed difference is derived. A reason is mandatory so a correction is never
     * an unexplained change.
     */
    suspend fun recordAdjustment(
        productId: Long,
        warehouseId: Long,
        newQuantity: Int,
        reason: AdjustmentReason,
        employeeId: Long?,
        date: Long,
        notes: String
    ): StockResult {
        if (newQuantity < 0) return StockResult.InvalidQuantity
        if (!DateUtils.isValidBusinessDate(date)) return StockResult.InvalidDate
        val actor = permissionChecker.requireAdmin("stock_adjustment")
            ?: return StockResult.NotAuthorized

        return runOperation {
            db.withTransaction {
                val product = productDao.getById(productId)
                    ?: return@withTransaction StockResult.ProductNotFound
                val warehouse = warehouseDao.getById(warehouseId)
                    ?: return@withTransaction StockResult.WarehouseNotFound

                val now = System.currentTimeMillis()
                ensureStockRow(productId, warehouseId, now)

                val previous = productStockDao.getQuantity(productId, warehouseId) ?: 0
                if (previous == newQuantity) return@withTransaction StockResult.NoChange

                if (productStockDao.setQuantity(productId, warehouseId, newQuantity, now) == 0) {
                    return@withTransaction StockResult.Failed("set_quantity_failed")
                }
                productDao.refreshTotalQuantity(productId, now)

                val employee = employeeId?.let { employeeDao.getById(it) }
                val difference = newQuantity - previous

                transactionDao.insert(
                    baseTransaction(
                        type = TransactionType.ADJUSTMENT,
                        product = product,
                        warehouse = warehouse,
                        // Magnitude of the correction; the sign lives in `difference`.
                        quantity = abs(difference),
                        previous = previous,
                        newQuantity = newQuantity,
                        date = date,
                        notes = notes,
                        actor = actor
                    ).copy(
                        reason = reason,
                        employeeId = employee?.id,
                        employeeName = employee?.name.orEmpty()
                    )
                )

                audit(
                    actor, AuditAction.STOCK_ADJUSTMENT, product,
                    "تسوية في ${warehouse.name} • ${previous} ← ${newQuantity} " +
                        "(${if (difference > 0) "+" else ""}${difference})"
                )
                StockResult.Success
            }
        }
    }

    /**
     * Moves stock between warehouses. Writes two history legs (TRANSFER_OUT then
     * TRANSFER_IN) sharing one `transferGroupId`; the total across warehouses is unchanged.
     *
     * Both legs live in one transaction, so stock is never in flight: either the source is
     * debited and the destination credited, or neither happened.
     */
    suspend fun recordTransfer(
        productId: Long,
        fromWarehouseId: Long,
        toWarehouseId: Long,
        quantity: Int,
        employeeId: Long?,
        date: Long,
        notes: String
    ): StockResult {
        if (quantity <= 0) return StockResult.InvalidQuantity
        if (fromWarehouseId == toWarehouseId) return StockResult.SameWarehouse
        if (!DateUtils.isValidBusinessDate(date)) return StockResult.InvalidDate
        val actor = permissionChecker.requireAdmin("stock_transfer")
            ?: return StockResult.NotAuthorized

        return runOperation {
            db.withTransaction {
                val product = productDao.getById(productId)
                    ?: return@withTransaction StockResult.ProductNotFound
                val source = warehouseDao.getById(fromWarehouseId)
                    ?: return@withTransaction StockResult.WarehouseNotFound
                val destination = warehouseDao.getById(toWarehouseId)
                    ?: return@withTransaction StockResult.WarehouseNotFound

                val now = System.currentTimeMillis()
                val sourcePrevious = productStockDao.getQuantity(productId, fromWarehouseId) ?: 0

                if (productStockDao.decrease(productId, fromWarehouseId, quantity, now) == 0) {
                    return@withTransaction StockResult.InsufficientStock
                }

                ensureStockRow(productId, toWarehouseId, now)
                // Read after ensuring the row exists but before crediting it, so the
                // recorded "previous" is the destination's true level beforehand.
                val destinationPrevious = productStockDao.getQuantity(productId, toWarehouseId) ?: 0
                if (productStockDao.increase(productId, toWarehouseId, quantity, now) == 0) {
                    return@withTransaction StockResult.Failed("increase_failed")
                }

                // Total is unchanged, but this refreshes updatedAt/synced for the sync layer.
                productDao.refreshTotalQuantity(productId, now)

                val employee = employeeId?.let { employeeDao.getById(it) }
                val groupId = UUID.randomUUID().toString()

                transactionDao.insert(
                    baseTransaction(
                        type = TransactionType.TRANSFER_OUT,
                        product = product,
                        warehouse = source,
                        quantity = quantity,
                        previous = sourcePrevious,
                        newQuantity = sourcePrevious - quantity,
                        date = date,
                        notes = notes,
                        actor = actor
                    ).copy(
                        counterpartWarehouseId = destination.id,
                        counterpartWarehouseName = destination.name,
                        transferGroupId = groupId,
                        employeeId = employee?.id,
                        employeeName = employee?.name.orEmpty()
                    )
                )
                transactionDao.insert(
                    baseTransaction(
                        type = TransactionType.TRANSFER_IN,
                        product = product,
                        warehouse = destination,
                        quantity = quantity,
                        previous = destinationPrevious,
                        newQuantity = destinationPrevious + quantity,
                        date = date,
                        notes = notes,
                        actor = actor
                    ).copy(
                        counterpartWarehouseId = source.id,
                        counterpartWarehouseName = source.name,
                        transferGroupId = groupId,
                        employeeId = employee?.id,
                        employeeName = employee?.name.orEmpty()
                    )
                )

                audit(
                    actor, AuditAction.STOCK_TRANSFER, product,
                    "تحويل ${quantity} من ${source.name} إلى ${destination.name}"
                )
                StockResult.Success
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Creates the (product, warehouse) stock row at zero if it does not exist yet, so the
     * guarded UPDATEs below have something to act on. Relies on
     * `OnConflictStrategy.IGNORE` against the unique index, which makes this safe to call
     * even if another transaction created the row first.
     */
    private suspend fun ensureStockRow(productId: Long, warehouseId: Long, now: Long) {
        productStockDao.insertIgnore(
            ProductStockEntity(
                productId = productId,
                warehouseId = warehouseId,
                quantity = 0,
                updatedAt = now
            )
        )
    }

    /** Shared skeleton for a history row; callers `copy()` in the fields specific to the type. */
    private fun baseTransaction(
        type: TransactionType,
        product: ProductEntity,
        warehouse: WarehouseEntity,
        quantity: Int,
        previous: Int,
        newQuantity: Int,
        date: Long,
        notes: String,
        actor: UserEntity
    ) = InventoryTransactionEntity(
        type = type,
        productId = product.id,
        productName = product.name,
        productSku = product.sku,
        unit = product.unit,
        quantity = quantity,
        previousQuantity = previous,
        newQuantity = newQuantity,
        difference = newQuantity - previous,
        warehouseId = warehouse.id,
        warehouseName = warehouse.name,
        notes = notes.trim(),
        date = date,
        userId = actor.id,
        userName = actor.name
    )

    private suspend fun audit(
        actor: UserEntity,
        action: AuditAction,
        product: ProductEntity,
        details: String
    ) {
        auditLogDao.insert(
            AuditLogEntity(
                userId = actor.id,
                userName = actor.name,
                userEmail = actor.email,
                action = action,
                targetType = "product",
                targetId = product.id,
                targetName = product.name,
                details = details
            )
        )
    }

    /**
     * Turns an unexpected exception into [StockResult.Failed] instead of letting it escape
     * into a ViewModel coroutine. A thrown exception has already rolled the transaction
     * back, so stock is consistent either way; this only decides what the UI shows.
     *
     * [CancellationException] is rethrown rather than reported: it means the caller's scope
     * went away, not that the operation failed, and swallowing it would break structured
     * concurrency.
     */
    private inline fun runOperation(block: () -> StockResult): StockResult =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StockResult.Failed(e.message)
        }
}
