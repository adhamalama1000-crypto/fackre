package com.warehouse.inventory.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.warehouse.inventory.ui.auth.LoginScreen
import com.warehouse.inventory.ui.categories.CategoriesScreen
import com.warehouse.inventory.ui.dashboard.DashboardScreen
import com.warehouse.inventory.ui.history.HistoryScreen
import com.warehouse.inventory.ui.inventory.InventoryScreen
import com.warehouse.inventory.ui.products.ProductFormScreen
import com.warehouse.inventory.ui.products.ProductsScreen
import com.warehouse.inventory.ui.stockout.StockOutScreen
import com.warehouse.inventory.ui.users.UsersScreen

@Composable
fun AppNavHost(rootViewModel: RootViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val session by rootViewModel.sessionState.collectAsStateWithLifecycle()

    when (val s = session) {
        is SessionState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is SessionState.LoggedOut -> {
            NavHost(navController = navController, startDestination = Route.Login.path) {
                composable(Route.Login.path) { LoginScreen() }
            }
        }

        is SessionState.LoggedIn -> {
            val isAdmin = s.user.isAdmin
            NavHost(navController = navController, startDestination = Route.Dashboard.path) {

                composable(Route.Dashboard.path) {
                    DashboardScreen(
                        userName = s.user.name,
                        isAdmin = isAdmin,
                        onNavigate = { navController.navigate(it) },
                        onLogout = { rootViewModel.logout() }
                    )
                }

                composable(Route.Inventory.path) {
                    InventoryScreen(onBack = { navController.popBackStack() })
                }

                composable(Route.Products.path) {
                    ProductsScreen(
                        isAdmin = isAdmin,
                        onBack = { navController.popBackStack() },
                        onAddProduct = { navController.navigate(Route.ProductForm.create()) },
                        onEditProduct = { id -> navController.navigate(Route.ProductForm.create(id)) }
                    )
                }

                composable(
                    route = Route.ProductForm.path,
                    arguments = listOf(navArgument("productId") {
                        type = NavType.LongType; defaultValue = -1L
                    })
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getLong("productId") ?: -1L
                    ProductFormScreen(
                        productId = if (id > 0) id else null,
                        onDone = { navController.popBackStack() }
                    )
                }

                composable(Route.Categories.path) {
                    CategoriesScreen(onBack = { navController.popBackStack() })
                }

                composable(Route.Users.path) {
                    UsersScreen(onBack = { navController.popBackStack() })
                }

                composable(Route.History.path) {
                    HistoryScreen(onBack = { navController.popBackStack() })
                }

                composable(
                    route = Route.StockOut.path,
                    arguments = listOf(navArgument("productId") {
                        type = NavType.LongType; defaultValue = -1L
                    })
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getLong("productId") ?: -1L
                    StockOutScreen(
                        preselectedProductId = if (id > 0) id else null,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
