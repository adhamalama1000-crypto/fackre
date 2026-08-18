package com.warehouse.inventory.data.local.entity

/**
 * The kind of movement an [InventoryTransactionEntity] represents.
 *
 * Every row in the unified history carries one of these. A warehouse-to-warehouse
 * transfer produces exactly two rows ([TRANSFER_OUT] then [TRANSFER_IN]) sharing a
 * single `transferGroupId`, so the pair can be shown as one operation.
 */
enum class TransactionType {
    STOCK_IN,
    STOCK_OUT,
    ADJUSTMENT,
    TRANSFER_OUT,
    TRANSFER_IN
}

/**
 * Why a stock adjustment was made. Recorded so a physical-count correction is never
 * an unexplained change.
 */
enum class AdjustmentReason {
    DAMAGED,
    LOST,
    COUNTING_ERROR,
    MANUAL_CORRECTION,
    OTHER
}

/** Unit of measure a product's quantities are expressed in. */
enum class ProductUnit {
    PIECE,
    BOX,
    CARTON,
    PACK,
    METER,
    KILOGRAM,
    LITER
}

/** An action worth keeping in the audit trail. */
enum class AuditAction {
    LOGIN,
    LOGOUT,
    PRODUCT_CREATED,
    PRODUCT_EDITED,
    PRODUCT_DELETED,
    CATEGORY_CREATED,
    CATEGORY_EDITED,
    CATEGORY_DELETED,
    USER_CREATED,
    USER_EDITED,
    USER_DELETED,
    SUPPLIER_CREATED,
    SUPPLIER_EDITED,
    SUPPLIER_DELETED,
    EMPLOYEE_CREATED,
    EMPLOYEE_EDITED,
    EMPLOYEE_DELETED,
    WAREHOUSE_CREATED,
    WAREHOUSE_EDITED,
    WAREHOUSE_DELETED,
    STOCK_IN,
    STOCK_OUT,
    STOCK_ADJUSTMENT,
    STOCK_TRANSFER,
    BACKUP_CREATED,
    BACKUP_RESTORED,
    PERMISSION_DENIED
}
