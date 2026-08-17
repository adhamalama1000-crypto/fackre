package com.warehouse.inventory.data.repository

import com.warehouse.inventory.data.local.entity.AuditAction
import com.warehouse.inventory.data.local.entity.TransactionType

/**
 * Criteria for querying the unified inventory history. Every field is optional; the
 * defaults select everything.
 */
data class HistoryFilter(
    val types: Set<TransactionType> = TransactionType.entries.toSet(),
    val productId: Long? = null,
    val employeeId: Long? = null,
    val warehouseId: Long? = null,
    val supplierId: Long? = null,
    val categoryId: Long? = null,
    /** Inclusive lower bound on the business date, epoch millis. */
    val from: Long = 0L,
    /** Inclusive upper bound on the business date, epoch millis. */
    val to: Long = Long.MAX_VALUE,
    val query: String = ""
) {
    /**
     * Type names for the SQL `IN` clause. An empty selection is widened to "all types"
     * because SQLite rejects an empty `IN ()` — and an empty filter chip row should read
     * as "no filter", not "no results".
     */
    val typeNames: List<String>
        get() = types.ifEmpty { TransactionType.entries.toSet() }.map { it.name }

    val isFiltered: Boolean
        get() = types.size != TransactionType.entries.size ||
            productId != null || employeeId != null || warehouseId != null ||
            supplierId != null || categoryId != null ||
            from != 0L || to != Long.MAX_VALUE || query.isNotBlank()
}

/** Criteria for querying the audit log. Same conventions as [HistoryFilter]. */
data class AuditFilter(
    val actions: Set<AuditAction> = AuditAction.entries.toSet(),
    val userId: Long? = null,
    val from: Long = 0L,
    val to: Long = Long.MAX_VALUE,
    val query: String = ""
) {
    val actionNames: List<String>
        get() = actions.ifEmpty { AuditAction.entries.toSet() }.map { it.name }
}
