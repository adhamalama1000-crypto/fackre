package com.warehouse.inventory.data

import com.warehouse.inventory.data.local.entity.AdjustmentReason
import com.warehouse.inventory.data.local.entity.AuditAction
import com.warehouse.inventory.data.local.entity.ProductUnit
import com.warehouse.inventory.data.local.entity.UserRole
import com.warehouse.inventory.data.repository.AuditFilter
import com.warehouse.inventory.data.repository.SaveResult
import com.warehouse.inventory.data.repository.StockResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Role enforcement below the UI.
 *
 * The point of these tests is that hiding a button is not the control: a viewer calling the
 * repository directly — which is what a bypassed or mis-wired screen amounts to — must still
 * be refused, and the attempt must be recorded.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RolePermissionTest {

    private lateinit var env: TestEnvironment
    private var adminId = 0L
    private var viewerId = 0L
    private var warehouseId = 0L
    private var productId = 0L

    private val today = System.currentTimeMillis()

    @Before
    fun setUp() = runTest {
        env = TestEnvironment.create()
        adminId = env.createUser("مدير", "admin@test.com", UserRole.ADMIN)
        viewerId = env.createUser("مشاهد", "viewer@test.com", UserRole.VIEWER)
        // Set the fixture up as the admin, then hand over to the viewer per test.
        env.signInAs(adminId)
        warehouseId = env.createWarehouse("المخزن الرئيسي", "A", isDefault = true)
        productId = env.createProduct("مسمار", warehouseId, quantity = 50)
    }

    @After
    fun tearDown() = env.close()

    @Test
    fun `viewer cannot receive stock`() = runTest {
        env.signInAs(viewerId)

        val result = env.inventoryRepository.recordStockIn(
            productId, warehouseId, 10, null, null, today, "", ""
        )

        assertEquals(StockResult.NotAuthorized, result)
        assertEquals(50, env.quantityIn(productId, warehouseId))
    }

    @Test
    fun `viewer cannot issue stock`() = runTest {
        env.signInAs(viewerId)

        val result = env.inventoryRepository.recordStockOut(
            productId, warehouseId, 10, null, today, ""
        )

        assertEquals(StockResult.NotAuthorized, result)
        assertEquals(50, env.quantityIn(productId, warehouseId))
    }

    @Test
    fun `viewer cannot adjust stock`() = runTest {
        env.signInAs(viewerId)

        val result = env.inventoryRepository.recordAdjustment(
            productId, warehouseId, 10, AdjustmentReason.LOST, null, today, ""
        )

        assertEquals(StockResult.NotAuthorized, result)
        assertEquals(50, env.quantityIn(productId, warehouseId))
    }

    @Test
    fun `viewer cannot transfer stock`() = runTest {
        val other = env.createWarehouse("المخزن الفرعي", "B")
        env.signInAs(viewerId)

        val result = env.inventoryRepository.recordTransfer(
            productId, warehouseId, other, 5, null, today, ""
        )

        assertEquals(StockResult.NotAuthorized, result)
        assertEquals(50, env.quantityIn(productId, warehouseId))
        assertEquals(0, env.quantityIn(productId, other))
    }

    @Test
    fun `viewer cannot create a product`() = runTest {
        env.signInAs(viewerId)

        val result = env.productRepository.saveProduct(
            id = null,
            name = "منتج جديد",
            categoryId = null,
            sku = "",
            barcode = "",
            unit = ProductUnit.PIECE,
            location = "",
            notes = "",
            imagePath = null,
            lowStockThreshold = 5,
            purchaseCost = null,
            sellingPrice = null,
            initialWarehouseId = warehouseId,
            initialQuantity = 5
        )

        assertEquals(SaveResult.NotAuthorized, result)
        // Still just the product the fixture created.
        assertEquals(1, env.productCount())
    }

    @Test
    fun `signed out caller is refused`() = runTest {
        env.signOut()

        val result = env.inventoryRepository.recordStockOut(
            productId, warehouseId, 1, null, today, ""
        )

        assertEquals(StockResult.NotAuthorized, result)
        assertEquals(50, env.quantityIn(productId, warehouseId))
    }

    @Test
    fun `refused attempts are written to the audit log`() = runTest {
        env.signInAs(viewerId)

        env.inventoryRepository.recordStockOut(productId, warehouseId, 10, null, today, "")

        val denials = env.auditLog().filter { it.action == AuditAction.PERMISSION_DENIED }
        assertEquals(1, denials.size)
        val denial = denials.single()
        assertEquals(viewerId, denial.userId)
        // The operation name is recorded so an audit reader can see what was attempted.
        assertEquals("stock_out", denial.targetName)
    }

    @Test
    fun `role is read from the database not the session`() = runTest {
        // Sign in while still an admin, then demote the row underneath the live session. This
        // is the rooted-device case: the session file could claim ADMIN forever, so the check
        // must consult the users table instead.
        env.signInAs(adminId)
        val admin = env.userDao.getById(adminId)!!
        env.userDao.update(admin.copy(role = UserRole.VIEWER))

        assertFalse(env.permissionChecker.isAdmin())
        assertEquals(
            StockResult.NotAuthorized,
            env.inventoryRepository.recordStockOut(productId, warehouseId, 1, null, today, "")
        )
    }

    @Test
    fun `viewer reading the audit log gets nothing`() = runTest {
        // Generate a row as the admin first, so an empty result cannot be a false pass.
        env.inventoryRepository.recordStockOut(productId, warehouseId, 1, null, today, "")
        assertTrue(env.auditLog().isNotEmpty())

        env.signInAs(viewerId)
        val visible = env.auditLogRepository.observeFiltered(AuditFilter()).first()
        assertTrue(visible.isEmpty())
    }

    @Test
    fun `admin can perform stock operations`() = runTest {
        // The counterpart to every refusal above: with the admin role the same call succeeds,
        // so the tests above are proving authorisation and not a broken fixture.
        val result = env.inventoryRepository.recordStockOut(
            productId, warehouseId, 10, null, today, ""
        )

        assertEquals(StockResult.Success, result)
        assertEquals(40, env.quantityIn(productId, warehouseId))
    }
}
