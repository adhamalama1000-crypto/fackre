package com.warehouse.inventory.data

import com.warehouse.inventory.data.local.entity.ProductUnit
import com.warehouse.inventory.data.local.entity.TransactionType
import com.warehouse.inventory.data.local.entity.UserRole
import com.warehouse.inventory.data.repository.DuplicateField
import com.warehouse.inventory.data.repository.SaveResult
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

/**
 * Product identification and validation: SKU/barcode uniqueness, blank handling, opening
 * balances, and the rule that editing a product cannot move its stock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProductValidationTest {

    private lateinit var env: TestEnvironment
    private var warehouseId = 0L

    @Before
    fun setUp() = runTest {
        env = TestEnvironment.create()
        val adminId = env.createUser("مدير", "admin@test.com", UserRole.ADMIN)
        env.signInAs(adminId)
        warehouseId = env.createWarehouse("المخزن الرئيسي", "A", isDefault = true)
    }

    @After
    fun tearDown() = env.close()

    private suspend fun save(
        id: Long? = null,
        name: String = "منتج",
        sku: String = "",
        barcode: String = "",
        quantity: Int = 0
    ): SaveResult = env.productRepository.saveProduct(
        id = id,
        name = name,
        categoryId = null,
        sku = sku,
        barcode = barcode,
        unit = ProductUnit.PIECE,
        location = "",
        notes = "",
        imagePath = null,
        lowStockThreshold = 5,
        purchaseCost = null,
        sellingPrice = null,
        initialWarehouseId = warehouseId,
        initialQuantity = quantity
    )

    // ------------------------------------------------------------ uniqueness

    @Test
    fun `duplicate barcode is refused and names the barcode field`() = runTest {
        assertTrue(save(name = "الأول", barcode = "1234567890") is SaveResult.Success)

        val result = save(name = "الثاني", barcode = "1234567890")

        assertEquals(SaveResult.Duplicate(DuplicateField.BARCODE), result)
        assertEquals(1, env.productCount())
    }

    @Test
    fun `duplicate sku is refused and names the sku field`() = runTest {
        assertTrue(save(name = "الأول", sku = "SKU-1") is SaveResult.Success)

        val result = save(name = "الثاني", sku = "SKU-1")

        assertEquals(SaveResult.Duplicate(DuplicateField.SKU), result)
        assertEquals(1, env.productCount())
    }

    @Test
    fun `a product keeps its own codes when edited`() = runTest {
        val id = (save(name = "الأول", sku = "SKU-1", barcode = "111") as SaveResult.Success).id

        // Re-saving the same row with unchanged codes must not collide with itself.
        val result = save(id = id, name = "الأول المعدل", sku = "SKU-1", barcode = "111")

        assertTrue(result is SaveResult.Success)
        assertEquals("الأول المعدل", env.productDao.getById(id)?.name)
    }

    @Test
    fun `many products may have no sku or barcode`() = runTest {
        // Blank codes are normalised to NULL, and SQLite treats NULLs as distinct in a unique
        // index — so this is the case that would break if "" were stored instead.
        assertTrue(save(name = "الأول") is SaveResult.Success)
        assertTrue(save(name = "الثاني") is SaveResult.Success)
        assertTrue(save(name = "الثالث") is SaveResult.Success)

        assertEquals(3, env.productCount())
        assertTrue(env.productDao.listOnce(null, null, false).all {
            it.product.sku == null && it.product.barcode == null
        })
    }

    @Test
    fun `blank name is refused`() = runTest {
        assertEquals(SaveResult.MissingRequiredField, save(name = "   "))
        assertEquals(0, env.productCount())
    }

    // ----------------------------------------------------------- code lookup

    @Test
    fun `lookup finds a product by barcode and by sku`() = runTest {
        val id = (save(name = "مسمار", sku = "SKU-9", barcode = "999888") as SaveResult.Success).id

        assertEquals(id, env.productRepository.findByCode("999888")?.id)
        // Falling back to SKU means a label printed with either identifier scans.
        assertEquals(id, env.productRepository.findByCode("SKU-9")?.id)
        // Surrounding whitespace from a scan must not defeat the match.
        assertEquals(id, env.productRepository.findByCode("  999888  ")?.id)
    }

    @Test
    fun `lookup of an unknown or blank code returns null`() = runTest {
        save(name = "مسمار", barcode = "999888")

        assertNull(env.productRepository.findByCode("000000"))
        assertNull(env.productRepository.findByCode(""))
        assertNull(env.productRepository.findByCode("   "))
    }

    // -------------------------------------------------------- opening balance

    @Test
    fun `opening balance is written as stock and as a history row`() = runTest {
        val id = (save(name = "مسمار", quantity = 25) as SaveResult.Success).id

        assertEquals(25, env.quantityIn(id, warehouseId))
        assertEquals(25, env.productTotal(id))

        // Stock that exists must always have a transaction explaining it.
        val row = env.history(id).single()
        assertEquals(TransactionType.STOCK_IN, row.type)
        assertEquals(0, row.previousQuantity)
        assertEquals(25, row.newQuantity)
        assertEquals(25, row.difference)
    }

    @Test
    fun `creating with a zero opening balance writes no history row`() = runTest {
        val id = (save(name = "مسمار", quantity = 0) as SaveResult.Success).id

        assertEquals(0, env.quantityIn(id, warehouseId))
        assertTrue(env.history(id).isEmpty())
    }

    @Test
    fun `editing a product does not change its quantity`() = runTest {
        val id = (save(name = "مسمار", quantity = 25) as SaveResult.Success).id

        save(id = id, name = "مسمار معدل", quantity = 999)

        // The 999 is ignored on purpose: a quantity change must go through an adjustment so
        // that it carries a reason and a history row.
        assertEquals(25, env.quantityIn(id, warehouseId))
        assertEquals(25, env.productTotal(id))
        assertEquals(1, env.history(id).size)
    }

    @Test
    fun `deleting a product keeps its history with the name preserved`() = runTest {
        val id = (save(name = "مسمار", quantity = 25) as SaveResult.Success).id
        val product = env.productDao.getById(id)!!

        env.productRepository.deleteProduct(product)

        assertNull(env.productDao.getById(id))
        // The movement survives with a null productId and the denormalized name intact.
        val row = env.history().single { it.productName == "مسمار" }
        assertNull(row.productId)
        assertNotNull(row.productName)
    }
}
