package com.warehouse.inventory.data

import com.warehouse.inventory.data.local.entity.AdjustmentReason
import com.warehouse.inventory.data.local.entity.TransactionType
import com.warehouse.inventory.data.local.entity.UserRole
import com.warehouse.inventory.data.repository.StockResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The four stock operations, against a real SQLite database.
 *
 * These assert the three invariants the app is built on: a movement changes the per-warehouse
 * level and the cached product total together, it always leaves a history row carrying
 * before/after/difference, and stock can never go negative.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StockOperationsTest {

    private lateinit var env: TestEnvironment
    private var adminId = 0L
    private var warehouseA = 0L
    private var warehouseB = 0L

    private val today = System.currentTimeMillis()

    @Before
    fun setUp() = runTest {
        env = TestEnvironment.create()
        adminId = env.createUser("مدير", "admin@test.com", UserRole.ADMIN)
        env.signInAs(adminId)
        warehouseA = env.createWarehouse("المخزن الرئيسي", "A", isDefault = true)
        warehouseB = env.createWarehouse("المخزن الفرعي", "B")
    }

    @After
    fun tearDown() {
        env.close()
    }

    // ------------------------------------------------------------- stock in

    @Test
    fun `stock in adds to the existing balance`() = runTest {
        val product = env.createProduct("مسمار", warehouseA, quantity = 50)

        val result = env.inventoryRepository.recordStockIn(
            productId = product,
            warehouseId = warehouseA,
            quantity = 20,
            supplierId = null,
            employeeId = null,
            date = today,
            invoiceNumber = "INV-1",
            notes = ""
        )

        assertEquals(StockResult.Success, result)
        // 50 + 20 = 70, in the warehouse row and in the cached product total alike.
        assertEquals(70, env.quantityIn(product, warehouseA))
        assertEquals(70, env.productTotal(product))
    }

    @Test
    fun `stock in records a history row with before and after`() = runTest {
        val product = env.createProduct("مسمار", warehouseA, quantity = 50)

        env.inventoryRepository.recordStockIn(
            product, warehouseA, 20, null, null, today, "INV-7", "ملاحظة"
        )

        val rows = env.history(product)
        assertEquals(1, rows.size)
        val row = rows.first()
        assertEquals(TransactionType.STOCK_IN, row.type)
        assertEquals(20, row.quantity)
        assertEquals(50, row.previousQuantity)
        assertEquals(70, row.newQuantity)
        assertEquals(20, row.difference)
        assertEquals("INV-7", row.invoiceNumber)
        // The acting user is recorded so the movement is attributable.
        assertEquals(adminId, row.userId)
    }

    @Test
    fun `stock in rejects a non-positive quantity`() = runTest {
        val product = env.createProduct("مسمار", warehouseA, quantity = 50)

        assertEquals(
            StockResult.InvalidQuantity,
            env.inventoryRepository.recordStockIn(
                product, warehouseA, 0, null, null, today, "", ""
            )
        )
        assertEquals(
            StockResult.InvalidQuantity,
            env.inventoryRepository.recordStockIn(
                product, warehouseA, -5, null, null, today, "", ""
            )
        )
        assertEquals(50, env.quantityIn(product, warehouseA))
    }

    @Test
    fun `stock in rejects a future date`() = runTest {
        val product = env.createProduct("مسمار", warehouseA, quantity = 50)
        val tomorrow = today + 2 * 24 * 60 * 60 * 1000L

        val result = env.inventoryRepository.recordStockIn(
            product, warehouseA, 10, null, null, tomorrow, "", ""
        )

        assertEquals(StockResult.InvalidDate, result)
        assertEquals(50, env.quantityIn(product, warehouseA))
    }

    // ------------------------------------------------------------ stock out

    @Test
    fun `stock out subtracts from the balance`() = runTest {
        val product = env.createProduct("مسمار", warehouseA, quantity = 50)

        val result = env.inventoryRepository.recordStockOut(
            productId = product,
            warehouseId = warehouseA,
            quantity = 20,
            employeeId = null,
            date = today,
            notes = ""
        )

        assertEquals(StockResult.Success, result)
        assertEquals(30, env.quantityIn(product, warehouseA))
        assertEquals(30, env.productTotal(product))
    }

    @Test
    fun `stock out refuses to exceed available stock and changes nothing`() = runTest {
        val product = env.createProduct("مسمار", warehouseA, quantity = 10)

        val result = env.inventoryRepository.recordStockOut(
            product, warehouseA, 11, null, today, ""
        )

        assertEquals(StockResult.InsufficientStock, result)
        // Nothing moved and nothing was logged: the whole operation unwound.
        assertEquals(10, env.quantityIn(product, warehouseA))
        assertEquals(10, env.productTotal(product))
        assertEquals(0, env.history(product).size)
    }

    @Test
    fun `stock out down to exactly zero is allowed`() = runTest {
        val product = env.createProduct("مسمار", warehouseA, quantity = 10)

        val result = env.inventoryRepository.recordStockOut(
            product, warehouseA, 10, null, today, ""
        )

        assertEquals(StockResult.Success, result)
        assertEquals(0, env.quantityIn(product, warehouseA))
    }

    @Test
    fun `stock out from a warehouse holding none is refused`() = runTest {
        // Stock exists, but in warehouse A — B has nothing.
        val product = env.createProduct("مسمار", warehouseA, quantity = 10)

        val result = env.inventoryRepository.recordStockOut(
            product, warehouseB, 1, null, today, ""
        )

        assertEquals(StockResult.InsufficientStock, result)
        assertEquals(10, env.productTotal(product))
    }

    // ----------------------------------------------------------- adjustment

    @Test
    fun `adjustment down records a negative difference`() = runTest {
        val product = env.createProduct("مسمار", warehouseA, quantity = 100)

        val result = env.inventoryRepository.recordAdjustment(
            productId = product,
            warehouseId = warehouseA,
            newQuantity = 97,
            reason = AdjustmentReason.DAMAGED,
            employeeId = null,
            date = today,
            notes = "تلف في النقل"
        )

        assertEquals(StockResult.Success, result)
        assertEquals(97, env.quantityIn(product, warehouseA))
        assertEquals(97, env.productTotal(product))

        val row = env.history(product).single()
        assertEquals(TransactionType.ADJUSTMENT, row.type)
        assertEquals(100, row.previousQuantity)
        assertEquals(97, row.newQuantity)
        assertEquals(-3, row.difference)
        assertEquals(AdjustmentReason.DAMAGED, row.reason)
        // Magnitude is always positive; the sign lives in `difference`.
        assertEquals(3, row.quantity)
    }

    @Test
    fun `adjustment up records a positive difference`() = runTest {
        val product = env.createProduct("مسمار", warehouseA, quantity = 40)

        env.inventoryRepository.recordAdjustment(
            product, warehouseA, 45, AdjustmentReason.COUNTING_ERROR, null, today, ""
        )

        val row = env.history(product).single()
        assertEquals(5, row.difference)
        assertEquals(45, env.quantityIn(product, warehouseA))
    }

    @Test
    fun `adjustment to the same quantity is refused as no change`() = runTest {
        val product = env.createProduct("مسمار", warehouseA, quantity = 40)

        val result = env.inventoryRepository.recordAdjustment(
            product, warehouseA, 40, AdjustmentReason.OTHER, null, today, ""
        )

        assertEquals(StockResult.NoChange, result)
        // No history row for a movement that did not happen.
        assertEquals(0, env.history(product).size)
    }

    @Test
    fun `adjustment to a negative quantity is refused`() = runTest {
        val product = env.createProduct("مسمار", warehouseA, quantity = 40)

        val result = env.inventoryRepository.recordAdjustment(
            product, warehouseA, -1, AdjustmentReason.LOST, null, today, ""
        )

        assertEquals(StockResult.InvalidQuantity, result)
        assertEquals(40, env.quantityIn(product, warehouseA))
    }

    // ------------------------------------------------------------- transfer

    @Test
    fun `transfer moves stock between warehouses and leaves the total unchanged`() = runTest {
        val product = env.createProduct("مسمار", warehouseA, quantity = 10)

        val result = env.inventoryRepository.recordTransfer(
            productId = product,
            fromWarehouseId = warehouseA,
            toWarehouseId = warehouseB,
            quantity = 3,
            employeeId = null,
            date = today,
            notes = ""
        )

        assertEquals(StockResult.Success, result)
        assertEquals(7, env.quantityIn(product, warehouseA))
        assertEquals(3, env.quantityIn(product, warehouseB))
        // A transfer relocates stock; it does not create or destroy any.
        assertEquals(10, env.productTotal(product))
    }

    @Test
    fun `transfer writes both legs sharing one group id`() = runTest {
        val product = env.createProduct("مسمار", warehouseA, quantity = 10)

        env.inventoryRepository.recordTransfer(
            product, warehouseA, warehouseB, 3, null, today, ""
        )

        val rows = env.history(product)
        assertEquals(2, rows.size)
        val out = rows.single { it.type == TransactionType.TRANSFER_OUT }
        val into = rows.single { it.type == TransactionType.TRANSFER_IN }

        assertNotNull(out.transferGroupId)
        // The shared id is what lets a report pair the two halves of one movement.
        assertEquals(out.transferGroupId, into.transferGroupId)
        assertEquals(10, out.previousQuantity)
        assertEquals(7, out.newQuantity)
        assertEquals(0, into.previousQuantity)
        assertEquals(3, into.newQuantity)
        // Each leg names the warehouse on the other side.
        assertEquals(warehouseB, out.counterpartWarehouseId)
        assertEquals(warehouseA, into.counterpartWarehouseId)
    }

    @Test
    fun `transfer exceeding source stock is atomic and moves nothing`() = runTest {
        val product = env.createProduct("مسمار", warehouseA, quantity = 2)

        val result = env.inventoryRepository.recordTransfer(
            product, warehouseA, warehouseB, 5, null, today, ""
        )

        assertEquals(StockResult.InsufficientStock, result)
        assertEquals(2, env.quantityIn(product, warehouseA))
        // The critical case: the destination must not have been credited before the source
        // debit failed.
        assertEquals(0, env.quantityIn(product, warehouseB))
        assertEquals(2, env.productTotal(product))
        assertEquals(0, env.history(product).size)
    }

    @Test
    fun `transfer to the same warehouse is refused`() = runTest {
        val product = env.createProduct("مسمار", warehouseA, quantity = 10)

        val result = env.inventoryRepository.recordTransfer(
            product, warehouseA, warehouseA, 1, null, today, ""
        )

        assertEquals(StockResult.SameWarehouse, result)
        assertEquals(10, env.quantityIn(product, warehouseA))
    }

    // ------------------------------------------- repeated ops stay consistent

    @Test
    fun `a sequence of movements leaves warehouse rows and product total in step`() = runTest {
        val product = env.createProduct("مسمار", warehouseA, quantity = 0)

        env.inventoryRepository.recordStockIn(
            product, warehouseA, 100, null, null, today, "", ""
        )
        env.inventoryRepository.recordStockOut(product, warehouseA, 30, null, today, "")
        env.inventoryRepository.recordTransfer(
            product, warehouseA, warehouseB, 20, null, today, ""
        )
        env.inventoryRepository.recordAdjustment(
            product, warehouseB, 18, AdjustmentReason.DAMAGED, null, today, ""
        )

        // A: 0 +100 -30 -20 = 50 · B: +20 then adjusted to 18 = 18 · total 68
        assertEquals(50, env.quantityIn(product, warehouseA))
        assertEquals(18, env.quantityIn(product, warehouseB))
        assertEquals(68, env.productTotal(product))

        val rows = env.history(product)
        // in, out, transfer-out, transfer-in, adjustment
        assertEquals(5, rows.size)
        assertTrue(rows.all { it.previousQuantity != null && it.newQuantity != null })
    }
}
