package com.warehouse.inventory.ui.categories

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.inventory.R
import com.warehouse.inventory.data.local.entity.CategoryEntity
import com.warehouse.inventory.data.repository.CategoryRepository
import com.warehouse.inventory.data.repository.DeleteResult
import com.warehouse.inventory.data.repository.SaveResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * See [com.warehouse.inventory.ui.suppliers.SuppliersUiState] — same shape and the same
 * reason for it: errors travel as string resource ids because the ViewModel has no Context,
 * [formError] belongs under the dialog's input and [message] is a one-shot snackbar.
 */
data class CategoriesUiState(
    val submitting: Boolean = false,
    @StringRes val formError: Int? = null,
    @StringRes val message: Int? = null,
    val saved: Boolean = false
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CategoriesUiState())
    val state: StateFlow<CategoriesUiState> = _state.asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> = categoryRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Creates a category, or renames [existing] when editing. */
    fun save(existing: CategoryEntity?, name: String) {
        _state.update { it.copy(submitting = true, formError = null) }
        viewModelScope.launch {
            val result = if (existing == null) {
                categoryRepository.addCategory(name)
            } else {
                categoryRepository.renameCategory(existing, name)
            }
            when (result) {
                is SaveResult.Success ->
                    _state.update { it.copy(submitting = false, saved = true) }

                SaveResult.MissingRequiredField ->
                    _state.update { it.copy(submitting = false, formError = R.string.required_field) }

                // Name is the table's only unique index, so a duplicate can only be the name.
                is SaveResult.Duplicate ->
                    _state.update {
                        it.copy(submitting = false, formError = R.string.error_duplicate_category)
                    }

                SaveResult.NotAuthorized ->
                    _state.update {
                        it.copy(submitting = false, message = R.string.error_not_authorized)
                    }

                SaveResult.NotFound, is SaveResult.Failed ->
                    _state.update { it.copy(submitting = false, message = R.string.error_generic) }
            }
        }
    }

    fun delete(category: CategoryEntity) {
        viewModelScope.launch {
            val message: Int? = when (categoryRepository.deleteCategory(category)) {
                DeleteResult.Success -> null
                DeleteResult.NotAuthorized -> R.string.error_not_authorized
                // Products keep existing with a null categoryId (ON DELETE SET NULL), so
                // nothing can hold a category back; anything else here is a real failure.
                else -> R.string.error_generic
            }
            if (message != null) _state.update { it.copy(message = message) }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun consumeSaved() = _state.update { it.copy(saved = false) }
    fun clearFormError() = _state.update { it.copy(formError = null) }
}
