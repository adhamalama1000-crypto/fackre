package com.warehouse.inventory.ui.users

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.inventory.R
import com.warehouse.inventory.data.local.entity.UserEntity
import com.warehouse.inventory.data.local.entity.UserRole
import com.warehouse.inventory.data.repository.DeleteResult
import com.warehouse.inventory.data.repository.DuplicateField
import com.warehouse.inventory.data.repository.MIN_PASSWORD_LENGTH
import com.warehouse.inventory.data.repository.SaveResult
import com.warehouse.inventory.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UsersUiState(
    val submitting: Boolean = false,
    @StringRes val formError: Int? = null,
    @StringRes val message: Int? = null,
    val saved: Boolean = false
)

@HiltViewModel
class UsersViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow(UsersUiState())
    val state: StateFlow<UsersUiState> = _state.asStateFlow()

    val users: StateFlow<List<UserEntity>> = userRepository.observeUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Creates a user, or updates [existing].
     *
     * On update the email is deliberately not editable: it is the login identifier and the
     * unique key other rows were created against. A blank [password] on update keeps the
     * current one rather than clearing it.
     */
    fun save(
        existing: UserEntity?,
        name: String,
        email: String,
        password: String,
        role: UserRole
    ) {
        _state.update { it.copy(submitting = true, formError = null) }
        viewModelScope.launch {
            val result = if (existing == null) {
                userRepository.addUser(name, email, password, role)
            } else {
                userRepository.updateUser(existing.id, name, role, password)
            }
            when (result) {
                is SaveResult.Success ->
                    _state.update { it.copy(submitting = false, saved = true) }

                // Covers both a blank name/email and a too-short password; the password rule
                // is the one the user is most likely to have tripped, and the only one with
                // a non-obvious threshold, so that is what the message names.
                SaveResult.MissingRequiredField -> _state.update {
                    it.copy(
                        submitting = false,
                        formError = if (password.isNotEmpty() && password.length < MIN_PASSWORD_LENGTH)
                            R.string.error_password_too_short else R.string.required_field
                    )
                }

                is SaveResult.Duplicate -> _state.update {
                    it.copy(
                        submitting = false,
                        formError = if (result.field == DuplicateField.EMAIL)
                            R.string.error_email_exists else R.string.error_generic
                    )
                }

                SaveResult.NotAuthorized ->
                    _state.update {
                        it.copy(submitting = false, message = R.string.error_not_authorized)
                    }

                SaveResult.NotFound ->
                    _state.update { it.copy(submitting = false, message = R.string.error_generic) }

                is SaveResult.Failed -> _state.update {
                    it.copy(
                        submitting = false,
                        // Demoting the only admin would lock everyone out of every admin
                        // operation, so the repository refuses it with this marker.
                        message = if (result.message == LAST_ADMIN) R.string.error_last_admin
                        else R.string.error_generic
                    )
                }
            }
        }
    }

    fun delete(user: UserEntity) {
        viewModelScope.launch {
            val message: Int? = when (val result = userRepository.deleteUser(user)) {
                DeleteResult.Success -> null
                DeleteResult.NotAuthorized -> R.string.error_not_authorized
                is DeleteResult.InUse -> when (result.details) {
                    SELF -> R.string.error_delete_self
                    LAST_ADMIN -> R.string.error_last_admin
                    else -> R.string.error_generic
                }
                is DeleteResult.Failed -> R.string.error_generic
            }
            if (message != null) _state.update { it.copy(message = message) }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun consumeSaved() = _state.update { it.copy(saved = false) }
    fun clearFormError() = _state.update { it.copy(formError = null) }

    private companion object {
        // Markers the repository puts in the result to distinguish its refusals.
        const val LAST_ADMIN = "last_admin"
        const val SELF = "self"
    }
}
