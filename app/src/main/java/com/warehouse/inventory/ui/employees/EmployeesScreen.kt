package com.warehouse.inventory.ui.employees

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
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
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
import com.warehouse.inventory.data.local.entity.EmployeeEntity
import com.warehouse.inventory.ui.common.AppTopBar
import com.warehouse.inventory.ui.common.ConfirmDialog
import com.warehouse.inventory.ui.common.EmptyState
import com.warehouse.inventory.ui.common.FieldError
import com.warehouse.inventory.ui.common.Pill

@Composable
fun EmployeesScreen(
    isAdmin: Boolean,
    onBack: () -> Unit,
    viewModel: EmployeesViewModel = hiltViewModel()
) {
    val employees by viewModel.employees.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EmployeeEntity?>(null) }
    var toDelete by remember { mutableStateOf<EmployeeEntity?>(null) }

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
        topBar = { AppTopBar(title = stringResource(R.string.employees), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            if (isAdmin) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editing = null
                        showForm = true
                    },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_employee)) }
                )
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                label = { Text(stringResource(R.string.employee_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Kept permanently visible, not only in the empty state: the most common mistake
            // on this screen is expecting app logins to appear here.
            Text(
                stringResource(R.string.employees_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )

            if (employees.isEmpty()) {
                EmptyState(
                    message = stringResource(
                        if (query.isBlank()) R.string.no_employees else R.string.no_results
                    ),
                    icon = Icons.Filled.Group
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(employees, key = { it.id }) { employee ->
                        EmployeeCard(
                            employee = employee,
                            isAdmin = isAdmin,
                            onEdit = {
                                editing = employee
                                showForm = true
                            },
                            onDelete = { toDelete = employee }
                        )
                    }
                }
            }
        }
    }

    if (showForm) {
        EmployeeFormDialog(
            existing = editing,
            errorText = state.formError?.let { stringResource(it) },
            submitting = state.submitting,
            onFieldEdited = viewModel::clearFormError,
            onDismiss = {
                showForm = false
                editing = null
                viewModel.clearFormError()
            },
            onSave = { name, code, phone, department, notes, active ->
                viewModel.save(editing?.id, name, code, phone, department, notes, active)
            }
        )
    }

    toDelete?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.delete),
            message = stringResource(R.string.confirm_delete_employee),
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
private fun EmployeeCard(
    employee: EmployeeEntity,
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
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    employee.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                listOfNotNull(employee.code, employee.department.takeIf { it.isNotBlank() })
                    .forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                if (employee.phone.isNotBlank()) {
                    Text(
                        employee.phone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                // Only former staff are flagged; marking everyone "active" would be noise.
                if (!employee.active) {
                    Pill(
                        text = stringResource(R.string.employee_inactive),
                        container = MaterialTheme.colorScheme.surfaceVariant,
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
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
private fun EmployeeFormDialog(
    existing: EmployeeEntity?,
    errorText: String?,
    submitting: Boolean,
    onFieldEdited: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, Boolean) -> Unit
) {
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    // The entity stores a blank code as null; the form only deals in text.
    var code by remember(existing) { mutableStateOf(existing?.code ?: "") }
    var phone by remember(existing) { mutableStateOf(existing?.phone ?: "") }
    var department by remember(existing) { mutableStateOf(existing?.department ?: "") }
    var notes by remember(existing) { mutableStateOf(existing?.notes ?: "") }
    var active by remember(existing) { mutableStateOf(existing?.active ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (existing == null) R.string.add_employee else R.string.edit_employee
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
                    label = { Text(stringResource(R.string.employee_name)) },
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
                    label = { Text(stringResource(R.string.employee_code)) },
                    // A duplicate code is the other error this dialog can report, so the
                    // same message hangs under whichever field the user touches next.
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
                    value = department,
                    onValueChange = { department = it },
                    label = { Text(stringResource(R.string.department)) },
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
                        stringResource(R.string.employee_active),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, code, phone, department, notes, active) },
                enabled = name.isNotBlank() && !submitting
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
