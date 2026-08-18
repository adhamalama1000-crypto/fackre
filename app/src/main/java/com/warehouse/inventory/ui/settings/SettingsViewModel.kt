package com.warehouse.inventory.ui.settings

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.inventory.R
import com.warehouse.inventory.data.local.AppDatabase
import com.warehouse.inventory.data.local.entity.AuditAction
import com.warehouse.inventory.data.repository.AuditLogRepository
import com.warehouse.inventory.data.repository.PermissionChecker
import com.warehouse.inventory.data.session.AppPreferences
import com.warehouse.inventory.data.session.SessionManager
import com.warehouse.inventory.data.session.ThemeMode
import com.warehouse.inventory.util.DatabaseBackupManager
import com.warehouse.inventory.util.DateUtils
import com.warehouse.inventory.util.LowStockNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * A snackbar message, carried as a resource id plus formatting arguments because the
 * ViewModel has no Context and must not build user-facing Arabic strings itself.
 */
data class UiMessage(@StringRes val res: Int, val args: List<String> = emptyList())

/**
 * A backup file that passed validation and is waiting for the user to confirm the overwrite.
 * Holding the [uri] rather than the extracted data means nothing has touched the live
 * database yet — cancelling leaves the app exactly as it was.
 */
data class PendingRestore(
    val uri: Uri,
    val createdAt: Long,
    val appVersion: String,
    val imageCount: Int
)

data class SettingsUiState(
    val busy: Boolean = false,
    val message: UiMessage? = null,
    val pendingRestore: PendingRestore? = null,
    /** Set once the database has been replaced; the screen then asks for a restart. */
    val restoreComplete: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AppPreferences,
    private val backupManager: DatabaseBackupManager,
    private val permissionChecker: PermissionChecker,
    private val auditLogRepository: AuditLogRepository,
    private val lowStockNotifier: LowStockNotifier,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = preferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val lowStockNotifications: StateFlow<Boolean> = preferences.lowStockNotificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val schemaVersion: Int get() = AppDatabase.VERSION

    /** Filename offered to the system file picker when creating a backup. */
    fun suggestedBackupName(): String = backupManager.suggestedFileName()

    // ------------------------------------------------------------- preferences

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setLowStockNotifications(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setLowStockNotificationsEnabled(enabled)
            // Turning the setting on should surface anything already below threshold rather
            // than waiting for the next stock movement to trigger a check.
            if (enabled) lowStockNotifier.refresh()
        }
    }

    fun checkLowStockNow() {
        viewModelScope.launch {
            lowStockNotifier.refresh()
            _state.update { it.copy(message = UiMessage(R.string.low_stock_check_done)) }
        }
    }

    // ----------------------------------------------------------------- backup

    /**
     * Writes a backup to the document the user created via the system picker.
     *
     * Admin-only, checked here against the database row rather than relying on the screen
     * having hidden the button — a viewer reaching this method is refused and the attempt is
     * recorded in the audit log by [PermissionChecker].
     */
    fun createBackup(target: Uri) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val actor = permissionChecker.requireAdmin("backup_create")
            if (actor == null) {
                _state.update {
                    it.copy(busy = false, message = UiMessage(R.string.error_not_authorized))
                }
                return@launch
            }

            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(target)
                        ?: error("cannot open output stream")
                    stream.use { backupManager.createBackup(it) }
                }
            }.getOrElse { DatabaseBackupManager.BackupResult.Failed(it.message) }

            when (result) {
                is DatabaseBackupManager.BackupResult.Success -> {
                    auditLogRepository.recordAs(
                        actor, AuditAction.BACKUP_CREATED, "backup", null, "",
                        "قاعدة البيانات و${result.imageCount} صورة"
                    )
                    _state.update {
                        it.copy(busy = false, message = UiMessage(R.string.backup_success))
                    }
                }

                is DatabaseBackupManager.BackupResult.Failed ->
                    _state.update {
                        it.copy(busy = false, message = UiMessage(R.string.backup_failed))
                    }
            }
        }
    }

    // ---------------------------------------------------------------- restore

    /**
     * Validates a chosen file and, if it is a usable backup, raises the confirmation state.
     * Nothing is written here: an invalid file produces a specific Arabic reason and the
     * current database is untouched.
     */
    fun inspectBackup(source: Uri) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            if (permissionChecker.requireAdmin("backup_restore_inspect") == null) {
                _state.update {
                    it.copy(busy = false, message = UiMessage(R.string.error_not_authorized))
                }
                return@launch
            }

            val inspection = runCatching {
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openInputStream(source)
                        ?: error("cannot open input stream")
                    stream.use { backupManager.inspect(it) }
                }
            }.getOrElse { DatabaseBackupManager.Inspection.Unreadable(it.message) }

            if (inspection is DatabaseBackupManager.Inspection.Valid) {
                _state.update {
                    it.copy(
                        busy = false,
                        pendingRestore = PendingRestore(
                            uri = source,
                            createdAt = inspection.createdAt,
                            appVersion = inspection.appVersion,
                            imageCount = inspection.imageCount
                        )
                    )
                }
            } else {
                _state.update {
                    it.copy(busy = false, message = UiMessage(messageFor(inspection)))
                }
            }
        }
    }

    fun cancelRestore() = _state.update { it.copy(pendingRestore = null) }

    /**
     * Performs the restore the user just confirmed.
     *
     * The file is re-opened and re-validated by [DatabaseBackupManager.restore] rather than
     * trusting the earlier inspection, because the document could have changed underneath us
     * between the two steps.
     *
     * On success the session is cleared: the restored database has its own `users` table, and
     * a session pointing at a user id from the *previous* database would otherwise survive
     * the swap and resolve to the wrong person or to nobody at all.
     */
    fun confirmRestore() {
        val pending = _state.value.pendingRestore ?: return
        _state.update { it.copy(busy = true, pendingRestore = null) }
        viewModelScope.launch {
            val actor = permissionChecker.requireAdmin("backup_restore")
            if (actor == null) {
                _state.update {
                    it.copy(busy = false, message = UiMessage(R.string.error_not_authorized))
                }
                return@launch
            }

            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openInputStream(pending.uri)
                        ?: error("cannot open input stream")
                    stream.use { backupManager.restore(it) }
                }
            }.getOrElse { DatabaseBackupManager.RestoreResult.Failed(it.message) }

            when (result) {
                DatabaseBackupManager.RestoreResult.SuccessRequiresRestart -> {
                    // Best-effort, and it lands in the *restored* database — the one that
                    // survives — so the trail records that this data was restored and by whom.
                    auditLogRepository.recordAs(
                        actor, AuditAction.BACKUP_RESTORED, "backup", null, "",
                        "استُعيدت نسخة أُنشئت في ${DateUtils.formatDateTime(pending.createdAt)}"
                    )
                    // The notified-low-stock set describes products that no longer exist.
                    lowStockNotifier.reset()
                    sessionManager.signOut()
                    _state.update {
                        it.copy(
                            busy = false,
                            restoreComplete = true,
                            message = UiMessage(R.string.restore_success)
                        )
                    }
                }

                is DatabaseBackupManager.RestoreResult.Rejected ->
                    _state.update {
                        it.copy(busy = false, message = UiMessage(messageFor(result.inspection)))
                    }

                is DatabaseBackupManager.RestoreResult.Failed ->
                    _state.update {
                        it.copy(busy = false, message = UiMessage(R.string.restore_failed))
                    }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** Maps a rejection to the specific Arabic reason, never a generic failure. */
    @StringRes
    private fun messageFor(inspection: DatabaseBackupManager.Inspection): Int = when (inspection) {
        is DatabaseBackupManager.Inspection.Valid -> R.string.restore_success
        DatabaseBackupManager.Inspection.NotABackup -> R.string.restore_invalid_file
        DatabaseBackupManager.Inspection.ForeignApp -> R.string.restore_foreign_file
        is DatabaseBackupManager.Inspection.TooNew -> R.string.restore_too_new
        DatabaseBackupManager.Inspection.Corrupt -> R.string.restore_corrupt
        is DatabaseBackupManager.Inspection.Unreadable -> R.string.restore_failed
    }
}
