package com.warehouse.inventory.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.inventory.data.repository.PermissionChecker
import com.warehouse.inventory.data.repository.UserRepository
import com.warehouse.inventory.data.session.AppPreferences
import com.warehouse.inventory.data.session.SessionManager
import com.warehouse.inventory.data.session.SessionUser
import com.warehouse.inventory.data.session.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val user: SessionUser) : SessionState
}

@HiltViewModel
class RootViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val permissionChecker: PermissionChecker,
    preferences: AppPreferences
) : ViewModel() {

    /**
     * The user's appearance choice, read here rather than in the settings screen because
     * MainActivity needs it to build the theme before any screen composes. MainActivity and
     * AppNavHost both resolve this ViewModel from the activity's store, so they share one
     * instance and one session read.
     */
    val themeMode = preferences.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val sessionState = flow {
        sessionManager.currentUser.collect { user ->
            emit(if (user == null) SessionState.LoggedOut else SessionState.LoggedIn(user))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SessionState.Loading)

    /**
     * Signs out, recording the event before clearing the session.
     *
     * Order matters: the audit row needs the acting user, and once the session is cleared
     * there is no way to resolve who it was — which is why the log write comes first.
     */
    fun logout() {
        viewModelScope.launch {
            runCatching { userRepository.recordLogout(permissionChecker.currentUser()) }
            sessionManager.signOut()
        }
    }
}
