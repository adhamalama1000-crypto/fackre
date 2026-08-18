package com.warehouse.inventory.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.warehouse.inventory.R
import com.warehouse.inventory.ui.audit.AuditLogScreen
import com.warehouse.inventory.ui.auth.LoginScreen
import com.warehouse.inventory.ui.categories.CategoriesScreen
import com.warehouse.inventory.ui.dashboard.DashboardScreen
import com.warehouse.inventory.ui.employees.EmployeesScreen
import com.warehouse.inventory.ui.history.HistoryScreen
import com.warehouse.inventory.ui.inventory.InventoryScreen
import com.warehouse.inventory.ui.products.ProductFormScreen
import com.warehouse.inventory.ui.products.ProductsScreen
import com.warehouse.inventory.ui.reports.ReportsScreen
import com.warehouse.inventory.ui.scanner.BarcodeScannerScreen
import com.warehouse.inventory.ui.settings.SettingsScreen
import com.warehouse.inventory.ui.splash.SplashScreen
import com.warehouse.inventory.ui.stock.AdjustmentScreen
import com.warehouse.inventory.ui.stock.StockInScreen
import com.warehouse.inventory.ui.stock.StockOutScreen
import com.warehouse.inventory.ui.stock.TransferScreen
import com.warehouse.inventory.ui.suppliers.SuppliersScreen
import com.warehouse.inventory.ui.users.UsersScreen
import com.warehouse.inventory.ui.warehouses.WarehousesScreen

/**
 * The whole navigation graph, gated on the session.
 *
 * Three top-level states rather than routes: while the session is still being read from
 * DataStore the branded splash shows, signed-out gets a graph containing only login, and
 * signed-in gets the full graph. Because the signed-in graph does not contain a login route
 * at all, "back" can never walk out of the app into a stale authenticated screen, and
 * signing out swaps the entire graph instead of trying to pop to it.
 *
 * Admin-only destinations are still registered for viewers, but the composables receive
 * `isAdmin = false` and every privileged action is refused by the repository layer as well —
 * see PermissionChecker. Route registration is not an authorisation decision.
 */
@Composable
fun AppNavHost(rootViewModel: RootViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val session by rootViewModel.sessionState.collectAsStateWithLifecycle()

    when (val current = session) {
        is SessionState.Loading -> SplashScreen()

        is SessionState.LoggedOut -> {
            NavHost(navController = navController, startDestination = Route.Login.path) {
                composable(Route.Login.path) { LoginScreen() }
            }
        }

        is SessionState.LoggedIn -> {
            val isAdmin = current.user.isAdmin
            SignedInGraph(
                navController = navController,
                userName = current.user.name,
                isAdmin = isAdmin,
                onLogout = rootViewModel::logout
            )
        }
    }
}

@Composable
private fun SignedInGraph(
    navController: NavHostController,
    userName: String,
    isAdmin: Boolean,
    onLogout: () -> Unit
) {
    // Set when the scanner is opened standalone from the dashboard: in that mode a scan must
    // resolve to a product and navigate, whereas from a stock form it just returns the code.
    var scannerResolvesProduct by remember { mutableStateOf(false) }
    var scannerError by remember { mutableStateOf<Int?>(null) }
    var scannerResetToken by remember { mutableStateOf(0) }

    val back: () -> Unit = { navController.popBackStack() }

    /** Opens the scanner in "return the raw code to the caller" mode. */
    val openScannerForCode: () -> Unit = {
        scannerResolvesProduct = false
        scannerError = null
        navController.navigate(Route.Scanner.path)
    }

    NavHost(navController = navController, startDestination = Route.Dashboard.path) {

        composable(Route.Dashboard.path) {
            DashboardScreen(
                userName = userName,
                isAdmin = isAdmin,
                onNavigate = { navController.navigate(it) },
                onScanRequest = {
                    scannerResolvesProduct = true
                    scannerError = null
                    navController.navigate(Route.Scanner.path)
                },
                onLogout = onLogout
            )
        }

        composable(Route.Inventory.path) {
            InventoryScreen(
                onBack = back,
                onProductClick = { id -> navController.navigate(Route.ProductForm.create(id)) }
            )
        }

        composable(Route.Products.path) {
            ProductsScreen(
                isAdmin = isAdmin,
                onBack = back,
                onAddProduct = { navController.navigate(Route.ProductForm.create()) },
                onEditProduct = { id -> navController.navigate(Route.ProductForm.create(id)) }
            )
        }

        productScopedDestination(Route.ProductForm.path) { productId ->
            ProductFormScreen(
                productId = productId,
                onDone = back,
                onScanRequest = openScannerForCode,
                onAdjustStock = { id -> navController.navigate(Route.Adjustment.create(id)) }
            )
        }

        composable(Route.Categories.path) {
            CategoriesScreen(isAdmin = isAdmin, onBack = back)
        }

        composable(Route.Users.path) { UsersScreen(isAdmin = isAdmin, onBack = back) }

        composable(Route.History.path) { HistoryScreen(onBack = back) }

        composable(Route.Suppliers.path) { SuppliersScreen(isAdmin = isAdmin, onBack = back) }

        composable(Route.Employees.path) { EmployeesScreen(isAdmin = isAdmin, onBack = back) }

        composable(Route.Warehouses.path) { WarehousesScreen(isAdmin = isAdmin, onBack = back) }

        composable(Route.AuditLog.path) { AuditLogScreen(onBack = back) }

        composable(Route.Reports.path) { ReportsScreen(isAdmin = isAdmin, onBack = back) }

        composable(Route.Settings.path) { SettingsScreen(isAdmin = isAdmin, onBack = back) }

        productScopedDestination(Route.StockIn.path) { productId ->
            StockInScreen(
                preselectedProductId = productId,
                onBack = back,
                onScanRequest = openScannerForCode
            )
        }

        productScopedDestination(Route.StockOut.path) { productId ->
            StockOutScreen(
                preselectedProductId = productId,
                onBack = back,
                onScanRequest = openScannerForCode
            )
        }

        productScopedDestination(Route.Adjustment.path) { productId ->
            AdjustmentScreen(
                preselectedProductId = productId,
                onBack = back,
                onScanRequest = openScannerForCode
            )
        }

        productScopedDestination(Route.Transfer.path) { productId ->
            TransferScreen(
                preselectedProductId = productId,
                onBack = back,
                onScanRequest = openScannerForCode
            )
        }

        composable(Route.Scanner.path) {
            val scannerViewModel: ScannerViewModel = hiltViewModel()
            BarcodeScannerScreen(
                onBack = back,
                statusMessage = scannerError?.let { stringResource(it) },
                resetToken = scannerResetToken,
                onCodeScanned = { code ->
                    if (scannerResolvesProduct) {
                        // Dashboard mode: resolve to a product and open it, or stay on the
                        // camera with an error so the next label can just be scanned.
                        scannerViewModel.resolve(code) { productId ->
                            if (productId == null) {
                                scannerError = R.string.error_barcode_not_found
                                scannerResetToken++
                            } else {
                                scannerError = null
                                navController.popBackStack()
                                navController.navigate(Route.ProductForm.create(productId))
                            }
                        }
                    } else {
                        // Form mode: hand the raw code back to the caller's ViewModel, which
                        // is scoped to the previous back stack entry, then pop.
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(NavKeys.SCANNED_CODE, code)
                        navController.popBackStack()
                    }
                }
            )
        }
    }
}

/**
 * Registers a destination carrying the optional `productId` argument, translating the `-1`
 * sentinel into a null so screens never have to know about it.
 */
private fun androidx.navigation.NavGraphBuilder.productScopedDestination(
    route: String,
    content: @Composable (productId: Long?) -> Unit
) {
    composable(
        route = route,
        arguments = listOf(
            navArgument(Route.ARG_PRODUCT_ID) {
                type = NavType.LongType
                defaultValue = Route.NONE
            }
        )
    ) { entry ->
        val raw = entry.arguments?.getLong(Route.ARG_PRODUCT_ID) ?: Route.NONE
        content(raw.takeIf { it > 0 })
    }
}
