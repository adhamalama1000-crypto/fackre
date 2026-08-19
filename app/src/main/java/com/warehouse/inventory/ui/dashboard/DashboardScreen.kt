package com.warehouse.inventory.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warehouse.inventory.R
import com.warehouse.inventory.data.local.entity.InventoryTransactionEntity
import com.warehouse.inventory.data.local.entity.ProductEntity
import com.warehouse.inventory.ui.common.Pill
import com.warehouse.inventory.ui.common.StatCard
import com.warehouse.inventory.ui.common.ThinDivider
import com.warehouse.inventory.ui.navigation.Route
import com.warehouse.inventory.ui.theme.BrandAmber
import com.warehouse.inventory.ui.theme.BrandNavy
import com.warehouse.inventory.ui.theme.Slate
import com.warehouse.inventory.ui.theme.SuccessGreen
import com.warehouse.inventory.ui.theme.TransactionColors
import com.warehouse.inventory.ui.theme.TransferTeal
import com.warehouse.inventory.ui.theme.WarningRed
import com.warehouse.inventory.util.DateUtils
import com.warehouse.inventory.util.ProductUnitLabels
import com.warehouse.inventory.util.TransactionTypeLabels
import java.util.Locale

private data class Stat(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val color: Color,
    val route: String? = null
)

private data class QuickAction(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val adminOnly: Boolean,
    val onClick: () -> Unit
)

private data class MenuTile(
    val label: String,
    val icon: ImageVector,
    val route: String,
    val adminOnly: Boolean
)

/**
 * The operational home screen: live figures, the handful of actions a warehouse worker
 * needs first, and everything else one tap away.
 *
 * Ordering is by how often it is needed rather than by feature area — stats, then quick
 * actions, then anything that needs attention (low stock), then recent activity, then the
 * full section list. Admin-only entries are filtered out for viewers here, and every
 * privileged action behind them is refused again at the repository level.
 */
@Composable
fun DashboardScreen(
    userName: String,
    isAdmin: Boolean,
    onNavigate: (String) -> Unit,
    onScanRequest: () -> Unit,
    onLogout: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val recent by viewModel.recentTransactions.collectAsStateWithLifecycle()
    val lowStockProducts by viewModel.lowStockProducts.collectAsStateWithLifecycle()

    val statItems = buildList {
        add(
            Stat(
                stringResource(R.string.total_products), stats.totalProducts.toString(),
                Icons.Filled.Widgets, BrandNavy, Route.Products.path
            )
        )
        add(
            Stat(
                stringResource(R.string.total_quantity), stats.totalQuantity.toString(),
                Icons.Filled.Numbers, SuccessGreen, Route.Inventory.path
            )
        )
        add(
            Stat(
                stringResource(R.string.low_stock), stats.lowStock.toString(),
                Icons.Filled.Warning, WarningRed, Route.Inventory.path
            )
        )
        add(
            Stat(
                stringResource(R.string.in_today), stats.inToday.toString(),
                Icons.Filled.Today, TransferTeal, Route.History.path
            )
        )
        add(
            Stat(
                stringResource(R.string.out_today), stats.outToday.toString(),
                Icons.Filled.RemoveShoppingCart, BrandAmber, Route.History.path
            )
        )
        add(
            Stat(
                stringResource(R.string.warehouses_count), stats.warehouses.toString(),
                Icons.Filled.Warehouse, Slate, Route.Warehouses.path
            )
        )
        // Only present for admins — the ViewModel emits null for viewers, so there is no
        // figure to hide here in the first place.
        stats.inventoryValue?.let { value ->
            add(
                Stat(
                    stringResource(R.string.inventory_value),
                    String.format(Locale.US, "%,.2f", value),
                    Icons.Filled.Payments, BrandNavy, Route.Reports.path
                )
            )
        }
    }

    val quickActions = listOf(
        QuickAction(
            stringResource(R.string.menu_stock_in), Icons.Filled.AddBox, SuccessGreen, true
        ) { onNavigate(Route.StockIn.create()) },
        QuickAction(
            stringResource(R.string.menu_stock_out), Icons.Filled.RemoveShoppingCart,
            BrandAmber, true
        ) { onNavigate(Route.StockOut.create()) },
        QuickAction(
            stringResource(R.string.menu_scan), Icons.Filled.QrCodeScanner, BrandNavy, false
        ) { onScanRequest() },
        QuickAction(
            stringResource(R.string.add_product), Icons.Filled.Widgets, Slate, true
        ) { onNavigate(Route.ProductForm.create()) },
        QuickAction(
            stringResource(R.string.menu_inventory), Icons.Filled.Inventory2, TransferTeal, false
        ) { onNavigate(Route.Inventory.path) },
        QuickAction(
            stringResource(R.string.menu_reports), Icons.Filled.Assessment, Slate, false
        ) { onNavigate(Route.Reports.path) }
    ).filter { isAdmin || !it.adminOnly }

    val menuTiles = listOf(
        MenuTile(stringResource(R.string.menu_products), Icons.Filled.Widgets, Route.Products.path, false),
        MenuTile(stringResource(R.string.menu_inventory), Icons.Filled.Inventory2, Route.Inventory.path, false),
        MenuTile(stringResource(R.string.menu_history), Icons.Filled.History, Route.History.path, false),
        MenuTile(stringResource(R.string.menu_reports), Icons.Filled.Assessment, Route.Reports.path, false),
        MenuTile(stringResource(R.string.menu_adjustment), Icons.Filled.Tune, Route.Adjustment.create(), true),
        MenuTile(stringResource(R.string.menu_transfer), Icons.Filled.SwapHoriz, Route.Transfer.create(), true),
        MenuTile(stringResource(R.string.menu_categories), Icons.Filled.Category, Route.Categories.path, true),
        MenuTile(stringResource(R.string.menu_warehouses), Icons.Filled.Warehouse, Route.Warehouses.path, true),
        MenuTile(stringResource(R.string.menu_suppliers), Icons.Filled.LocalShipping, Route.Suppliers.path, true),
        MenuTile(stringResource(R.string.menu_employees), Icons.Filled.Badge, Route.Employees.path, true),
        MenuTile(stringResource(R.string.menu_users), Icons.Filled.Group, Route.Users.path, true),
        MenuTile(stringResource(R.string.menu_audit_log), Icons.AutoMirrored.Filled.FactCheck, Route.AuditLog.path, true),
        MenuTile(stringResource(R.string.menu_settings), Icons.Filled.Settings, Route.Settings.path, false)
    ).filter { isAdmin || !it.adminOnly }

    Scaffold(
        topBar = {
            BrandHeader(
                userName = userName,
                isAdmin = isAdmin,
                onSettings = { onNavigate(Route.Settings.path) },
                onLogout = onLogout
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(Modifier.height(2.dp))

            // ------------------------------------------------------------ stats
            // Fixed two-column grid built from Rows rather than a LazyVerticalGrid: the item
            // count is small and bounded, and nesting a lazy grid inside a scrolling column
            // has no intrinsic height to measure against.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                statItems.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { stat ->
                            StatCard(
                                label = stat.label,
                                value = stat.value,
                                icon = stat.icon,
                                accent = stat.color,
                                onClick = stat.route?.let { route -> { onNavigate(route) } },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Keeps a lone tile on the last row at half width instead of
                        // stretching it across the screen.
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            // --------------------------------------------------- quick actions
            SectionTitle(stringResource(R.string.quick_actions))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(quickActions.size) { index ->
                    val action = quickActions[index]
                    QuickActionChip(action)
                }
            }

            // ------------------------------------------------- low stock alerts
            if (lowStockProducts.isNotEmpty()) {
                SectionRow(
                    title = stringResource(R.string.low_stock_products),
                    onViewAll = { onNavigate(Route.Inventory.path) }
                )
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        lowStockProducts.forEachIndexed { index, product ->
                            LowStockRow(
                                product = product,
                                onClick = { onNavigate(Route.ProductForm.create(product.id)) }
                            )
                            if (index != lowStockProducts.lastIndex) {
                                ThinDivider(Modifier.padding(horizontal = 14.dp))
                            }
                        }
                    }
                }
            }

            // ------------------------------------------------ recent activity
            SectionRow(
                title = stringResource(R.string.recent_transactions),
                onViewAll = { onNavigate(Route.History.path) }
            )
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (recent.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_history),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                    )
                } else {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        recent.forEachIndexed { index, transaction ->
                            TransactionRow(transaction)
                            if (index != recent.lastIndex) {
                                ThinDivider(Modifier.padding(horizontal = 14.dp))
                            }
                        }
                    }
                }
            }

            // ----------------------------------------------------- all sections
            SectionTitle(stringResource(R.string.all_sections))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                menuTiles.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { tile ->
                            MenuTileCard(
                                tile = tile,
                                onClick = { onNavigate(tile.route) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun BrandHeader(
    userName: String,
    isAdmin: Boolean,
    onSettings: () -> Unit,
    onLogout: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_brand_mark),
                contentDescription = null,
                modifier = Modifier.size(42.dp)
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.dashboard_greeting, userName),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        if (isAdmin) R.string.role_admin else R.string.role_viewer
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onSettings) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.settings)
                )
            }
            IconButton(onClick = onLogout) {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = stringResource(R.string.logout)
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun SectionRow(title: String, onViewAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onViewAll) { Text(stringResource(R.string.view_all)) }
    }
}

@Composable
private fun QuickActionChip(action: QuickAction) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .size(width = 104.dp, height = 92.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = action.onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = action.color.copy(alpha = 0.14f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        action.icon,
                        contentDescription = null,
                        tint = action.color,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = action.label,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MenuTileCard(tile: MenuTile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .height(88.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(6.dp)
        ) {
            Icon(
                tile.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = tile.label,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LowStockRow(product: ProductEntity, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = WarningRed,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.low_stock_threshold) +
                    ": ${product.lowStockThreshold}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Pill(
            text = stringResource(
                R.string.quantity_with_unit,
                product.quantity.toString(),
                stringResource(ProductUnitLabels.stringRes(product.unit))
            ),
            container = WarningRed.copy(alpha = 0.14f),
            content = WarningRed
        )
    }
}

@Composable
private fun TransactionRow(transaction: InventoryTransactionEntity) {
    val accent = TransactionColors.forType(transaction.type)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = transaction.productName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = DateUtils.formatDateTime(transaction.date) +
                    if (transaction.userName.isNotBlank()) " • ${transaction.userName}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.size(8.dp))
        Pill(
            text = stringResource(TransactionTypeLabels.stringRes(transaction.type)),
            container = accent.copy(alpha = 0.14f),
            content = accent
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = transaction.quantity.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = accent
        )
    }
}
