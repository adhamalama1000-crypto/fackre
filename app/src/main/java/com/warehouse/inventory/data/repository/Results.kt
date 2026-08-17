package com.warehouse.inventory.data.repository

/**
 * Outcomes of a stock movement. Each case maps to one specific Arabic message in the UI,
 * which is why they are distinct objects rather than a single error string.
 */
sealed interface StockResult {
    data object Success : StockResult

    /** Quantity was zero, negative, or unparseable. */
    data object InvalidQuantity : StockResult

    /** Less stock on hand than the operation asked for. Nothing was changed. */
    data object InsufficientStock : StockResult

    data object ProductNotFound : StockResult
    data object WarehouseNotFound : StockResult

    /** Transfer source and destination were the same warehouse. */
    data object SameWarehouse : StockResult

    /** An adjustment whose new quantity equals the current one — nothing to record. */
    data object NoChange : StockResult

    /** The business date was outside the acceptable range. */
    data object InvalidDate : StockResult

    /** Caller is not an admin. The attempt is recorded in the audit log. */
    data object NotAuthorized : StockResult

    data class Failed(val message: String? = null) : StockResult
}

/** Outcome of creating or editing a reference record (supplier, employee, warehouse, ...). */
sealed interface SaveResult {
    data class Success(val id: Long) : SaveResult

    /** A required text field was blank. */
    data object MissingRequiredField : SaveResult

    /** A uniquely-indexed value (name, code, SKU, barcode, email) is already taken. */
    data class Duplicate(val field: DuplicateField) : SaveResult

    data object NotFound : SaveResult
    data object NotAuthorized : SaveResult
    data class Failed(val message: String? = null) : SaveResult
}

/** Which unique field collided, so the UI can flag the right input. */
enum class DuplicateField { NAME, CODE, EMAIL, SKU, BARCODE }

/** Outcome of deleting a reference record. */
sealed interface DeleteResult {
    data object Success : DeleteResult
    data object NotAuthorized : DeleteResult

    /** Blocked because other records still depend on it; [details] explains what. */
    data class InUse(val details: String = "") : DeleteResult
    data class Failed(val message: String? = null) : DeleteResult
}
