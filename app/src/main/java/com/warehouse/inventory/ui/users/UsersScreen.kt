package com.warehouse.inventory.ui.users

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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warehouse.inventory.R
import com.warehouse.inventory.data.local.entity.UserEntity
import com.warehouse.inventory.data.local.entity.UserRole
import com.warehouse.inventory.ui.common.AppTopBar
import com.warehouse.inventory.ui.common.ConfirmDialog
import com.warehouse.inventory.ui.common.EmptyState
import com.warehouse.inventory.ui.common.FieldError
import com.warehouse.inventory.ui.common.Pill
import com.warehouse.inventory.util.UserRoleLabels

/**
 * App user accounts: create, edit role/password, delete.
 *
 * Admin-only in practice — every operation here goes through
 * [com.warehouse.inventory.data.repository.UserRepository], which refuses non-admins and
 * additionally protects the last remaining admin account from being demoted or deleted.
 * Viewers reaching this screen see the list without any mutating affordance.
 */
@Composable
fun UsersScreen(
    isAdmin: Boolean,
    onBack: () -> Unit,
    viewModel: UsersViewModel = hiltViewModel()
) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<UserEntity?>(null) }
    var toDelete by remember { mutableStateOf<UserEntity?>(null) }

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
        topBar = { AppTopBar(title = stringResource(R.string.users), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            if (isAdmin) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editing = null
                        showForm = true
                    },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_user)) }
                )
            }
        }
    ) { padding ->
        if (users.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.no_users),
                icon = Icons.Filled.Person,
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(users, key = { it.id }) { user ->
                    UserCard(
                        user = user,
                        isAdmin = isAdmin,
                        onEdit = {
                            editing = user
                            showForm = true
                        },
                        onDelete = { toDelete = user }
                    )
                }
            }
        }
    }

    if (showForm) {
        UserFormDialog(
            existing = editing,
            errorText = state.formError?.let { stringResource(it) },
            submitting = state.submitting,
            onFieldEdited = viewModel::clearFormError,
            onDismiss = {
                showForm = false
                editing = null
                viewModel.clearFormError()
            },
            onConfirm = { name, email, password, role ->
                viewModel.save(editing, name, email, password, role)
            }
        )
    }

    toDelete?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.delete),
            message = stringResource(R.string.confirm_delete_user),
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
private fun UserCard(
    user: UserEntity,
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Pill(
                text = stringResource(UserRoleLabels.stringRes(user.role)),
                container = if (user.role == UserRole.ADMIN)
                    MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                content = if (user.role == UserRole.ADMIN)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
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
private fun UserFormDialog(
    existing: UserEntity?,
    errorText: String?,
    submitting: Boolean,
    onFieldEdited: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (name: String, email: String, password: String, role: UserRole) -> Unit
) {
    val isEdit = existing != null
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var email by remember(existing?.id) { mutableStateOf(existing?.email.orEmpty()) }
    var password by remember(existing?.id) { mutableStateOf("") }
    var role by remember(existing?.id) { mutableStateOf(existing?.role ?: UserRole.VIEWER) }

    // On create everything is required. On edit the password may stay blank to keep the
    // current one, and the email is fixed because it is the login identifier.
    val canSubmit = name.isNotBlank() && !submitting &&
        (isEdit || (email.isNotBlank() && password.isNotBlank()))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isEdit) R.string.edit_user else R.string.add_user)) },
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
                    label = { Text(stringResource(R.string.user_name)) },
                    singleLine = true,
                    isError = errorText != null,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        onFieldEdited()
                    },
                    label = { Text(stringResource(R.string.email)) },
                    singleLine = true,
                    enabled = !isEdit,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        onFieldEdited()
                    },
                    label = {
                        Text(
                            stringResource(
                                if (isEdit) R.string.new_password_optional else R.string.password
                            )
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FieldError(errorText)

                Text(
                    stringResource(R.string.user_role),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UserRole.entries.forEach { option ->
                        FilterChip(
                            selected = role == option,
                            onClick = { role = option },
                            label = { Text(stringResource(UserRoleLabels.stringRes(option))) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), email.trim(), password, role) },
                enabled = canSubmit
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
