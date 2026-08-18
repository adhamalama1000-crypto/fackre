package com.warehouse.inventory.ui.stock

import androidx.annotation.StringRes
import com.warehouse.inventory.R
import com.warehouse.inventory.data.local.entity.EmployeeEntity
import com.warehouse.inventory.data.local.entity.ProductEntity
import com.warehouse.inventory.data.local.entity.ProductWithCategory
import com.warehouse.inventory.data.local.entity.SupplierEntity
import com.warehouse.inventory.data.local.entity.WarehouseEntity
import com.warehouse.inventory.data.repository.EmployeeRepository
import com.warehouse.inventory.data.repository.InventoryRepository
import com.warehouse.inventory.data.repository.ProductRepository
import com.warehouse.inventory.data.repository.StockResult
import com.warehouse.inventory.data.repository.SupplierRepository
import com.warehouse.inventory.data.repository.WarehouseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The option lists every stock form needs, behind one injectable seam.
 *
 * Without this each of the four stock ViewModels would constructor-inject the same four
 * repositories just to populate the same four dropdowns. Collecting them here keeps those
 * ViewModels down to their actual dependencies: this, plus the one repository that performs
 * their operation.
 */
@Singleton
class StockFormData @Inject constructor(
    private val productRepository: ProductRepository,
    private val warehouseRepository: WarehouseRepository,
    private val employeeRepository: EmployeeRepository,
    private val supplierRepository: SupplierRepository,
    private val inventoryRepository: InventoryRepository
) {
    fun products(): Flow<List<ProductWithCategory>> = productRepository.observeAll()

    fun warehouses(): Flow<List<WarehouseEntity>> = warehouseRepository.observeAll()

    /** Active employees only — a form should not offer someone who has left. */
    fun employees(): Flow<List<EmployeeEntity>> = employeeRepository.observeActive()

    fun suppliers(): Flow<List<SupplierEntity>> = supplierRepository.observeAll()

    suspend fun defaultWarehouseId(): Long? = warehouseRepository.getDefault()?.id

    /** Resolves a scanned barcode (or SKU) to a product; null when nothing matches. */
    suspend fun findByCode(code: String): ProductEntity? = productRepository.findByCode(code)

    /** Stock on hand for one product in one warehouse — drives the balance preview. */
    suspend fun quantityIn(productId: Long, warehouseId: Long): Int =
        inventoryRepository.quantityIn(productId, warehouseId)
}

/**
 * Maps a [StockResult] to the Arabic message the user sees.
 *
 * Shared by all four stock screens so the same failure never gets two different wordings,
 * and exhaustive over the sealed interface so a new result case cannot be added without
 * being given a message.
 */
object StockResultMessages {

    /** Message resource for a result, or null for [StockResult.Success]. */
    @StringRes
    fun messageFor(result: StockResult): Int? = when (result) {
        StockResult.Success -> null
        StockResult.InvalidQuantity -> R.string.error_invalid_quantity
        StockResult.InsufficientStock -> R.string.error_insufficient_stock
        StockResult.ProductNotFound -> R.string.error_product_not_found
        StockResult.WarehouseNotFound -> R.string.error_warehouse_not_found
        StockResult.SameWarehouse -> R.string.error_same_warehouse
        StockResult.NoChange -> R.string.error_no_change
        StockResult.InvalidDate -> R.string.error_invalid_date
        StockResult.NotAuthorized -> R.string.error_not_authorized
        is StockResult.Failed -> R.string.error_generic
    }
}
