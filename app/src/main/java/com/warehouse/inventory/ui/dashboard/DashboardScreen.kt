package com.warehouse.inventory.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warehouse.inventory.R
import com.warehouse.inventory.ui.navigation.Route
import com.warehouse.inventory.ui.theme.Amber40
import com.warehouse.inventory.ui.theme.Blue40
import com.warehouse.inventory.ui.theme.SuccessGreen
import com.warehouse.inventory.ui.theme.WarningRed

private data class StatItem(val label: String, val value: String, val icon: ImageVector, val color: Color)
private data class MenuItem(val label: String, val icon: ImageVector, val route: String, val adminOnly: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    userName: String,
    isAdmin: Boolean,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    val statItems = listOf(
        StatItem(stringResource(R.string.total_products), stats.totalProducts.toString(), Icons.Filled.Widgets, Blue40),
        StatItem(stringResource(R.string.total_quantity), stats.totalQuantity.toString(), Icons.Filled.Numbers, SuccessGreen),
        StatItem(stringResource(R.string.low_stock), stats.lowStock.toString(), Icons.Filled.Warning, WarningRed),
        StatItem(stringResource(R.string.out_today), stats.outToday.toString(), Icons.Filled.Today, Amber40),
    )

    val menuItems = buildList {
        add(MenuItem(stringResource(R.string.menu_products), Icons.Filled.Widgets, Route.Products.path, adminOnly = false))
        add(MenuItem(stringResource(R.string.menu_inventory), Icons.Filled.Inventory2, Route.Inventory.path, adminOnly = false))
        add(MenuItem(stringResource(R.string.menu_stock_out), Icons.Filled.RemoveShoppingCart, Route.StockOut.create(), adminOnly = true))
        add(MenuItem(stringResource(R.string.menu_categories), Icons.Filled.Category, Route.Categories.path, adminOnly = true))
        add(MenuItem(stringResource(R.string.menu_history), Icons.Filled.History, Route.History.path, adminOnly = false))
        add(MenuItem(stringResource(R.string.menu_users), Icons.Filled.Group, Route.Users.path, adminOnly = true))
    }.filter { isAdmin || !it.adminOnly }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.dashboard), style = MaterialTheme.typography.titleLarge)
                        Text(userName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.logout))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding)
        ) {
            // Stat cards
            items(statItems) { stat -> StatCard(stat) }

            // Section spacer / header spanning both columns
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                Spacer(Modifier.height(8.dp))
            }

            // Large action buttons
            items(menuItems) { item ->
                MenuButton(item) { onNavigate(item.route) }
            }
        }
    }
}

@Composable
private fun StatCard(stat: StatItem) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Surface(
                shape = CircleShape,
                color = stat.color.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(stat.icon, contentDescription = null, tint = stat.color, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(stat.value, style = MaterialTheme.typography.headlineMedium)
            Text(stat.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun MenuButton(item: MenuItem, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                item.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp)
            )
            Text(
                item.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
