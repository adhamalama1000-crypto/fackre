package com.warehouse.inventory.ui.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.warehouse.inventory.R
import com.warehouse.inventory.data.local.entity.EmployeeEntity
import com.warehouse.inventory.data.local.entity.ProductUnit
import com.warehouse.inventory.data.local.entity.ProductWithCategory
import com.warehouse.inventory.data.local.entity.WarehouseEntity
import com.warehouse.inventory.ui.common.LabeledDropdown
import com.warehouse.inventory.util.ProductUnitLabels

/**
 * Form controls shared by the four stock operations (receive, issue, adjust, transfer).
 *
 * The operations differ in their rules, not their inputs — all four pick a product, a
 * warehouse, an employee and a date — so the widgets live here and each screen composes
 * them. That is what keeps the four screens visually identical, which matters when warehouse
 * staff switch between them all day.
 */

/** Product picker with a scan shortcut beside it. */
@Composable
fun ProductSelector(
    products: List<ProductWithCategory>,
    selectedId: Long?,
    onSelected: (Long) -> Unit,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    val selected = products.firstOrNull { it.product.id == selectedId }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LabeledDropdown(
            fieldLabel = stringResource(R.string.select_product),
            selected = selected,
            items = products,
            label = { it.product.name },
            // Second line carries the SKU and current total, so the right item can be
            // picked when several products have similar names.
            secondaryLabel = { item ->
                listOfNotNull(
                    item.product.sku?.takeIf { it.isNotBlank() },
                    "${item.product.quantity}"
                ).joinToString(" • ")
            },
            onSelected = { onSelected(it.product.id) },
            placeholder = stringResource(R.string.select_product),
            isError = isError,
            modifier = Modifier.weight(1f)
        )
        FilledTonalIconButton(onClick = onScanClick, modifier = Modifier.size(56.dp)) {
            Icon(
                Icons.Filled.QrCodeScanner,
                contentDescription = stringResource(R.string.scan_barcode)
            )
        }
    }
}

@Composable
fun WarehouseSelector(
    label: String,
    warehouses: List<WarehouseEntity>,
    selectedId: Long?,
    onSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    LabeledDropdown(
        fieldLabel = label,
        selected = warehouses.firstOrNull { it.id == selectedId },
        items = warehouses,
        label = { it.name },
        secondaryLabel = { it.code },
        onSelected = { onSelected(it.id) },
        placeholder = stringResource(R.string.select_warehouse),
        isError = isError,
        modifier = modifier
    )
}

/**
 * Employee picker. Optional on every operation — the person handling the goods is often not
 * recorded for internal moves — so there is no error state and no required marker.
 */
@Composable
fun EmployeeSelector(
    employees: List<EmployeeEntity>,
    selectedId: Long?,
    onSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LabeledDropdown(
        fieldLabel = stringResource(R.string.employee_name),
        selected = employees.firstOrNull { it.id == selectedId },
        items = employees,
        label = { it.name },
        secondaryLabel = { employee ->
            listOfNotNull(
                employee.code?.takeIf { it.isNotBlank() },
                employee.department.takeIf { it.isNotBlank() }
            ).joinToString(" • ")
        },
        onSelected = { onSelected(it.id) },
        placeholder = stringResource(R.string.select_employee),
        modifier = modifier
    )
}

/** Digits-only quantity input. Filtering in the field keeps parse errors out of the VM. */
@Composable
fun QuantityField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Next
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter(Char::isDigit)) },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = imeAction
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun NotesField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.notes)) },
        minLines = 2,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Shows what the operation will do to the balance before it is committed: current level in
 * the selected warehouse, and the resulting level.
 *
 * Deliberately prominent. Stock mistakes are expensive and hard to unwind, so the numbers a
 * confirm button is about to write are stated plainly first.
 */
@Composable
fun BalancePreview(
    available: Int,
    resulting: Int?,
    unit: ProductUnit,
    modifier: Modifier = Modifier
) {
    val unitLabel = stringResource(ProductUnitLabels.stringRes(unit))
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    stringResource(R.string.available),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    stringResource(R.string.quantity_with_unit, available.toString(), unitLabel),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            if (resulting != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        stringResource(R.string.new_balance_preview),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        stringResource(
                            R.string.quantity_with_unit, resulting.toString(), unitLabel
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (resulting < 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}
