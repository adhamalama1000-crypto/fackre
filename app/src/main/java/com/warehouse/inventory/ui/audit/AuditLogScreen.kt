package com.warehouse.inventory.ui.audit

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warehouse.inventory.R
import com.warehouse.inventory.data.local.entity.AuditAction
import com.warehouse.inventory.data.local.entity.AuditLogEntity
import com.warehouse.inventory.data.repository.AuditFilter
import com.warehouse.inventory.ui.common.AppTopBar
import com.warehouse.inventory.ui.common.DateField
import com.warehouse.inventory.ui.common.DetailRow
import com.warehouse.inventory.ui.common.EmptyState
import com.warehouse.inventory.ui.common.LabeledDropdown
import com.warehouse.inventory.ui.common.Pill
import com.warehouse.inventory.ui.common.SectionHeader
import com.warehouse.inventory.ui.common.ThinDivider
import com.warehouse.inventory.util.AuditActionLabels
import com.warehouse.inventory.util.DateUtils

/**
 * The audit trail, newest first.
 *
 * Access control lives in `AuditLogRepository`, which streams an empty list to anyone who
 * is not an admin. This screen therefore renders the ordinary empty state for a viewer and
 * makes no claim about who is allowed to be here.
 */
@Composable
fun AuditLogScreen(
    onBack: () -> Unit,
    viewModel: AuditLogViewModel = hiltViewModel()
) {
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()

    var showFilters by rememberSaveable { mutableStateOf(false) }

    val isFiltered = filter.isFiltered()

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.audit_log),
                onBack = onBack,
                actions = {
                    if (isFiltered) {
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
                label = { Text(stringResource(R.string.audit_search_hint)) },
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

            if (showFilters) {
                FilterPanel(
                    filter = filter,
                    isFiltered = isFiltered,
                    onActionSelected = viewModel::onActionSelected,
                    onDateFrom = viewModel::onDateFrom,
                    onDateTo = viewModel::onDateTo,
                    onClearFilters = viewModel::clearFilters
                )
            }

            ThinDivider(Modifier.padding(top = 8.dp))

            if (entries.isEmpty()) {
                EmptyState(
                    message = stringResource(R.string.no_audit_entries),
                    icon = Icons.Filled.Assignment
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(entries, key = { it.id }) { entry -> AuditCard(entry) }
                }
            }
        }
    }
}

/**
 * Unlike `HistoryFilter`, [AuditFilter] carries no `isFiltered`. Only the UI needs it —
 * to decide whether a "clear" affordance is worth showing — so it lives here.
 */
private fun AuditFilter.isFiltered(): Boolean =
    query.isNotBlank() ||
        actions.size != AuditAction.entries.size ||
        from != 0L ||
        to != Long.MAX_VALUE

@Composable
private fun FilterPanel(
    filter: AuditFilter,
    isFiltered: Boolean,
    onActionSelected: (AuditAction?) -> Unit,
    onDateFrom: (Long?) -> Unit,
    onDateTo: (Long?) -> Unit,
    onClearFilters: () -> Unit
) {
    // 27 actions is far too many for a chip row, so the action filter is a dropdown. The
    // Arabic labels are resolved once here because LabeledDropdown's label lambda is not
    // composable and so cannot call stringResource itself.
    val actionLabels = AuditAction.entries.associateWith {
        stringResource(AuditActionLabels.stringRes(it))
    }
    // A single-value selection: the filter holds every action when nothing is picked.
    val selectedAction = filter.actions.singleOrNull()

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeader(stringResource(R.string.filters))

            ClearableField(active = selectedAction != null, onClear = { onActionSelected(null) }) {
                LabeledDropdown(
                    fieldLabel = stringResource(R.string.audit_action),
                    selected = selectedAction,
                    items = AuditAction.entries,
                    label = { actionLabels[it].orEmpty() },
                    onSelected = onActionSelected,
                    placeholder = stringResource(R.string.all_actions),
                    modifier = Modifier.weight(1f)
                )
            }

            SectionHeader(stringResource(R.string.date_range))

            // Unset bounds have no date to display, so the picker opens on today; the clear
            // button is what signals that a bound is actually applied.
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

            if (isFiltered) {
                TextButton(onClick = onClearFilters) {
                    Text(stringResource(R.string.clear_filters))
                }
            }
        }
    }
}

/** A filter input plus a reset affordance that exists only while the input is [active]. */
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
private fun AuditCard(entry: AuditLogEntity) {
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
                    text = stringResource(AuditActionLabels.stringRes(entry.action)),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.weight(1f))
                // Time as well as date: two audit entries a minute apart are common and the
                // order they happened in is the point of the trail.
                Text(
                    DateUtils.formatDateTime(entry.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            DetailRow(stringResource(R.string.audit_user), entry.userName)
            if (entry.targetName.isNotBlank()) {
                DetailRow(stringResource(R.string.audit_target), entry.targetName)
            }
            if (entry.details.isNotBlank()) {
                DetailRow(stringResource(R.string.notes), entry.details)
            }
        }
    }
}
