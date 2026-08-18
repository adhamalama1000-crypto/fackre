package com.warehouse.inventory.ui.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warehouse.inventory.R
import com.warehouse.inventory.data.local.entity.ProductWithCategory
import com.warehouse.inventory.ui.common.AppTopBar
import com.warehouse.inventory.ui.common.EmptyState
import com.warehouse.inventory.ui.common.Pill
import com.warehouse.inventory.ui.common.ProductImage
import com.warehouse.inventory.ui.theme.WarningRed
import com.warehouse.inventory.util.ProductUnitLabels
import java.util.Locale

/**
 * Searchable stock list, filterable by category, warehouse and low-stock.
 *
 * Available to viewers as well as admins — reading stock is the one thing everyone needs —
 * so the purchase-cost line is gated on the session role reported by the ViewModel, not on a
 * parameter the caller could get wrong.
 */
@Composable
fun InventoryScreen(
    onBack: () -> Unit,
    onProductClick: (Long) -> Unit,
    viewModel: InventoryViewModel = hiltViewModel()
) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val warehouses by viewModel.warehouses.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val showFinancials by viewModel.showFinancials.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.inventory),
                subtitle = stringResource(
                    R.string.items_count_value,
                    products.size.toString()
                ),
                onBack = onBack
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = filter.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Category row.
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = filter.categoryId == null,
                        onClick = { viewModel.onCategorySelected(null) },
                        label = { Text(stringResource(R.string.all_categories)) }
                    )
                }
                items(categories, key = { it.id }) { category ->
                    FilterChip(
                        selected = filter.categoryId == category.id,
                        onClick = { viewModel.onCategorySelected(category.id) },
                        label = { Text(category.name) }
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Warehouse row plus the low-stock toggle. Only worth showing the warehouse
            // filter once there is more than one warehouse to choose between.
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = filter.lowStockOnly,
                        onClick = { viewModel.onLowStockOnlyChange(!filter.lowStockOnly) },
                        label = { Text(stringResource(R.string.filter_low_stock_only)) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
                if (warehouses.size > 1) {
                    item {
                        FilterChip(
                            selected = filter.warehouseId == null,
                            onClick = { viewModel.onWarehouseSelected(null) },
                            label = { Text(stringResource(R.string.all_warehouses)) }
                        )
                    }
                    items(warehouses, key = { it.id }) { warehouse ->
                        FilterChip(
                            selected = filter.warehouseId == warehouse.id,
                            onClick = { viewModel.onWarehouseSelected(warehouse.id) },
                            label = { Text(warehouse.name) }
                        )
                    }
                }
            }

            if (products.isEmpty()) {
                EmptyState(
                    // An empty catalogue and a filter that matched nothing are different
                    // problems and need different wording.
                    message = stringResource(
                        if (filter == InventoryFilter()) R.string.no_products
                        else R.string.no_results
                    )
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(products, key = { it.product.id }) { item ->
                        InventoryCard(
                            item = item,
                            showFinancials = showFinancials,
                            onClick = { onProductClick(item.product.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryCard(
    item: ProductWithCategory,
    showFinancials: Boolean,
    onClick: () -> Unit
) {
    val product = item.product
    val isLow = product.quantity <= product.lowStockThreshold

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column {
            ProductImage(
                imagePath = product.imagePath,
                modifier = Modifier.fillMaxWidth().height(150.dp)
            )
            Column(Modifier.padding(12.dp)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.categoryName ?: stringResource(R.string.no_category),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Identification line: SKU and barcode are what a storekeeper matches
                // against a physical label, so they belong on the card, not one tap away.
                val identifiers = listOfNotNull(
                    product.sku?.takeIf { it.isNotBlank() },
                    product.barcode?.takeIf { it.isNotBlank() }
                )
                if (identifiers.isNotEmpty()) {
                    Text(
                        text = identifiers.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(
                        text = stringResource(
                            R.string.quantity_with_unit,
                            product.quantity.toString(),
                            stringResource(ProductUnitLabels.stringRes(product.unit))
                        ),
                        container = if (isLow) WarningRed.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.primaryContainer,
                        content = if (isLow) WarningRed
                        else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (isLow) {
                        Spacer(Modifier.size(8.dp))
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = stringResource(R.string.low_stock),
                            tint = WarningRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (showFinancials) {
                        product.purchaseCost?.let { cost ->
                            Text(
                                text = String.format(Locale.US, "%,.2f", cost),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
