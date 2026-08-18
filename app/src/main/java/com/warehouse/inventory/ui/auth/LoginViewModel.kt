package com.warehouse.inventory.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.inventory.data.repository.LoginResult
import com.warehouse.inventory.data.repository.UserRepository
import com.warehouse.inventory.data.session.SessionManager
import com.warehouse.inventory.data.session.SessionUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: LoginError? = null
)

enum class LoginError { EMPTY, INVALID_EMAIL, INVALID }

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun login() {
        val current = _state.value
        if (current.email.isBlank() || current.password.isBlank()) {
            _state.update { it.copy(error = LoginError.EMPTY) }
            return
        }
        // Checked before hitting the database so an obvious typo says "bad email format"
        // rather than the misleading "wrong email or password".
        if (!EMAIL_PATTERN.matches(current.email.trim())) {
            _state.update { it.copy(error = LoginError.INVALID_EMAIL) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = userRepository.login(current.email, current.password)) {
                is LoginResult.Success -> {
                    val u = result.user
                    sessionManager.signIn(
                        SessionUser(id = u.id, name = u.name, email = u.email, role = u.role)
                    )
                    // Session flow drives navigation; no manual nav needed.
                }
                LoginResult.InvalidCredentials -> {
                    _state.update { it.copy(loading = false, error = LoginError.INVALID) }
                }
            }
        }
    }

    private companion object {
        /**
         * Deliberately permissive: this only catches obvious typos (missing @, missing dot,
         * stray spaces). Full RFC 5322 validation would reject addresses that are legal and
         * in use, and the credential check is the real gate anyway.
         */
        val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")
    }
}
