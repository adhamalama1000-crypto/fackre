package com.warehouse.inventory.ui.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warehouse.inventory.R
import com.warehouse.inventory.ui.common.AppTopBar
import com.warehouse.inventory.ui.common.DateField
import com.warehouse.inventory.ui.common.EmptyState
import com.warehouse.inventory.ui.common.LabeledDropdown
import com.warehouse.inventory.ui.common.SectionHeader
import com.warehouse.inventory.util.AdjustmentReasonLabels
import com.warehouse.inventory.util.ProductUnitLabels

/**
 * Stock adjustment form: enter the counted quantity, see the difference, give a reason.
 * Admin-only, enforced in the repository.
 */
@Composable
fun AdjustmentScreen(
    preselectedProductId: Long?,
    onBack: () -> Unit,
    onScanRequest: () -> Unit,
    viewModel: AdjustmentViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val warehouses by viewModel.warehouses.collectAsStateWithLifecycle()
    val employees by viewModel.employees.collectAsStateWithLifecycle()
    val scannedCode by viewModel.scannedCode.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(preselectedProductId) { viewModel.preselectProduct(preselectedProductId) }
    LaunchedEffect(scannedCode) { scannedCode?.let(viewModel::onCodeScanned) }

    val message = state.message?.let { stringResource(it) }
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHost.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.stock_adjustment),
                subtitle = stringResource(R.string.app_name),
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        if (warehouses.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.no_warehouses),
                hint = stringResource(R.string.error_no_warehouse),
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                stringResource(R.string.adjustment_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionHeader(stringResource(R.string.section_stock))

            ProductSelector(
                products = products,
                selectedId = state.productId,
                onSelected = viewModel::onProductSelected,
                onScanClick = onScanRequest,
                isError = state.productError
            )

            WarehouseSelector(
                label = stringResource(R.string.warehouse),
                warehouses = warehouses,
                selectedId = state.warehouseId,
                onSelected = viewModel::onWarehouseSelected
            )

            QuantityField(
                label = stringResource(R.string.physical_quantity),
                value = state.physicalQuantity,
                onValueChange = viewModel::onPhysicalQuantityChange,
                isError = state.quantityError
            )

            if (state.productId != null) {
                AdjustmentSummary(
                    systemQuantity = state.systemQuantity,
                    difference = state.difference,
                    unitLabel = stringResource(ProductUnitLabels.stringRes(state.unit))
                )
            }

            LabeledDropdown(
                fieldLabel = stringResource(R.string.adjustment_reason),
                selected = state.reason,
                items = viewModel.reasons,
                label = { stringResource(AdjustmentReasonLabels.stringRes(it)) },
                onSelected = viewModel::onReasonSelected,
                placeholder = stringResource(R.string.select_reason),
                isError = state.reasonError
            )

            SectionHeader(stringResource(R.string.section_details))

            EmployeeSelector(
                employees = employees,
                selectedId = state.employeeId,
                onSelected = viewModel::onEmployeeSelected
            )

            DateField(
                label = stringResource(R.string.date),
                date = state.date,
                onDateChange = viewModel::onDateChange
            )

            NotesField(value = state.notes, onValueChange = viewModel::onNotesChange)

            Button(
                onClick = viewModel::submit,
                enabled = !state.submitting,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        stringResource(R.string.confirm_adjustment),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

/**
 * System quantity beside the signed correction. The difference is the number the user is
 * really deciding about, so it is the emphasised one and is colour-coded by direction.
 */
@Composable
private fun AdjustmentSummary(
    systemQuantity: Int,
    difference: Int?,
    unitLabel: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    stringResource(R.string.system_quantity),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    stringResource(
                        R.string.quantity_with_unit, systemQuantity.toString(), unitLabel
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(R.string.difference),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = when {
                        difference == null -> stringResource(R.string.unknown_value)
                        // Explicit "+" so a positive correction is unmistakable.
                        difference > 0 -> "+$difference"
                        else -> difference.toString()
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        difference == null || difference == 0 ->
                            MaterialTheme.colorScheme.onSecondaryContainer
                        difference < 0 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
    }
}
