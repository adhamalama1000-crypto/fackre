package com.warehouse.inventory.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.inventory.data.session.SessionManager
import com.warehouse.inventory.data.session.SessionUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
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
    private val sessionManager: SessionManager
) : ViewModel() {

    val sessionState = kotlinx.coroutines.flow.flow {
        sessionManager.currentUser.collect { user ->
            emit(if (user == null) SessionState.LoggedOut else SessionState.LoggedIn(user))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SessionState.Loading)

    fun logout() {
        viewModelScope.launch { sessionManager.signOut() }
    }
}
