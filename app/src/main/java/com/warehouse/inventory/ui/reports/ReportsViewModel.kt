package com.warehouse.inventory.ui.reports

import android.content.Intent
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.inventory.R
import com.warehouse.inventory.data.local.dao.InventoryTransactionDao
import com.warehouse.inventory.data.local.entity.CategoryEntity
import com.warehouse.inventory.data.local.entity.EmployeeEntity
import com.warehouse.inventory.data.local.entity.InventoryTransactionEntity
import com.warehouse.inventory.data.local.entity.PeriodActivity
import com.warehouse.inventory.data.local.entity.ProductMovementTotal
import com.warehouse.inventory.data.local.entity.ProductWithCategory
import com.warehouse.inventory.data.local.entity.TransactionType
import com.warehouse.inventory.data.local.entity.WarehouseEntity
import com.warehouse.inventory.data.repository.CategoryRepository
import com.warehouse.inventory.data.repository.EmployeeRepository
import com.warehouse.inventory.data.repository.HistoryFilter
import com.warehouse.inventory.data.repository.InventoryRepository
import com.warehouse.inventory.data.repository.PermissionChecker
import com.warehouse.inventory.data.repository.ProductRepository
import com.warehouse.inventory.data.repository.WarehouseRepository
import com.warehouse.inventory.util.AdjustmentReasonLabels
import com.warehouse.inventory.util.CsvWriter
import com.warehouse.inventory.util.DateUtils
import com.warehouse.inventory.util.ExportManager
import com.warehouse.inventory.util.PdfReportWriter
import com.warehouse.inventory.util.ProductUnitLabels
import com.warehouse.inventory.util.TransactionTypeLabels
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * The reports this screen can produce.
 *
 * Each constant also declares which filters mean anything for it, so the screen shows only
 * the controls that will actually change the result — offering a product filter on the
 * monthly activity summary would just be a dead input.
 *
 * [fileStem] is deliberately ASCII: it becomes part of an exported filename that travels
 * through share sheets, mail clients and Windows file managers, and Arabic filenames survive
 * that trip badly.
 */
enum class ReportType(
    @StringRes val labelRes: Int,
    val fileStem: String,
    val usesPeriod: Boolean,
    val usesWarehouse: Boolean,
    val usesCategory: Boolean,
    val usesProduct: Boolean,
    val usesEmployee: Boolean
) {
    CURRENT_INVENTORY(
        R.string.report_current_inventory, "inventory",
        usesPeriod = false, usesWarehouse = true, usesCategory = true,
        usesProduct = false, usesEmployee = false
    ),
    LOW_STOCK(
        R.string.report_low_stock, "low_stock",
        usesPeriod = false, usesWarehouse = true, usesCategory = true,
        usesProduct = false, usesEmployee = false
    ),
    STOCK_IN(
        R.string.report_stock_in, "stock_in",
        usesPeriod = true, usesWarehouse = true, usesCategory = true,
        usesProduct = true, usesEmployee = true
    ),
    STOCK_OUT(
        R.string.report_stock_out, "stock_out",
        usesPeriod = true, usesWarehouse = true, usesCategory = true,
        usesProduct = true, usesEmployee = true
    ),
    ADJUSTMENTS(
        R.string.report_adjustments, "adjustments",
        usesPeriod = true, usesWarehouse = true, usesCategory = true,
        usesProduct = true, usesEmployee = true
    ),
    MOVEMENT(
        R.string.report_movement, "movement",
        usesPeriod = true, usesWarehouse = true, usesCategory = true,
        usesProduct = true, usesEmployee = true
    ),
    TOP_ISSUED(
        R.string.report_top_issued, "top_issued",
        usesPeriod = true, usesWarehouse = false, usesCategory = false,
        usesProduct = false, usesEmployee = false
    ),
    DAILY_ACTIVITY(
        R.string.report_daily_activity, "daily_activity",
        usesPeriod = true, usesWarehouse = false, usesCategory = false,
        usesProduct = false, usesEmployee = false
    ),
    MONTHLY_ACTIVITY(
        R.string.report_monthly_activity, "monthly_activity",
        usesPeriod = true, usesWarehouse = false, usesCategory = false,
        usesProduct = false, usesEmployee = false
    );

    /** True when the report is a snapshot of stock as it stands, not a range of movements. */
    val isSnapshot: Boolean get() = !usesPeriod
}

/** Date-range presets offered above the report. */
enum class ReportPeriod(@StringRes val labelRes: Int) {
    TODAY(R.string.period_today),
    LAST_7(R.string.period_last_7),
    LAST_30(R.string.period_last_30),
    THIS_MONTH(R.string.period_this_month),
    ALL(R.string.period_all);

    /**
     * Inclusive `from..to` bounds, matching the `date >= :from AND date <= :to` predicate
     * every query below uses. [DateUtils.todayRange] is half-open, so its upper bound is
     * pulled back by one millisecond here; [DateUtils.lastDaysRange] and
     * [DateUtils.monthRange] are already inclusive.
     */
    fun range(): Pair<Long, Long> = when (this) {
        TODAY -> DateUtils.todayRange().let { (start, endExclusive) -> start to endExclusive - 1 }
        LAST_7 -> DateUtils.lastDaysRange(7)
        LAST_30 -> DateUtils.lastDaysRange(30)
        THIS_MONTH -> DateUtils.monthRange()
        ALL -> 0L to Long.MAX_VALUE
    }
}

/** One column of a generated report. [weight] is its share of the PDF page width. */
data class ReportColumn(@StringRes val headerRes: Int, val weight: Float)

/**
 * A single cell.
 *
 * Most cells are data the database already holds as text. A few are enum labels — a
 * transaction type, a unit, an adjustment reason — which exist only as string resources and
 * therefore cannot be resolved here: a ViewModel has no Context, and resolving them early
 * would also bake the wrong locale into the value. Those carry their resource id and are
 * resolved by the screen.
 */
sealed interface ReportCell {
    data class Text(val value: String) : ReportCell
    data class Label(@StringRes val res: Int) : ReportCell
}

data class ReportTable(
    val columns: List<ReportColumn> = emptyList(),
    val rows: List<List<ReportCell>> = emptyList()
)

enum class ExportFormat { CSV, PDF }

/**
 * A report resolved to plain text and ready to be written to a file.
 *
 * The screen assembles this rather than the ViewModel, because every heading, column header
 * and enum label in it is a string resource. Building it from the same resolved strings the
 * preview shows also guarantees the exported file and the screen never word anything
 * differently.
 */
data class ReportDocument(
    val fileStem: String,
    val title: String,
    val subtitle: String,
    val columnHeaders: List<String>,
    val weights: List<Float>,
    val rows: List<List<String>>,
    val summaryLines: List<String>,
    val footer: String
)

data class ReportsUiState(
    val type: ReportType = ReportType.CURRENT_INVENTORY,
    val period: ReportPeriod = ReportPeriod.LAST_30,
    val warehouseId: Long? = null,
    val categoryId: Long? = null,
    val productId: Long? = null,
    val employeeId: Long? = null,
    val table: ReportTable = ReportTable(),
    val generating: Boolean = false,
    val exporting: Boolean = false,
    /** True when cost and value columns were included, i.e. the viewer is a confirmed admin. */
    val financialsIncluded: Boolean = false,
    @StringRes val message: Int? = null,
    /** Set once a file is written; the screen consumes it to open the share sheet. */
    val pendingShare: ExportManager.Export? = null
) {
    val rowCount: Int get() = table.rows.size
}

/**
 * Builds the report tables and turns them into CSV / PDF files.
 *
 * Purchase cost, selling price and inventory value are only ever put into a table for a
 * confirmed admin. That check is [PermissionChecker.isAdmin] against the database role, not
 * the flag the screen passed in: hiding a column is a UX affordance, and a viewer must not be
 * able to reach the figures by any other route.
 */
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
    // No repository wraps the two report aggregates (topProductsByType / activityByPeriod).
    // Both are pure reads with no rules attached, so the DAO is used directly rather than
    // adding a pass-through that would only restate its signature.
    private val transactionDao: InventoryTransactionDao,
    private val exportManager: ExportManager,
    private val permissionChecker: PermissionChecker,
    warehouseRepository: WarehouseRepository,
    categoryRepository: CategoryRepository,
    employeeRepository: EmployeeRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ReportsUiState())
    val state: StateFlow<ReportsUiState> = _state.asStateFlow()

    val warehouses: StateFlow<List<WarehouseEntity>> = warehouseRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories: StateFlow<List<CategoryEntity>> = categoryRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val products: StateFlow<List<ProductWithCategory>> = productRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val employees: StateFlow<List<EmployeeEntity>> = employeeRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** What the screen believes the signed-in role to be; only ever narrows the real check. */
    private var adminHint = false
    private var generateJob: Job? = null

    /**
     * Records the role the screen was entered with. Does not regenerate on its own — the
     * screen sets it and then calls [generate], so the first table is built once rather than
     * twice on entry.
     */
    fun setAdmin(isAdmin: Boolean) {
        adminHint = isAdmin
    }

    fun onTypeSelected(type: ReportType) {
        if (_state.value.type == type) return
        // Clearing the table rather than leaving the previous report's rows on screen: the
        // column set is about to change, and stale rows under new headers would read as data.
        _state.update { it.copy(type = type, table = ReportTable(), message = null) }
        generate()
    }

    fun onPeriodSelected(period: ReportPeriod) {
        if (_state.value.period == period) return
        _state.update { it.copy(period = period, message = null) }
        generate()
    }

    fun onWarehouseSelected(id: Long?) {
        _state.update { it.copy(warehouseId = id, message = null) }
        generate()
    }

    fun onCategorySelected(id: Long?) {
        _state.update { it.copy(categoryId = id, message = null) }
        generate()
    }

    fun onProductSelected(id: Long?) {
        _state.update { it.copy(productId = id, message = null) }
        generate()
    }

    fun onEmployeeSelected(id: Long?) {
        _state.update { it.copy(employeeId = id, message = null) }
        generate()
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun consumeShare() = _state.update { it.copy(pendingShare = null) }

    /**
     * Share-sheet intent for a finished export.
     *
     * A passthrough to [ExportManager] rather than the screen injecting it directly: the
     * screen already has the ViewModel, and routing it here keeps `ExportManager` (and the
     * FileProvider authority it knows about) out of the UI layer's dependencies.
     */
    fun shareIntent(export: ExportManager.Export, subject: String): Intent =
        exportManager.shareIntent(export, subject)

    /** Regenerates the table for the current selection, replacing any run still in flight. */
    fun generate() {
        val current = _state.value
        generateJob?.cancel()
        _state.update { it.copy(generating = true, message = null) }
        generateJob = viewModelScope.launch {
            val financials = adminHint && permissionChecker.isAdmin()
            val table = try {
                buildTable(current, financials)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(generating = false, message = R.string.error_generic)
                }
                return@launch
            }
            _state.update {
                it.copy(table = table, generating = false, financialsIncluded = financials)
            }
        }
    }

    fun export(format: ExportFormat, document: ReportDocument) {
        if (document.rows.isEmpty()) {
            _state.update { it.copy(message = R.string.export_empty) }
            return
        }
        if (_state.value.exporting) return
        _state.update { it.copy(exporting = true, message = null) }
        viewModelScope.launch {
            val export = try {
                when (format) {
                    ExportFormat.CSV -> exportManager.exportCsv(document.fileStem, document.toCsv())
                    ExportFormat.PDF -> exportManager.exportPdf(document.fileStem, document.toPdf())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(exporting = false, message = R.string.export_failed)
                }
                return@launch
            }
            _state.update {
                it.copy(
                    exporting = false,
                    message = R.string.export_success,
                    pendingShare = export
                )
            }
        }
    }

    // ------------------------------------------------------------ table building

    private suspend fun buildTable(s: ReportsUiState, financials: Boolean): ReportTable =
        when (s.type) {
            ReportType.CURRENT_INVENTORY -> inventoryTable(s, financials)
            ReportType.LOW_STOCK -> lowStockTable(s)
            ReportType.STOCK_IN -> transactionTable(s, setOf(TransactionType.STOCK_IN))
            ReportType.STOCK_OUT -> transactionTable(s, setOf(TransactionType.STOCK_OUT))
            ReportType.ADJUSTMENTS -> transactionTable(s, setOf(TransactionType.ADJUSTMENT))
            ReportType.MOVEMENT -> transactionTable(s, TransactionType.entries.toSet())
            ReportType.TOP_ISSUED -> topIssuedTable(s)
            ReportType.DAILY_ACTIVITY -> activityTable(s, DAILY_FORMAT)
            ReportType.MONTHLY_ACTIVITY -> activityTable(s, MONTHLY_FORMAT)
        }

    private suspend fun inventoryTable(s: ReportsUiState, financials: Boolean): ReportTable {
        val items = productRepository.listOnce(
            categoryId = s.categoryId,
            warehouseId = s.warehouseId,
            lowStockOnly = false
        )
        val fields = buildList {
            add(Field<ProductWithCategory>(ReportColumn(R.string.product_name, 2.4f)) {
                ReportCell.Text(it.product.name)
            })
            add(Field<ProductWithCategory>(ReportColumn(R.string.sku, 1.2f)) {
                ReportCell.Text(it.product.sku.orEmpty())
            })
            add(Field<ProductWithCategory>(ReportColumn(R.string.category, 1.4f)) {
                ReportCell.Text(it.categoryName.orEmpty())
            })
            add(Field<ProductWithCategory>(ReportColumn(R.string.quantity, 0.9f)) {
                ReportCell.Text(it.product.quantity.toString())
            })
            add(Field<ProductWithCategory>(ReportColumn(R.string.unit, 1.0f)) {
                ReportCell.Label(ProductUnitLabels.stringRes(it.product.unit))
            })
            add(Field<ProductWithCategory>(ReportColumn(R.string.location, 1.2f)) {
                ReportCell.Text(it.product.location)
            })
            if (financials) {
                add(Field<ProductWithCategory>(ReportColumn(R.string.purchase_cost, 1.1f)) {
                    ReportCell.Text(money(it.product.purchaseCost))
                })
                add(Field<ProductWithCategory>(ReportColumn(R.string.inventory_value, 1.3f)) {
                    ReportCell.Text(
                        money(it.product.purchaseCost?.times(it.product.quantity))
                    )
                })
            }
        }
        return reportTable(fields, items)
    }

    /**
     * Products at or below their threshold. No cost or value column here at all — the
     * question this report answers is "what must be reordered", which never needs the money.
     */
    private suspend fun lowStockTable(s: ReportsUiState): ReportTable {
        val items = productRepository.listOnce(
            categoryId = s.categoryId,
            warehouseId = s.warehouseId,
            lowStockOnly = true
        )
        return reportTable(
            listOf(
                Field(ReportColumn(R.string.product_name, 2.4f)) {
                    ReportCell.Text(it.product.name)
                },
                Field(ReportColumn(R.string.sku, 1.2f)) {
                    ReportCell.Text(it.product.sku.orEmpty())
                },
                Field(ReportColumn(R.string.category, 1.4f)) {
                    ReportCell.Text(it.categoryName.orEmpty())
                },
                Field(ReportColumn(R.string.quantity, 0.9f)) {
                    ReportCell.Text(it.product.quantity.toString())
                },
                Field(ReportColumn(R.string.low_stock_threshold, 1.2f)) {
                    ReportCell.Text(it.product.lowStockThreshold.toString())
                },
                Field(ReportColumn(R.string.unit, 1.0f)) {
                    ReportCell.Label(ProductUnitLabels.stringRes(it.product.unit))
                },
                Field(ReportColumn(R.string.location, 1.2f)) {
                    ReportCell.Text(it.product.location)
                }
            ),
            items
        )
    }

    private suspend fun transactionTable(
        s: ReportsUiState,
        types: Set<TransactionType>
    ): ReportTable {
        val (from, to) = s.period.range()
        val items = inventoryRepository.listFiltered(
            HistoryFilter(
                types = types,
                productId = s.productId,
                employeeId = s.employeeId,
                warehouseId = s.warehouseId,
                categoryId = s.categoryId,
                from = from,
                to = to
            )
        )
        // Columns that only make sense for one kind of movement: a supplier and an invoice
        // belong to a receipt, a reason belongs to a correction.
        val singleType = types.singleOrNull()
        val fields = buildList {
            add(Field<InventoryTransactionEntity>(ReportColumn(R.string.date, 1.3f)) {
                ReportCell.Text(DateUtils.formatIsoDate(it.date))
            })
            if (singleType == null) {
                add(Field<InventoryTransactionEntity>(
                    ReportColumn(R.string.transaction_type, 1.3f)
                ) {
                    ReportCell.Label(TransactionTypeLabels.stringRes(it.type))
                })
            }
            add(Field<InventoryTransactionEntity>(ReportColumn(R.string.product_name, 2.2f)) {
                ReportCell.Text(it.productName)
            })
            add(Field<InventoryTransactionEntity>(ReportColumn(R.string.warehouse, 1.4f)) {
                ReportCell.Text(it.warehouseName)
            })
            add(Field<InventoryTransactionEntity>(ReportColumn(R.string.quantity, 0.9f)) {
                ReportCell.Text(it.quantity.toString())
            })
            add(Field<InventoryTransactionEntity>(ReportColumn(R.string.unit, 1.0f)) {
                ReportCell.Label(ProductUnitLabels.stringRes(it.unit))
            })
            add(Field<InventoryTransactionEntity>(ReportColumn(R.string.difference, 1.0f)) {
                // Null on rows migrated from the v1 stock_out table, where the app never
                // recorded a balance; "—" rather than a number nobody measured.
                ReportCell.Text(it.difference?.let(::signed) ?: UNKNOWN)
            })
            add(Field<InventoryTransactionEntity>(ReportColumn(R.string.new_stock, 1.1f)) {
                ReportCell.Text(it.newQuantity?.toString() ?: UNKNOWN)
            })
            if (singleType == TransactionType.STOCK_IN) {
                add(Field<InventoryTransactionEntity>(ReportColumn(R.string.supplier, 1.4f)) {
                    ReportCell.Text(it.supplierName)
                })
                add(Field<InventoryTransactionEntity>(
                    ReportColumn(R.string.invoice_number, 1.2f)
                ) {
                    ReportCell.Text(it.invoiceNumber)
                })
            }
            if (singleType == TransactionType.ADJUSTMENT) {
                add(Field<InventoryTransactionEntity>(
                    ReportColumn(R.string.adjustment_reason, 1.4f)
                ) { entry ->
                    entry.reason
                        ?.let { ReportCell.Label(AdjustmentReasonLabels.stringRes(it)) }
                        ?: ReportCell.Text(UNKNOWN)
                })
            }
            add(Field<InventoryTransactionEntity>(ReportColumn(R.string.employee_name, 1.4f)) {
                ReportCell.Text(it.employeeName)
            })
            add(Field<InventoryTransactionEntity>(ReportColumn(R.string.recorded_by, 1.3f)) {
                ReportCell.Text(it.userName)
            })
        }
        return reportTable(fields, items)
    }

    private suspend fun topIssuedTable(s: ReportsUiState): ReportTable {
        val (from, to) = s.period.range()
        val items = transactionDao.topProductsByType(
            type = TransactionType.STOCK_OUT.name,
            from = from,
            to = to,
            limit = TOP_PRODUCTS_LIMIT
        )
        return reportTable(
            listOf(
                Field<ProductMovementTotal>(ReportColumn(R.string.product_name, 2.6f)) {
                    ReportCell.Text(it.productName)
                },
                Field(ReportColumn(R.string.unit, 1.0f)) {
                    ReportCell.Label(ProductUnitLabels.stringRes(it.unit))
                },
                Field(ReportColumn(R.string.total, 1.2f)) {
                    ReportCell.Text(it.totalQuantity.toString())
                },
                Field(ReportColumn(R.string.count, 1.0f)) {
                    ReportCell.Text(it.transactionCount.toString())
                }
            ),
            items
        )
    }

    private suspend fun activityTable(s: ReportsUiState, format: String): ReportTable {
        val (from, to) = s.period.range()
        val items = transactionDao.activityByPeriod(format, from, to)
        return reportTable(
            listOf(
                Field<PeriodActivity>(ReportColumn(R.string.report_period, 1.4f)) {
                    ReportCell.Text(it.period)
                },
                Field(ReportColumn(R.string.stock_in, 1.2f)) {
                    ReportCell.Text(it.stockInQuantity.toString())
                },
                Field(ReportColumn(R.string.stock_out, 1.2f)) {
                    ReportCell.Text(it.stockOutQuantity.toString())
                },
                Field(ReportColumn(R.string.stock_adjustment, 1.3f)) {
                    ReportCell.Text(it.adjustmentCount.toString())
                },
                Field(ReportColumn(R.string.count, 1.0f)) {
                    ReportCell.Text(it.transactionCount.toString())
                }
            ),
            items
        )
    }

    private fun money(value: Double?): String =
        if (value == null) "" else String.format(Locale.US, "%.2f", value)

    /** Signed movement, so a correction downwards is not mistaken for a receipt. */
    private fun signed(value: Int): String = if (value > 0) "+$value" else value.toString()

    private companion object {
        /** SQLite strftime patterns, as [InventoryTransactionDao.activityByPeriod] expects. */
        const val DAILY_FORMAT = "%Y-%m-%d"
        const val MONTHLY_FORMAT = "%Y-%m"
        const val TOP_PRODUCTS_LIMIT = 50
        const val UNKNOWN = "—"
    }
}

/**
 * A column bound to the cell it produces.
 *
 * Pairing them is what makes it impossible for the headers and the cells to drift out of
 * step, which is the failure mode of a report whose column set changes with the selected
 * filters — a shifted column silently mislabels every number in the export.
 */
private class Field<T>(val column: ReportColumn, val cell: (T) -> ReportCell)

private fun <T> reportTable(fields: List<Field<T>>, items: List<T>) = ReportTable(
    columns = fields.map { it.column },
    rows = items.map { item -> fields.map { it.cell(item) } }
)

private fun ReportDocument.toCsv(): CsvWriter = CsvWriter().apply {
    row(title)
    if (subtitle.isNotBlank()) row(subtitle)
    blankLine()
    row(columnHeaders)
    rows.forEach { row(it) }
    if (summaryLines.isNotEmpty()) {
        blankLine()
        summaryLines.forEach { row(it) }
    }
}

private fun ReportDocument.toPdf(): PdfReportWriter.Document = PdfReportWriter.Document(
    title = title,
    subtitle = subtitle,
    columns = columnHeaders.mapIndexed { index, header ->
        PdfReportWriter.Column(header, weights.getOrElse(index) { 1f })
    },
    rows = rows,
    summaryLines = summaryLines,
    footer = footer
)
