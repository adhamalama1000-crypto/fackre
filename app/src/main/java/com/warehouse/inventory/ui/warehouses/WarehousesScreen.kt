package com.warehouse.inventory.ui.warehouses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warehouse.inventory.R
import com.warehouse.inventory.data.local.entity.WarehouseEntity
import com.warehouse.inventory.ui.common.AppTopBar
import com.warehouse.inventory.ui.common.ConfirmDialog
import com.warehouse.inventory.ui.common.EmptyState
import com.warehouse.inventory.ui.common.FieldError
import com.warehouse.inventory.ui.common.Pill

@Composable
fun WarehousesScreen(
    isAdmin: Boolean,
    onBack: () -> Unit,
    viewModel: WarehousesViewModel = hiltViewModel()
) {
    val warehouses by viewModel.warehouses.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WarehouseEntity?>(null) }
    var toDelete by remember { mutableStateOf<WarehouseEntity?>(null) }

    val snackbarHost = remember { SnackbarHostState() }
    val message = state.message?.let { stringResource(it) }
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHost.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            showForm = false
            editing = null
            viewModel.consumeSaved()
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = stringResource(R.string.warehouses), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            if (isAdmin) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editing = null
                        showForm = true
                    },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_warehouse)) }
                )
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                label = { Text(stringResource(R.string.warehouse_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (warehouses.isEmpty()) {
                EmptyState(
                    message = stringResource(
                        if (query.isBlank()) R.string.no_warehouses else R.string.no_results
                    ),
                    icon = Icons.Filled.Store
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(warehouses, key = { it.warehouse.id }) { row ->
                        WarehouseCard(
                            row = row,
                            isAdmin = isAdmin,
                            onEdit = {
                                editing = row.warehouse
                                showForm = true
                            },
                            onSetDefault = { viewModel.setDefault(row.warehouse.id) },
                            onDelete = { toDelete = row.warehouse }
                        )
                    }
                }
            }
        }
    }

    if (showForm) {
        WarehouseFormDialog(
            existing = editing,
            errorText = state.formError?.let { stringResource(it) },
            submitting = state.submitting,
            onFieldEdited = viewModel::clearFormError,
            onDismiss = {
                showForm = false
                editing = null
                viewModel.clearFormError()
            },
            onSave = { name, code, location, notes, makeDefault ->
                viewModel.save(editing?.id, name, code, location, notes, makeDefault)
            }
        )
    }

    toDelete?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.delete),
            message = stringResource(R.string.confirm_delete_warehouse),
            confirmLabel = stringResource(R.string.delete),
            destructive = true,
            onConfirm = {
                viewModel.delete(target)
                toDelete = null
            },
            onDismiss = { toDelete = null }
        )
    }
}

@Composable
private fun WarehouseCard(
    row: WarehouseRow,
    isAdmin: Boolean,
    onEdit: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit
) {
    val warehouse = row.warehouse
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Store,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    warehouse.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    warehouse.code,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                if (warehouse.location.isNotBlank()) {
                    Text(
                        warehouse.location,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "${stringResource(R.string.items_count)}: ${row.productCount}" +
                        "  •  ${stringResource(R.string.total_quantity)}: ${row.totalQuantity}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (warehouse.isDefault) {
                    Pill(
                        text = stringResource(R.string.default_warehouse),
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            if (isAdmin) {
                // Promoting the already-default warehouse would be a no-op, so the action
                // only appears where it means something.
                if (!warehouse.isDefault) {
                    IconButton(onClick = onSetDefault) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = stringResource(R.string.set_as_default),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit))
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun WarehouseFormDialog(
    existing: WarehouseEntity?,
    errorText: String?,
    submitting: Boolean,
    onFieldEdited: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Boolean) -> Unit
) {
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var code by remember(existing) { mutableStateOf(existing?.code ?: "") }
    var location by remember(existing) { mutableStateOf(existing?.location ?: "") }
    var notes by remember(existing) { mutableStateOf(existing?.notes ?: "") }
    var makeDefault by remember(existing) { mutableStateOf(existing?.isDefault ?: false) }

    // Exactly one warehouse is default, so the flag can be granted but never revoked here —
    // clearing it would leave stock forms with nothing preselected.
    val alreadyDefault = existing?.isDefault == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (existing == null) R.string.add_warehouse else R.string.edit_warehouse
                )
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        onFieldEdited()
                    },
                    label = { Text(stringResource(R.string.warehouse_name)) },
                    isError = errorText != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it
                        onFieldEdited()
                    },
                    label = { Text(stringResource(R.string.warehouse_code)) },
                    isError = errorText != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FieldError(errorText)
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(stringResource(R.string.location)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.notes)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.set_as_default),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = makeDefault,
                        onCheckedChange = { makeDefault = it },
                        enabled = !alreadyDefault
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, code, location, notes, makeDefault) },
                enabled = name.isNotBlank() && code.isNotBlank() && !submitting
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
