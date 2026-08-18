package com.warehouse.inventory.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warehouse.inventory.R
import com.warehouse.inventory.data.local.entity.EmployeeEntity
import com.warehouse.inventory.data.local.entity.InventoryTransactionEntity
import com.warehouse.inventory.data.local.entity.ProductWithCategory
import com.warehouse.inventory.data.local.entity.TransactionType
import com.warehouse.inventory.data.local.entity.WarehouseEntity
import com.warehouse.inventory.data.repository.HistoryFilter
import com.warehouse.inventory.ui.common.AppTopBar
import com.warehouse.inventory.ui.common.DateField
import com.warehouse.inventory.ui.common.DetailRow
import com.warehouse.inventory.ui.common.EmptyState
import com.warehouse.inventory.ui.common.LabeledDropdown
import com.warehouse.inventory.ui.common.Pill
import com.warehouse.inventory.ui.common.SectionHeader
import com.warehouse.inventory.ui.common.ThinDivider
import com.warehouse.inventory.ui.theme.TransactionColors
import com.warehouse.inventory.util.DateUtils
import com.warehouse.inventory.util.AdjustmentReasonLabels
import com.warehouse.inventory.util.ProductUnitLabels
import com.warehouse.inventory.util.TransactionTypeLabels

/**
 * The unified inventory history: stock-ins, stock-outs, adjustments and both legs of every
 * transfer, newest first.
 *
 * Every control edits one [HistoryFilter] in the ViewModel rather than filtering the list
 * in Compose, so the narrowing happens in SQL and the screen stays a pure projection.
 */
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val employees by viewModel.employees.collectAsStateWithLifecycle()
    val warehouses by viewModel.warehouses.collectAsStateWithLifecycle()

    // Survives rotation so a half-built filter isn't hidden from under the user.
    var showFilters by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.history),
                onBack = onBack,
                actions = {
                    if (filter.isFiltered) {
                        IconButton(onClick = viewModel::clearFilters) {
                            Icon(
                                Icons.Filled.FilterAltOff,
                                contentDescription = stringResource(R.string.clear_filters)
                            )
                        }
                    }
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(
                            Icons.Filled.FilterAlt,
                            contentDescription = stringResource(R.string.filters)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            OutlinedTextField(
                value = filter.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text(stringResource(R.string.history_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (filter.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.close)
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            TypeChips(
                types = filter.types,
                onToggleType = viewModel::toggleType,
                onSelectAll = viewModel::selectAllTypes
            )

            if (showFilters) {
                FilterPanel(
                    filter = filter,
                    products = products,
                    employees = employees,
                    warehouses = warehouses,
                    onProductSelected = viewModel::onProductSelected,
                    onEmployeeSelected = viewModel::onEmployeeSelected,
                    onWarehouseSelected = viewModel::onWarehouseSelected,
                    onDateFrom = viewModel::onDateFrom,
                    onDateTo = viewModel::onDateTo,
                    onClearFilters = viewModel::clearFilters
                )
            }

            ThinDivider(Modifier.padding(top = 8.dp))

            if (history.isEmpty()) {
                EmptyState(
                    // "No results" is a filtering outcome; "no history" is an empty ledger.
                    message = stringResource(
                        if (filter.isFiltered) R.string.no_results else R.string.no_history
                    ),
                    icon = Icons.Filled.History
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(history, key = { it.id }) { entry -> HistoryCard(entry) }
                }
            }
        }
    }
}

/**
 * Transaction-type multi-select. The "all types" chip and the per-type chips are mutually
 * exclusive as a display: when every type is selected the row reads as "all types" rather
 * than lighting up all five, which would be indistinguishable from an actual five-way
 * selection.
 */
@Composable
private fun TypeChips(
    types: Set<TransactionType>,
    onToggleType: (TransactionType) -> Unit,
    onSelectAll: () -> Unit
) {
    val allSelected = types.size == TransactionType.entries.size

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = allSelected,
                onClick = onSelectAll,
                label = { Text(stringResource(R.string.all_types)) }
            )
        }
        items(TransactionType.entries, key = { it.name }) { type ->
            FilterChip(
                selected = !allSelected && type in types,
                onClick = { onToggleType(type) },
                label = { Text(stringResource(TransactionTypeLabels.stringRes(type))) }
            )
        }
    }
}

@Composable
private fun FilterPanel(
    filter: HistoryFilter,
    products: List<ProductWithCategory>,
    employees: List<EmployeeEntity>,
    warehouses: List<WarehouseEntity>,
    onProductSelected: (Long?) -> Unit,
    onEmployeeSelected: (Long?) -> Unit,
    onWarehouseSelected: (Long?) -> Unit,
    onDateFrom: (Long?) -> Unit,
    onDateTo: (Long?) -> Unit,
    onClearFilters: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeader(stringResource(R.string.filters))

            ClearableField(active = filter.productId != null, onClear = { onProductSelected(null) }) {
                LabeledDropdown(
                    fieldLabel = stringResource(R.string.product_name),
                    selected = products.firstOrNull { it.product.id == filter.productId },
                    items = products,
                    label = { it.product.name },
                    secondaryLabel = { it.product.sku.orEmpty() },
                    onSelected = { onProductSelected(it.product.id) },
                    placeholder = stringResource(R.string.select_product),
                    modifier = Modifier.weight(1f)
                )
            }

            ClearableField(active = filter.employeeId != null, onClear = { onEmployeeSelected(null) }) {
                LabeledDropdown(
                    fieldLabel = stringResource(R.string.employee_name),
                    selected = employees.firstOrNull { it.id == filter.employeeId },
                    items = employees,
                    label = { it.name },
                    secondaryLabel = { it.department },
                    onSelected = { onEmployeeSelected(it.id) },
                    placeholder = stringResource(R.string.select_employee),
                    modifier = Modifier.weight(1f)
                )
            }

            ClearableField(active = filter.warehouseId != null, onClear = { onWarehouseSelected(null) }) {
                LabeledDropdown(
                    fieldLabel = stringResource(R.string.warehouse),
                    selected = warehouses.firstOrNull { it.id == filter.warehouseId },
                    items = warehouses,
                    label = { it.name },
                    secondaryLabel = { it.code },
                    onSelected = { onWarehouseSelected(it.id) },
                    placeholder = stringResource(R.string.select_warehouse),
                    modifier = Modifier.weight(1f)
                )
            }

            SectionHeader(stringResource(R.string.date_range))

            // An unset bound has no date to show, so the picker simply opens on today; the
            // clear button appearing is what tells the user the bound is actually applied.
            ClearableField(active = filter.from != 0L, onClear = { onDateFrom(null) }) {
                DateField(
                    label = stringResource(R.string.date_from),
                    date = filter.from.takeIf { it != 0L } ?: System.currentTimeMillis(),
                    onDateChange = onDateFrom,
                    modifier = Modifier.weight(1f)
                )
            }

            ClearableField(active = filter.to != Long.MAX_VALUE, onClear = { onDateTo(null) }) {
                DateField(
                    label = stringResource(R.string.date_to),
                    date = filter.to.takeIf { it != Long.MAX_VALUE } ?: System.currentTimeMillis(),
                    onDateChange = onDateTo,
                    modifier = Modifier.weight(1f)
                )
            }

            if (filter.isFiltered) {
                TextButton(onClick = onClearFilters) {
                    Text(stringResource(R.string.clear_filters))
                }
            }
        }
    }
}

/**
 * A filter input plus a reset affordance that only exists while the input is [active].
 * Keeps every filter row the same shape, and lets one bound be dropped without wiping the
 * others.
 */
@Composable
private fun ClearableField(
    active: Boolean,
    onClear: () -> Unit,
    field: @Composable (RowScope.() -> Unit)
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        field()
        if (active) {
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.clear_filters)
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(entry: InventoryTransactionEntity) {
    val accent = TransactionColors.forType(entry.type)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Pill(
                    text = stringResource(TransactionTypeLabels.stringRes(entry.type)),
                    container = accent.copy(alpha = 0.14f),
                    content = accent
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = signedQuantity(entry),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                entry.productName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!entry.productSku.isNullOrBlank()) {
                Text(
                    entry.productSku,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(6.dp))

            DetailRow(stringResource(R.string.date), DateUtils.formatDate(entry.date))
            if (entry.warehouseName.isNotBlank()) {
                DetailRow(stringResource(R.string.warehouse), entry.warehouseName)
            }

            // The other leg of a transfer: a TRANSFER_OUT names where the stock went, a
            // TRANSFER_IN where it came from.
            if (entry.counterpartWarehouseName.isNotBlank()) {
                val counterpartLabel = when (entry.type) {
                    TransactionType.TRANSFER_OUT -> R.string.to_warehouse
                    else -> R.string.from_warehouse
                }
                DetailRow(stringResource(counterpartLabel), entry.counterpartWarehouseName)
            }

            // Rendered even when null: DetailRow shows the "—" placeholder, which is the
            // honest answer for rows migrated from schema v1, where the balances were
            // never recorded.
            DetailRow(stringResource(R.string.previous_stock), entry.previousQuantity?.toString())
            DetailRow(stringResource(R.string.new_stock), entry.newQuantity?.toString())

            if (entry.employeeName.isNotBlank()) {
                DetailRow(stringResource(R.string.employee_name), entry.employeeName)
            }
            if (entry.type == TransactionType.STOCK_IN && entry.supplierName.isNotBlank()) {
                DetailRow(stringResource(R.string.supplier), entry.supplierName)
            }
            if (entry.type == TransactionType.STOCK_IN && entry.invoiceNumber.isNotBlank()) {
                DetailRow(stringResource(R.string.invoice_number), entry.invoiceNumber)
            }
            entry.reason?.let { reason ->
                DetailRow(
                    stringResource(R.string.adjustment_reason),
                    stringResource(AdjustmentReasonLabels.stringRes(reason))
                )
            }
            if (entry.userName.isNotBlank()) {
                DetailRow(stringResource(R.string.recorded_by), entry.userName)
            }
            if (entry.notes.isNotBlank()) {
                DetailRow(stringResource(R.string.notes), entry.notes)
            }
        }
    }
}

/**
 * Movement with its direction and unit, e.g. "+12 قطعة".
 *
 * Prefers the stored `difference` — it is the signed truth including an adjustment that
 * lowered stock — and falls back to the type's direction for v1-migrated rows that carry
 * no difference. An adjustment without a difference has no known direction, so it is shown
 * unsigned rather than guessed.
 */
@Composable
private fun signedQuantity(entry: InventoryTransactionEntity): String {
    val signed = entry.difference ?: when (entry.type) {
        TransactionType.STOCK_IN, TransactionType.TRANSFER_IN -> entry.quantity
        TransactionType.STOCK_OUT, TransactionType.TRANSFER_OUT -> -entry.quantity
        TransactionType.ADJUSTMENT -> null
    }
    val amount = when {
        signed == null -> entry.quantity.toString()
        signed > 0 -> "+$signed"
        else -> signed.toString()
    }
    return stringResource(
        R.string.quantity_with_unit,
        amount,
        stringResource(ProductUnitLabels.stringRes(entry.unit))
    )
}
