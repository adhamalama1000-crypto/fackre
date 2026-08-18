package com.warehouse.inventory.util

import androidx.annotation.StringRes
import com.warehouse.inventory.R
import com.warehouse.inventory.data.local.entity.AdjustmentReason
import com.warehouse.inventory.data.local.entity.AuditAction
import com.warehouse.inventory.data.local.entity.ProductUnit
import com.warehouse.inventory.data.local.entity.TransactionType
import com.warehouse.inventory.data.local.entity.UserRole

/**
 * Maps persisted enum values to Arabic string resources.
 *
 * Enums are stored in the database by their English `name` (stable, machine-readable) and
 * must never be shown to the user as such. Centralising the mapping here is what keeps
 * `STOCK_IN` from leaking into the Arabic UI, and makes it a compile error to add an enum
 * constant without giving it a label — the `when` blocks below are exhaustive.
 */
object ProductUnitLabels {
    @StringRes
    fun stringRes(unit: ProductUnit): Int = when (unit) {
        ProductUnit.PIECE -> R.string.unit_piece
        ProductUnit.BOX -> R.string.unit_box
        ProductUnit.CARTON -> R.string.unit_carton
        ProductUnit.PACK -> R.string.unit_pack
        ProductUnit.METER -> R.string.unit_meter
        ProductUnit.KILOGRAM -> R.string.unit_kilogram
        ProductUnit.LITER -> R.string.unit_liter
    }
}

object TransactionTypeLabels {
    @StringRes
    fun stringRes(type: TransactionType): Int = when (type) {
        TransactionType.STOCK_IN -> R.string.transaction_stock_in
        TransactionType.STOCK_OUT -> R.string.transaction_stock_out
        TransactionType.ADJUSTMENT -> R.string.transaction_adjustment
        TransactionType.TRANSFER_OUT -> R.string.transaction_transfer_out
        TransactionType.TRANSFER_IN -> R.string.transaction_transfer_in
    }
}

object AdjustmentReasonLabels {
    @StringRes
    fun stringRes(reason: AdjustmentReason): Int = when (reason) {
        AdjustmentReason.DAMAGED -> R.string.reason_damaged
        AdjustmentReason.LOST -> R.string.reason_lost
        AdjustmentReason.COUNTING_ERROR -> R.string.reason_counting_error
        AdjustmentReason.MANUAL_CORRECTION -> R.string.reason_manual_correction
        AdjustmentReason.OTHER -> R.string.reason_other
    }
}

object UserRoleLabels {
    @StringRes
    fun stringRes(role: UserRole): Int = when (role) {
        UserRole.ADMIN -> R.string.role_admin
        UserRole.VIEWER -> R.string.role_viewer
    }
}

object AuditActionLabels {
    @StringRes
    fun stringRes(action: AuditAction): Int = when (action) {
        AuditAction.LOGIN -> R.string.audit_login
        AuditAction.LOGOUT -> R.string.audit_logout
        AuditAction.PRODUCT_CREATED -> R.string.audit_product_created
        AuditAction.PRODUCT_EDITED -> R.string.audit_product_edited
        AuditAction.PRODUCT_DELETED -> R.string.audit_product_deleted
        AuditAction.CATEGORY_CREATED -> R.string.audit_category_created
        AuditAction.CATEGORY_EDITED -> R.string.audit_category_edited
        AuditAction.CATEGORY_DELETED -> R.string.audit_category_deleted
        AuditAction.USER_CREATED -> R.string.audit_user_created
        AuditAction.USER_EDITED -> R.string.audit_user_edited
        AuditAction.USER_DELETED -> R.string.audit_user_deleted
        AuditAction.SUPPLIER_CREATED -> R.string.audit_supplier_created
        AuditAction.SUPPLIER_EDITED -> R.string.audit_supplier_edited
        AuditAction.SUPPLIER_DELETED -> R.string.audit_supplier_deleted
        AuditAction.EMPLOYEE_CREATED -> R.string.audit_employee_created
        AuditAction.EMPLOYEE_EDITED -> R.string.audit_employee_edited
        AuditAction.EMPLOYEE_DELETED -> R.string.audit_employee_deleted
        AuditAction.WAREHOUSE_CREATED -> R.string.audit_warehouse_created
        AuditAction.WAREHOUSE_EDITED -> R.string.audit_warehouse_edited
        AuditAction.WAREHOUSE_DELETED -> R.string.audit_warehouse_deleted
        AuditAction.STOCK_IN -> R.string.audit_stock_in
        AuditAction.STOCK_OUT -> R.string.audit_stock_out
        AuditAction.STOCK_ADJUSTMENT -> R.string.audit_stock_adjustment
        AuditAction.STOCK_TRANSFER -> R.string.audit_stock_transfer
        AuditAction.BACKUP_CREATED -> R.string.audit_backup_created
        AuditAction.BACKUP_RESTORED -> R.string.audit_backup_restored
        AuditAction.PERMISSION_DENIED -> R.string.audit_permission_denied
    }
}
