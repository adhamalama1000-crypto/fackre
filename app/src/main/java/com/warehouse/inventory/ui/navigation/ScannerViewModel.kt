package com.warehouse.inventory.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.inventory.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Turns a scanned code into a product id, for the scanner's standalone mode.
 *
 * Only the dashboard's "scan and open the product" flow needs this. When the scanner is
 * opened from a stock form it hands the raw code back through the navigation result and the
 * form's own ViewModel resolves it, because that ViewModel also has to select the product,
 * load its per-warehouse balance and validate against it.
 *
 * The lookup matches barcode first and falls back to SKU — see
 * [ProductRepository.findByCode] — so a label printed with either identifier scans.
 */
@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _resolving = MutableStateFlow(false)
    val resolving: StateFlow<Boolean> = _resolving.asStateFlow()

    /**
     * Looks [code] up and reports the matching product id, or null when nothing matches so
     * the caller can show "الباركود غير مسجل" and keep the camera open.
     *
     * Concurrent scans are ignored while a lookup is in flight: the analyzer can deliver the
     * same label on several consecutive frames, and without this guard one scan could
     * navigate twice.
     */
    fun resolve(code: String, onResult: (Long?) -> Unit) {
        if (_resolving.value) return
        _resolving.value = true
        viewModelScope.launch {
            val productId = runCatching { productRepository.findByCode(code)?.id }.getOrNull()
            _resolving.value = false
            onResult(productId)
        }
    }
}
