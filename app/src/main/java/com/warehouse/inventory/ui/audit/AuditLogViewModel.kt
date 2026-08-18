package com.warehouse.inventory.ui.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.inventory.data.local.entity.AuditAction
import com.warehouse.inventory.data.local.entity.AuditLogEntity
import com.warehouse.inventory.data.repository.AuditFilter
import com.warehouse.inventory.data.repository.AuditLogRepository
import com.warehouse.inventory.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Backs the audit trail screen. Same shape as `HistoryViewModel`: [filter] is the single
 * source of truth and the list is derived from it.
 *
 * No role check here — [AuditLogRepository.observeFiltered] is admin-only and emits an
 * empty list for everyone else, so this ViewModel stays a plain projection of the filter.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AuditLogViewModel @Inject constructor(
    private val auditLogRepository: AuditLogRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(AuditFilter())
    val filter: StateFlow<AuditFilter> = _filter.asStateFlow()

    val entries: StateFlow<List<AuditLogEntity>> = _filter
        .flatMapLatest { auditLogRepository.observeFiltered(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChange(value: String) = _filter.update { it.copy(query = value) }

    /**
     * Null means "all actions". The filter stores a set because the DAO takes an `IN` list;
     * the screen only ever picks one action at a time, so the set holds either everything
     * or a single value.
     */
    fun onActionSelected(action: AuditAction?) = _filter.update {
        it.copy(actions = action?.let(::setOf) ?: AuditAction.entries.toSet())
    }

    fun onDateFrom(millis: Long?) = _filter.update {
        it.copy(from = millis?.let(DateUtils::startOfDay) ?: 0L)
    }

    fun onDateTo(millis: Long?) = _filter.update {
        it.copy(to = millis?.let(DateUtils::endOfDay) ?: Long.MAX_VALUE)
    }

    fun clearFilters() { _filter.value = AuditFilter() }
}
