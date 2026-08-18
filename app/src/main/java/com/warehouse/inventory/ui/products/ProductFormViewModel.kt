package com.warehouse.inventory.ui.products

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.inventory.R
import com.warehouse.inventory.data.local.entity.CategoryEntity
import com.warehouse.inventory.data.local.entity.ProductUnit
import com.warehouse.inventory.data.local.entity.WarehouseEntity
import com.warehouse.inventory.data.local.entity.WarehouseStock
import com.warehouse.inventory.data.repository.CategoryRepository
import com.warehouse.inventory.data.repository.DuplicateField
import com.warehouse.inventory.data.repository.InventoryRepository
import com.warehouse.inventory.data.repository.ProductRepository
import com.warehouse.inventory.data.repository.SaveResult
import com.warehouse.inventory.data.repository.WarehouseRepository
import com.warehouse.inventory.data.session.SessionManager
import com.warehouse.inventory.ui.navigation.NavKeys
import com.warehouse.inventory.util.ImageStorage
import com.warehouse.inventory.util.LowStockNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductFormState(
    val productId: Long? = null,
    val name: String = "",
    val categoryId: Long? = null,
    val sku: String = "",
    val barcode: String = "",
    val unit: ProductUnit = ProductUnit.PIECE,
    val location: String = "",
    val notes: String = "",
    val imagePath: String? = null,
    val lowStockThreshold: String = "5",
    val purchaseCost: String = "",
    val sellingPrice: String = "",

    /** Create-only: where the opening count is placed, and how much of it. */
    val initialWarehouseId: Long? = null,
    val initialQuantity: String = "0",

    /** Edit-only: the current total, shown read-only. Stock moves only via transactions. */
    val currentQuantity: Int = 0,

    val loading: Boolean = false,
    val submitting: Boolean = false,
    val nameError: Boolean = false,
    @StringRes val skuError: Int? = null,
    @StringRes val barcodeError: Int? = null,
    @StringRes val message: Int? = null,
    val saved: Boolean = false
) {
    val isEditing: Boolean get() = productId != null
}

/**
 * Backs both "add product" and "edit product".
 *
 * The quantity field only exists when creating, as an opening balance that the repository
 * writes as a real STOCK_IN transaction. Editing deliberately cannot change the quantity:
 * every later movement has to be a receipt, an issue, an adjustment or a transfer so that
 * the balance always has history behind it. The edit screen shows the current total
 * read-only and points at those operations instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProductFormViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val warehouseRepository: WarehouseRepository,
    private val inventoryRepository: InventoryRepository,
    private val lowStockNotifier: LowStockNotifier,
    categoryRepository: CategoryRepository,
    sessionManager: SessionManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(ProductFormState())
    val state: StateFlow<ProductFormState> = _state.asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> = categoryRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val warehouses: StateFlow<List<WarehouseEntity>> = warehouseRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Cost and price are financial data; viewers do not get the fields at all. */
    val showFinancials: StateFlow<Boolean> = sessionManager.currentUser
        .map { it?.isAdmin == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Per-warehouse breakdown for the product being edited. Empty while creating. */
    val stockByWarehouse: StateFlow<List<WarehouseStock>> = _state
        .map { it.productId }
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else inventoryRepository.observeStockByWarehouse(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Codes handed back by the scanner destination; see [NavKeys]. */
    val scannedCode: StateFlow<String?> =
        savedStateHandle.getStateFlow(NavKeys.SCANNED_CODE, null)

    private var loadedFor: Long? = null

    /**
     * Loads an existing product, or prepares a blank form.
     *
     * Idempotent: the screen calls this from a `LaunchedEffect`, and reloading after the user
     * has started typing would discard their edits.
     */
    fun load(productId: Long?) {
        if (productId == null) {
            if (loadedFor == NEW_PRODUCT) return
            loadedFor = NEW_PRODUCT
            // Preselect the default warehouse so the opening balance has somewhere to go
            // without the user having to choose in the common single-warehouse case.
            viewModelScope.launch {
                warehouseRepository.getDefault()?.let { warehouse ->
                    _state.update {
                        if (it.initialWarehouseId == null) it.copy(initialWarehouseId = warehouse.id)
                        else it
                    }
                }
            }
            return
        }
        if (loadedFor == productId) return
        loadedFor = productId

        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val product = productRepository.getById(productId)
            if (product == null) {
                _state.update {
                    it.copy(loading = false, message = R.string.error_product_not_found)
                }
                return@launch
            }
            _state.update {
                it.copy(
                    productId = product.id,
                    name = product.name,
                    categoryId = product.categoryId,
                    sku = product.sku.orEmpty(),
                    barcode = product.barcode.orEmpty(),
                    unit = product.unit,
                    location = product.location,
                    notes = product.notes,
                    imagePath = product.imagePath,
                    lowStockThreshold = product.lowStockThreshold.toString(),
                    purchaseCost = product.purchaseCost?.toString().orEmpty(),
                    sellingPrice = product.sellingPrice?.toString().orEmpty(),
                    currentQuantity = product.quantity,
                    loading = false
                )
            }
        }
    }

    // ------------------------------------------------------------ field edits

    fun onName(value: String) = _state.update { it.copy(name = value, nameError = false) }
    fun onCategory(id: Long?) = _state.update { it.copy(categoryId = id) }
    fun onSku(value: String) = _state.update { it.copy(sku = value, skuError = null) }
    fun onBarcode(value: String) = _state.update { it.copy(barcode = value, barcodeError = null) }
    fun onUnit(unit: ProductUnit) = _state.update { it.copy(unit = unit) }
    fun onLocation(value: String) = _state.update { it.copy(location = value) }
    fun onNotes(value: String) = _state.update { it.copy(notes = value) }
    fun onInitialWarehouse(id: Long) = _state.update { it.copy(initialWarehouseId = id) }

    fun onThreshold(value: String) =
        _state.update { it.copy(lowStockThreshold = value.filter(Char::isDigit)) }

    fun onInitialQuantity(value: String) =
        _state.update { it.copy(initialQuantity = value.filter(Char::isDigit)) }

    fun onPurchaseCost(value: String) =
        _state.update { it.copy(purchaseCost = value.filterDecimal()) }

    fun onSellingPrice(value: String) =
        _state.update { it.copy(sellingPrice = value.filterDecimal()) }

    fun onImageCaptured(path: String?) {
        if (path == null) return
        // Drop the previous file if the photo was replaced before saving, so abandoned
        // captures do not accumulate in app storage.
        val old = _state.value.imagePath
        if (old != null && old != path) ImageStorage.deleteIfExists(old)
        _state.update { it.copy(imagePath = path) }
    }

    /** Fills the barcode field from a scan and clears the nav result so it fires once. */
    fun onScannedCode(code: String) {
        savedStateHandle[NavKeys.SCANNED_CODE] = null
        _state.update { it.copy(barcode = code.trim(), barcodeError = null) }
    }

    // ----------------------------------------------------------------- saving

    fun save() {
        val current = _state.value
        if (current.submitting) return

        if (current.name.isBlank()) {
            _state.update { it.copy(nameError = true) }
            return
        }

        _state.update { it.copy(submitting = true, skuError = null, barcodeError = null) }
        viewModelScope.launch {
            val result = productRepository.saveProduct(
                id = current.productId,
                name = current.name,
                categoryId = current.categoryId,
                sku = current.sku,
                barcode = current.barcode,
                unit = current.unit,
                location = current.location,
                notes = current.notes,
                imagePath = current.imagePath,
                lowStockThreshold = current.lowStockThreshold.toIntOrNull()
                    ?: DEFAULT_THRESHOLD,
                purchaseCost = current.purchaseCost.toDoubleOrNull(),
                sellingPrice = current.sellingPrice.toDoubleOrNull(),
                initialWarehouseId = current.initialWarehouseId,
                initialQuantity = if (current.isEditing) 0
                else current.initialQuantity.toIntOrNull() ?: 0
            )

            when (result) {
                is SaveResult.Success -> {
                    // A new product may already be below its threshold on day one.
                    lowStockNotifier.refresh()
                    _state.update { it.copy(submitting = false, saved = true) }
                }

                SaveResult.MissingRequiredField ->
                    _state.update { it.copy(submitting = false, nameError = current.name.isBlank()) }

                is SaveResult.Duplicate -> _state.update {
                    when (result.field) {
                        DuplicateField.SKU ->
                            it.copy(submitting = false, skuError = R.string.error_duplicate_sku)
                        DuplicateField.BARCODE ->
                            it.copy(submitting = false, barcodeError = R.string.error_duplicate_barcode)
                        else -> it.copy(submitting = false, message = R.string.error_generic)
                    }
                }

                SaveResult.NotAuthorized ->
                    _state.update {
                        it.copy(submitting = false, message = R.string.error_not_authorized)
                    }

                SaveResult.NotFound ->
                    _state.update {
                        it.copy(submitting = false, message = R.string.error_product_not_found)
                    }

                is SaveResult.Failed -> _state.update {
                    it.copy(
                        submitting = false,
                        // A product must live in a warehouse; there is nowhere to put the
                        // opening balance until one exists.
                        message = if (result.message == NO_WAREHOUSE) R.string.error_no_warehouse
                        else R.string.error_generic
                    )
                }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /**
     * Keeps digits and at most one decimal separator, so a price field cannot end up as
     * something `toDoubleOrNull()` silently turns into null on save.
     */
    private fun String.filterDecimal(): String {
        val filtered = filter { it.isDigit() || it == '.' }
        val firstDot = filtered.indexOf('.')
        if (firstDot < 0) return filtered
        return filtered.substring(0, firstDot + 1) +
            filtered.substring(firstDot + 1).filter(Char::isDigit)
    }

    private companion object {
        /** Sentinel distinguishing "blank form prepared" from "nothing loaded yet". */
        const val NEW_PRODUCT = -1L
        const val DEFAULT_THRESHOLD = 5
        const val NO_WAREHOUSE = "no_warehouse"
    }
}
