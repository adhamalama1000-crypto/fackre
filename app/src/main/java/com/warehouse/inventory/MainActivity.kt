package com.warehouse.inventory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warehouse.inventory.ui.navigation.AppNavHost
import com.warehouse.inventory.ui.navigation.RootViewModel
import com.warehouse.inventory.ui.theme.WarehouseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Drop the branded cold-start drawable now that Compose is about to paint. Leaving it
        // as the window background would sit behind every screen: a full-screen overdraw that
        // would also show through anywhere the content is translucent.
        setTheme(R.style.Theme_Rasid_Main)

        setContent {
            // Resolved from the activity's ViewModel store, so this is the same RootViewModel
            // instance AppNavHost uses — one session read, one preferences read.
            val rootViewModel: RootViewModel = hiltViewModel()
            val themeMode by rootViewModel.themeMode.collectAsStateWithLifecycle()

            WarehouseTheme(darkTheme = themeMode.isDark(isSystemInDarkTheme())) {
                // Force right-to-left layout for Arabic UI across the whole app.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppNavHost(rootViewModel = rootViewModel)
                    }
                }
            }
        }
    }
}
