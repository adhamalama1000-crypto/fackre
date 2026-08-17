package com.warehouse.inventory.ui.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.inventory.data.local.entity.UserEntity
import com.warehouse.inventory.data.local.entity.UserRole
import com.warehouse.inventory.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UsersViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    val users = userRepository.observeUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<UserEntity>())

    fun add(
        name: String,
        email: String,
        password: String,
        role: UserRole,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val ok = userRepository.addUser(name, email, password, role)
            onResult(ok)
        }
    }

    fun delete(user: UserEntity) {
        viewModelScope.launch { userRepository.deleteUser(user) }
    }
}
