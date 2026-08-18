package com.warehouse.inventory.ui.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warehouse.inventory.R
import com.warehouse.inventory.ui.common.AppTopBar
import com.warehouse.inventory.ui.common.DateField
import com.warehouse.inventory.ui.common.EmptyState
import com.warehouse.inventory.ui.common.SectionHeader

/**
 * Warehouse transfer form. Admin-only, enforced in the repository.
 *
 * Needs two warehouses to be useful, so it says so plainly rather than presenting a form
 * that cannot be submitted.
 */
@Composable
fun TransferScreen(
    preselectedProductId: Long?,
    onBack: () -> Unit,
    onScanRequest: () -> Unit,
    viewModel: TransferViewModel = hiltViewModel()
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
                title = stringResource(R.string.stock_transfer),
                subtitle = stringResource(R.string.app_name),
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        if (warehouses.size < 2) {
            EmptyState(
                message = stringResource(R.string.error_need_two_warehouses),
                hint = stringResource(R.string.add_warehouse),
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
            SectionHeader(stringResource(R.string.section_stock))

            ProductSelector(
                products = products,
                selectedId = state.productId,
                onSelected = viewModel::onProductSelected,
                onScanClick = onScanRequest,
                isError = state.productError
            )

            WarehouseSelector(
                label = stringResource(R.string.from_warehouse),
                warehouses = warehouses,
                selectedId = state.fromWarehouseId,
                onSelected = viewModel::onSourceSelected,
                isError = state.warehouseError
            )

            WarehouseSelector(
                label = stringResource(R.string.to_warehouse),
                // The source is filtered out, so an invalid pair cannot be selected at all.
                warehouses = warehouses.filter { it.id != state.fromWarehouseId },
                selectedId = state.toWarehouseId,
                onSelected = viewModel::onDestinationSelected,
                isError = state.warehouseError
            )

            QuantityField(
                label = stringResource(R.string.quantity_transferred),
                value = state.quantity,
                onValueChange = viewModel::onQuantityChange,
                isError = state.quantityError || state.exceedsAvailable,
                supportingText = if (state.exceedsAvailable) {
                    stringResource(R.string.error_insufficient_stock)
                } else {
                    null
                }
            )

            if (state.productId != null) {
                // Both sides shown: the point of a transfer is the pair of balances.
                Text(
                    stringResource(R.string.from_warehouse),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                BalancePreview(
                    available = state.sourceAvailable,
                    resulting = state.sourceResulting?.takeIf { it >= 0 },
                    unit = state.unit
                )
                Text(
                    stringResource(R.string.to_warehouse),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                BalancePreview(
                    available = state.destinationAvailable,
                    resulting = state.destinationResulting,
                    unit = state.unit
                )
            }

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
                enabled = !state.submitting && !state.sameWarehouse,
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
                        stringResource(R.string.confirm_transfer),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}
