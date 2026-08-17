package com.warehouse.inventory.ui.stockout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warehouse.inventory.R
import com.warehouse.inventory.ui.common.AppTopBar
import com.warehouse.inventory.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockOutScreen(
    preselectedProductId: Long?,
    onBack: () -> Unit,
    viewModel: StockOutViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(preselectedProductId) { viewModel.preselect(preselectedProductId) }

    val successMsg = stringResource(R.string.stock_out_success)
    LaunchedEffect(state.success) {
        if (state.success) {
            snackbarHost.showSnackbar(successMsg)
            viewModel.consumeSuccess()
        }
    }

    var productExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val selectedProduct = products.firstOrNull { it.product.id == state.selectedProductId }
    val available = viewModel.availableFor(state.selectedProductId, products)

    Scaffold(
        topBar = { AppTopBar(title = stringResource(R.string.stock_out), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Product picker
            ExposedDropdownMenuBox(
                expanded = productExpanded,
                onExpandedChange = { productExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedProduct?.product?.name ?: stringResource(R.string.select_product),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.select_product)) },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = productExpanded,
                    onDismissRequest = { productExpanded = false }
                ) {
                    products.forEach { item ->
                        DropdownMenuItem(
                            text = { Text("${item.product.name}  (${item.product.quantity})") },
                            onClick = {
                                viewModel.onProductSelected(item.product.id)
                                productExpanded = false
                            }
                        )
                    }
                }
            }

            if (state.selectedProductId != null) {
                Text(
                    "${stringResource(R.string.available)}: $available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            OutlinedTextField(
                value = state.quantity,
                onValueChange = viewModel::onQuantity,
                label = { Text(stringResource(R.string.quantity_removed)) },
                singleLine = true,
                isError = state.error == StockOutError.INVALID_QUANTITY || state.error == StockOutError.INSUFFICIENT,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.employeeName,
                onValueChange = viewModel::onEmployee,
                label = { Text(stringResource(R.string.employee_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = DateUtils.formatDate(state.date),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.date)) },
                trailingIcon = {
                    TextButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotes,
                label = { Text(stringResource(R.string.notes)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            val errorText = when (state.error) {
                StockOutError.INSUFFICIENT -> stringResource(R.string.error_insufficient_stock)
                StockOutError.INVALID_QUANTITY -> stringResource(R.string.error_invalid_quantity)
                StockOutError.PRODUCT_NOT_FOUND -> stringResource(R.string.select_product)
                StockOutError.NONE -> null
            }
            if (errorText != null) {
                Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = viewModel::submit,
                enabled = !state.submitting,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(stringResource(R.string.confirm_stock_out), style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = state.date)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { viewModel.onDate(it) }
                    showDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
