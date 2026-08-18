package com.warehouse.inventory.ui.suppliers

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.inventory.R
import com.warehouse.inventory.data.local.entity.SupplierEntity
import com.warehouse.inventory.data.repository.DeleteResult
import com.warehouse.inventory.data.repository.DuplicateField
import com.warehouse.inventory.data.repository.SaveResult
import com.warehouse.inventory.data.repository.SupplierRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Outcome of the last save/delete attempt.
 *
 * Errors are carried as string resource ids, not text: the ViewModel has no Context and the
 * screen is the only place that knows how to resolve them. [formError] belongs under an input
 * inside the dialog, [message] is a one-shot snackbar; both are cleared by the screen once
 * consumed so a rotation does not replay them.
 */
data class SuppliersUiState(
    val submitting: Boolean = false,
    @StringRes val formError: Int? = null,
    @StringRes val message: Int? = null,
    /** Raised once after a successful save so the screen can close the form. */
    val saved: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SuppliersViewModel @Inject constructor(
    private val supplierRepository: SupplierRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow(SuppliersUiState())
    val state: StateFlow<SuppliersUiState> = _state.asStateFlow()

    // A blank query matches everything in the DAO, so this single flow serves both the full
    // list and the filtered one — no separate observeAll() path to keep in sync.
    val suppliers: StateFlow<List<SupplierEntity>> = _query
        .flatMapLatest { supplierRepository.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChange(value: String) { _query.value = value }

    fun save(
        id: Long?,
        name: String,
        phone: String,
        email: String,
        address: String,
        notes: String
    ) {
        _state.update { it.copy(submitting = true, formError = null) }
        viewModelScope.launch {
            when (val result = supplierRepository.save(id, name, phone, email, address, notes)) {
                is SaveResult.Success ->
                    _state.update { it.copy(submitting = false, saved = true) }

                SaveResult.MissingRequiredField ->
                    _state.update { it.copy(submitting = false, formError = R.string.required_field) }

                is SaveResult.Duplicate -> _state.update {
                    it.copy(
                        submitting = false,
                        // Name is the table's only unique index; anything else would be a bug.
                        formError = if (result.field == DuplicateField.NAME)
                            R.string.error_duplicate_supplier else R.string.error_generic
                    )
                }

                SaveResult.NotAuthorized ->
                    _state.update { it.copy(submitting = false, message = R.string.error_not_authorized) }

                // The row vanished under us (deleted on another screen); nothing field-specific to say.
                SaveResult.NotFound, is SaveResult.Failed ->
                    _state.update { it.copy(submitting = false, message = R.string.error_generic) }
            }
        }
    }

    fun delete(supplier: SupplierEntity) {
        viewModelScope.launch {
            val message: Int? = when (supplierRepository.delete(supplier)) {
                DeleteResult.Success -> null
                DeleteResult.NotAuthorized -> R.string.error_not_authorized
                // Failed, and an InUse that should not be reachable: history keeps the
                // supplier's name and the foreign key nulls itself, so nothing blocks a delete.
                else -> R.string.error_generic
            }
            if (message != null) _state.update { it.copy(message = message) }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun consumeSaved() = _state.update { it.copy(saved = false) }
    fun clearFormError() = _state.update { it.copy(formError = null) }
}
