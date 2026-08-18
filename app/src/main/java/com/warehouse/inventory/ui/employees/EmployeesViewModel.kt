package com.warehouse.inventory.ui.employees

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.inventory.R
import com.warehouse.inventory.data.local.entity.EmployeeEntity
import com.warehouse.inventory.data.repository.DeleteResult
import com.warehouse.inventory.data.repository.DuplicateField
import com.warehouse.inventory.data.repository.EmployeeRepository
import com.warehouse.inventory.data.repository.SaveResult
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
 * Outcome of the last save/delete attempt, as string resource ids so the ViewModel needs no
 * Context. [formError] is shown under an input in the dialog, [message] as a one-shot
 * snackbar; the screen clears both once consumed.
 */
data class EmployeesUiState(
    val submitting: Boolean = false,
    @StringRes val formError: Int? = null,
    @StringRes val message: Int? = null,
    /** Raised once after a successful save so the screen can close the form. */
    val saved: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EmployeesViewModel @Inject constructor(
    private val employeeRepository: EmployeeRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow(EmployeesUiState())
    val state: StateFlow<EmployeesUiState> = _state.asStateFlow()

    // Deliberately search() and not observeActive(): this screen manages employees, so
    // former staff must stay visible and editable.
    val employees: StateFlow<List<EmployeeEntity>> = _query
        .flatMapLatest { employeeRepository.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChange(value: String) { _query.value = value }

    fun save(
        id: Long?,
        name: String,
        code: String,
        phone: String,
        department: String,
        notes: String,
        active: Boolean
    ) {
        _state.update { it.copy(submitting = true, formError = null) }
        viewModelScope.launch {
            val result = employeeRepository.save(id, name, code, phone, department, notes, active)
            when (result) {
                is SaveResult.Success ->
                    _state.update { it.copy(submitting = false, saved = true) }

                SaveResult.MissingRequiredField ->
                    _state.update { it.copy(submitting = false, formError = R.string.required_field) }

                is SaveResult.Duplicate -> _state.update {
                    it.copy(
                        submitting = false,
                        // The employee number is the only unique column here.
                        formError = if (result.field == DuplicateField.CODE)
                            R.string.error_duplicate_employee_code else R.string.error_generic
                    )
                }

                SaveResult.NotAuthorized ->
                    _state.update { it.copy(submitting = false, message = R.string.error_not_authorized) }

                SaveResult.NotFound, is SaveResult.Failed ->
                    _state.update { it.copy(submitting = false, message = R.string.error_generic) }
            }
        }
    }

    fun delete(employee: EmployeeEntity) {
        viewModelScope.launch {
            val message: Int? = when (employeeRepository.delete(employee)) {
                DeleteResult.Success -> null
                DeleteResult.NotAuthorized -> R.string.error_not_authorized
                // Failed, and an InUse that should not be reachable: transactions keep the
                // recorded name and null their employeeId, so nothing blocks a delete.
                else -> R.string.error_generic
            }
            if (message != null) _state.update { it.copy(message = message) }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun consumeSaved() = _state.update { it.copy(saved = false) }
    fun clearFormError() = _state.update { it.copy(formError = null) }
}
