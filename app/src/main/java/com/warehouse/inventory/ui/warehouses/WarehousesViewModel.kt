package com.warehouse.inventory.ui.warehouses

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.inventory.R
import com.warehouse.inventory.data.local.entity.WarehouseEntity
import com.warehouse.inventory.data.repository.DeleteResult
import com.warehouse.inventory.data.repository.DuplicateField
import com.warehouse.inventory.data.repository.SaveResult
import com.warehouse.inventory.data.repository.WarehouseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One list row: the warehouse plus the stock it currently holds. */
data class WarehouseRow(
    val warehouse: WarehouseEntity,
    val productCount: Int,
    val totalQuantity: Int
)

/**
 * Outcome of the last save/delete attempt, as string resource ids so the ViewModel needs no
 * Context. [formError] is shown under an input in the dialog, [message] as a one-shot
 * snackbar; the screen clears both once consumed.
 */
data class WarehousesUiState(
    val submitting: Boolean = false,
    @StringRes val formError: Int? = null,
    @StringRes val message: Int? = null,
    /** Raised once after a successful save so the screen can close the form. */
    val saved: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WarehousesViewModel @Inject constructor(
    private val warehouseRepository: WarehouseRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow(WarehousesUiState())
    val state: StateFlow<WarehousesUiState> = _state.asStateFlow()

    val warehouses: StateFlow<List<WarehouseRow>> = _query
        .flatMapLatest { warehouseRepository.search(it) }
        .combine(warehouseRepository.observeTotals()) { list, totals ->
            // The totals query aggregates product_stock, so a warehouse holding nothing has
            // no row at all — absent means zero here, not unknown.
            val byWarehouse = totals.associateBy { it.warehouseId }
            list.map { warehouse ->
                val t = byWarehouse[warehouse.id]
                WarehouseRow(
                    warehouse = warehouse,
                    productCount = t?.productCount ?: 0,
                    totalQuantity = t?.totalQuantity ?: 0
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChange(value: String) { _query.value = value }

    fun save(
        id: Long?,
        name: String,
        code: String,
        location: String,
        notes: String,
        makeDefault: Boolean
    ) {
        _state.update { it.copy(submitting = true, formError = null) }
        viewModelScope.launch {
            val result = warehouseRepository.save(id, name, code, location, notes, makeDefault)
            when (result) {
                is SaveResult.Success ->
                    _state.update { it.copy(submitting = false, saved = true) }

                SaveResult.MissingRequiredField ->
                    _state.update { it.copy(submitting = false, formError = R.string.required_field) }

                is SaveResult.Duplicate -> _state.update {
                    it.copy(
                        submitting = false,
                        // Code is the only unique column; names may legitimately repeat.
                        formError = if (result.field == DuplicateField.CODE)
                            R.string.error_duplicate_warehouse_code else R.string.error_generic
                    )
                }

                SaveResult.NotAuthorized ->
                    _state.update { it.copy(submitting = false, message = R.string.error_not_authorized) }

                SaveResult.NotFound, is SaveResult.Failed ->
                    _state.update { it.copy(submitting = false, message = R.string.error_generic) }
            }
        }
    }

    fun setDefault(id: Long) {
        viewModelScope.launch {
            val message = when (warehouseRepository.setDefault(id)) {
                is SaveResult.Success -> null
                SaveResult.NotAuthorized -> R.string.error_not_authorized
                else -> R.string.error_generic
            }
            if (message != null) _state.update { it.copy(message = message) }
        }
    }

    fun delete(warehouse: WarehouseEntity) {
        viewModelScope.launch {
            val message: Int? = when (val result = warehouseRepository.delete(warehouse)) {
                DeleteResult.Success -> null
                DeleteResult.NotAuthorized -> R.string.error_not_authorized
                // The repository signals both refusals through InUse: the sentinel "last"
                // for the final warehouse, otherwise the quantity still held in it.
                is DeleteResult.InUse ->
                    if (result.details == "last") R.string.error_last_warehouse
                    else R.string.error_warehouse_not_empty
                else -> R.string.error_generic
            }
            if (message != null) _state.update { it.copy(message = message) }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun consumeSaved() = _state.update { it.copy(saved = false) }
    fun clearFormError() = _state.update { it.copy(formError = null) }
}
