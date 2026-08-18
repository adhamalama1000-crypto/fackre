package com.warehouse.inventory.ui.suppliers

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
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Search
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
import com.warehouse.inventory.data.local.entity.SupplierEntity
import com.warehouse.inventory.ui.common.AppTopBar
import com.warehouse.inventory.ui.common.ConfirmDialog
import com.warehouse.inventory.ui.common.EmptyState
import com.warehouse.inventory.ui.common.FieldError

@Composable
fun SuppliersScreen(
    isAdmin: Boolean,
    onBack: () -> Unit,
    viewModel: SuppliersViewModel = hiltViewModel()
) {
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // `showForm` is separate from `editing` because "add" is the same dialog with no target.
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SupplierEntity?>(null) }
    var toDelete by remember { mutableStateOf<SupplierEntity?>(null) }

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
        topBar = { AppTopBar(title = stringResource(R.string.suppliers), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            if (isAdmin) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editing = null
                        showForm = true
                    },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_supplier)) }
                )
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                label = { Text(stringResource(R.string.supplier_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (suppliers.isEmpty()) {
                EmptyState(
                    // An empty table and an empty result set are different problems.
                    message = stringResource(
                        if (query.isBlank()) R.string.no_suppliers else R.string.no_results
                    ),
                    icon = Icons.Filled.LocalShipping
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Extra bottom room so the FAB never sits on top of the last card.
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(suppliers, key = { it.id }) { supplier ->
                        SupplierCard(
                            supplier = supplier,
                            isAdmin = isAdmin,
                            onEdit = {
                                editing = supplier
                                showForm = true
                            },
                            onDelete = { toDelete = supplier }
                        )
                    }
                }
            }
        }
    }

    if (showForm) {
        SupplierFormDialog(
            existing = editing,
            errorText = state.formError?.let { stringResource(it) },
            submitting = state.submitting,
            onFieldEdited = viewModel::clearFormError,
            onDismiss = {
                showForm = false
                editing = null
                viewModel.clearFormError()
            },
            onSave = { name, phone, email, address, notes ->
                viewModel.save(editing?.id, name, phone, email, address, notes)
            }
        )
    }

    toDelete?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.delete),
            message = stringResource(R.string.confirm_delete_supplier),
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
private fun SupplierCard(
    supplier: SupplierEntity,
    isAdmin: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
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
                Icons.Filled.LocalShipping,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    supplier.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Contact details are all optional; blank ones are dropped rather than
                // rendered as empty lines.
                listOf(supplier.phone, supplier.email, supplier.address)
                    .filter { it.isNotBlank() }
                    .forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
            }
            if (isAdmin) {
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
private fun SupplierFormDialog(
    existing: SupplierEntity?,
    errorText: String?,
    submitting: Boolean,
    onFieldEdited: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit
) {
    // Keyed on the target so switching from one supplier to another refills the fields
    // instead of keeping the previous row's values.
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var phone by remember(existing) { mutableStateOf(existing?.phone ?: "") }
    var email by remember(existing) { mutableStateOf(existing?.email ?: "") }
    var address by remember(existing) { mutableStateOf(existing?.address ?: "") }
    var notes by remember(existing) { mutableStateOf(existing?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (existing == null) R.string.add_supplier else R.string.edit_supplier
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
                    label = { Text(stringResource(R.string.supplier_name)) },
                    isError = errorText != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FieldError(errorText)
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(stringResource(R.string.phone)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.email)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(stringResource(R.string.address)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.notes)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, phone, email, address, notes) },
                enabled = name.isNotBlank() && !submitting
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
