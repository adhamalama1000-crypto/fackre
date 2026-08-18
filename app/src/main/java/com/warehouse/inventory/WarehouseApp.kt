package com.warehouse.inventory

import android.app.Application
import com.warehouse.inventory.data.repository.UserRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class WarehouseApp : Application() {

    @Inject
    lateinit var userRepository: UserRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Seed the default admin account + starter categories on first launch.
        appScope.launch {
            runCatching { userRepository.seedIfEmpty() }
        }
    }
}
