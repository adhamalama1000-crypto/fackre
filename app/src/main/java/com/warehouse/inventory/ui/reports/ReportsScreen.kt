package com.warehouse.inventory.ui.reports

import android.content.Context
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warehouse.inventory.R
import com.warehouse.inventory.ui.common.AppTopBar
import com.warehouse.inventory.ui.common.EmptyState
import com.warehouse.inventory.ui.common.LabeledDropdown
import com.warehouse.inventory.ui.common.Pill
import com.warehouse.inventory.ui.common.SectionHeader
import com.warehouse.inventory.ui.common.ThinDivider
import com.warehouse.inventory.util.DateUtils

/**
 * Report picker, filters, on-screen preview and CSV / PDF export.
 *
 * [isAdmin] decides whether purchase cost and inventory value appear at all. It is passed to
 * the ViewModel rather than only used to hide widgets here, because the ViewModel re-checks
 * the role against the database before it will put those figures into a table or a file.
 */
@Composable
fun ReportsScreen(
    isAdmin: Boolean,
    onBack: () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val warehouses by viewModel.warehouses.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val employees by viewModel.employees.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(isAdmin) {
        viewModel.setAdmin(isAdmin)
        viewModel.generate()
    }

    // Headers and cells are resolved through the Context rather than `stringResource` so the
    // same strings can be handed to the export, which runs outside composition.
    val headers = state.table.columns.map { context.getString(it.headerRes) }
    val rows = state.table.rows.map { row -> row.map { it.resolve(context) } }

    val reportTitle = stringResource(state.type.labelRes)
    val periodLabel = stringResource(R.string.report_period)
    val periodValue = stringResource(state.period.labelRes)
    val generatedAtLabel = stringResource(R.string.report_generated_at)
    val rowsCountLabel = stringResource(R.string.rows_count)
    val footer = stringResource(R.string.app_full_name)
    val usesPeriod = state.type.usesPeriod

    /** Snapshots the report exactly as it is on screen, stamped with the current time. */
    fun buildDocument(): ReportDocument {
        val subtitle = buildList {
            if (usesPeriod) add("$periodLabel: $periodValue")
            add("$generatedAtLabel: ${DateUtils.formatDateTime(System.currentTimeMillis())}")
        }.joinToString(" • ")
        return ReportDocument(
            fileStem = state.type.fileStem,
            title = reportTitle,
            subtitle = subtitle,
            columnHeaders = headers,
            weights = state.table.columns.map { it.weight },
            rows = rows,
            summaryLines = listOf("$rowsCountLabel: ${state.rowCount}"),
            footer = footer
        )
    }

    // One-shot state consumed here: the message becomes a snackbar, the finished file becomes
    // a share sheet. Both are cleared so a recomposition cannot replay them.
    state.message?.let { messageRes ->
        val text = stringResource(messageRes)
        LaunchedEffect(messageRes, text) {
            snackbarHost.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }
    val shareSubject = stringResource(R.string.share_file)
    state.pendingShare?.let { export ->
        LaunchedEffect(export.uri) {
            context.startActivity(viewModel.shareIntent(export, shareSubject))
            viewModel.consumeShare()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.reports),
                subtitle = reportTitle,
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionHeader(stringResource(R.string.report_options))
                ReportTypeList(
                    selected = state.type,
                    onSelected = viewModel::onTypeSelected
                )
            }

            item {
                SectionHeader(stringResource(R.string.filters))
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (usesPeriod) {
                        PeriodChips(
                            selected = state.period,
                            onSelected = viewModel::onPeriodSelected
                        )
                    }
                    if (state.type.usesWarehouse) {
                        OptionDropdown(
                            fieldLabel = stringResource(R.string.warehouse),
                            anyLabel = stringResource(R.string.all_warehouses),
                            includeAnyItem = true,
                            selectedId = state.warehouseId,
                            options = warehouses.map { FilterOption(it.id, it.name, it.code) },
                            onSelected = viewModel::onWarehouseSelected
                        )
                    }
                    if (state.type.usesCategory) {
                        OptionDropdown(
                            fieldLabel = stringResource(R.string.category),
                            anyLabel = stringResource(R.string.all_categories),
                            includeAnyItem = true,
                            selectedId = state.categoryId,
                            options = categories.map { FilterOption(it.id, it.name) },
                            onSelected = viewModel::onCategorySelected
                        )
                    }
                    if (state.type.usesProduct) {
                        // No "all products" entry: an unselected picker already means
                        // "every product", and the clear button below undoes a choice.
                        OptionDropdown(
                            fieldLabel = stringResource(R.string.product_name),
                            anyLabel = stringResource(R.string.select_product),
                            includeAnyItem = false,
                            selectedId = state.productId,
                            options = products.map {
                                FilterOption(it.product.id, it.product.name, it.product.sku.orEmpty())
                            },
                            onSelected = viewModel::onProductSelected
                        )
                    }
                    if (state.type.usesEmployee) {
                        OptionDropdown(
                            fieldLabel = stringResource(R.string.employee_name),
                            anyLabel = stringResource(R.string.select_employee),
                            includeAnyItem = false,
                            selectedId = state.employeeId,
                            options = employees.map {
                                FilterOption(it.id, it.name, it.department)
                            },
                            onSelected = viewModel::onEmployeeSelected
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.rows_count),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Pill(
                        text = state.rowCount.toString(),
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    if (state.generating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        viewModel.onWarehouseSelected(null)
                        viewModel.onCategorySelected(null)
                        viewModel.onProductSelected(null)
                        viewModel.onEmployeeSelected(null)
                    }) { Text(stringResource(R.string.clear_filters)) }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.export(ExportFormat.CSV, buildDocument()) },
                        enabled = !state.exporting && !state.generating,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Filled.TableChart,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.export_csv))
                    }
                    OutlinedButton(
                        onClick = { viewModel.export(ExportFormat.PDF, buildDocument()) },
                        enabled = !state.exporting && !state.generating,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Filled.Description,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.export_pdf))
                    }
                }
                if (state.exporting) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.exporting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = viewModel::generate,
                    enabled = !state.generating,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.generate_report)) }
            }

            if (rows.isEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = stringResource(
                            if (state.generating) R.string.loading else R.string.no_results
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                // The preview is capped: a full inventory export runs to thousands of rows,
                // and the file — not the screen — is where those are meant to be read.
                items(rows.take(PREVIEW_ROW_LIMIT)) { row ->
                    ReportRowCard(headers = headers, cells = row)
                }
                if (rows.size > PREVIEW_ROW_LIMIT) {
                    item {
                        Text(
                            text = "$rowsCountLabel: ${rows.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Dropdown entry. [id] is null for the "everything" row. */
private data class FilterOption(
    val id: Long?,
    val label: String,
    val detail: String = ""
)

@Composable
private fun OptionDropdown(
    fieldLabel: String,
    anyLabel: String,
    includeAnyItem: Boolean,
    selectedId: Long?,
    options: List<FilterOption>,
    onSelected: (Long?) -> Unit
) {
    val items = if (includeAnyItem) listOf(FilterOption(null, anyLabel)) + options else options
    LabeledDropdown(
        fieldLabel = fieldLabel,
        selected = items.firstOrNull { it.id == selectedId },
        items = items,
        label = { it.label },
        secondaryLabel = { it.detail },
        onSelected = { onSelected(it.id) },
        placeholder = anyLabel
    )
}

@Composable
private fun ReportTypeList(
    selected: ReportType,
    onSelected: (ReportType) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Column {
            ReportType.entries.forEachIndexed { index, type ->
                if (index > 0) ThinDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = type == selected, onClick = { onSelected(type) })
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = type == selected,
                        onClick = { onSelected(type) }
                    )
                    Text(
                        text = stringResource(type.labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                        // Start padding, never left: in Arabic the label sits to the left
                        // of the radio and this gap has to follow the direction.
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodChips(
    selected: ReportPeriod,
    onSelected: (ReportPeriod) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReportPeriod.entries.forEach { period ->
            FilterChip(
                selected = period == selected,
                onClick = { onSelected(period) },
                label = { Text(stringResource(period.labelRes)) }
            )
        }
    }
}

/**
 * One report row.
 *
 * Rendered as a labelled card rather than a grid row on purpose: the column count changes
 * with the selected report, Arabic product names and notes wrap to several lines, and a
 * horizontally scrolled table would put the numbers out of reach on a phone. Each value
 * carries its own header, so nothing depends on remembering the column order.
 */
@Composable
private fun ReportRowCard(headers: List<String>, cells: List<String>) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            // The first column is the row's identity — product, date or period — so it reads
            // as the card's heading rather than as another label/value pair.
            cells.firstOrNull()?.let { headline ->
                Text(
                    text = headline.ifBlank { stringResource(R.string.unknown_value) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
            }
            headers.drop(1).forEachIndexed { index, header ->
                val value = cells.getOrNull(index + 1)
                if (!value.isNullOrBlank()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(
                            text = header,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/** Empty-report placeholder, kept out of the scrolling body. */
@Composable
private fun NoReportRows() {
    EmptyState(
        message = stringResource(R.string.no_results),
        icon = Icons.Outlined.Assessment
    )
}

private fun ReportCell.resolve(context: Context): String = when (this) {
    is ReportCell.Text -> value
    is ReportCell.Label -> context.getString(res)
}

private const val PREVIEW_ROW_LIMIT = 30
