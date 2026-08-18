package com.warehouse.inventory.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warehouse.inventory.BuildConfig
import com.warehouse.inventory.MainActivity
import com.warehouse.inventory.R
import com.warehouse.inventory.data.session.ThemeMode
import com.warehouse.inventory.ui.common.AppTopBar
import com.warehouse.inventory.ui.common.ConfirmDialog
import com.warehouse.inventory.ui.common.DetailRow
import com.warehouse.inventory.ui.common.SectionHeader
import com.warehouse.inventory.util.DateUtils

/**
 * Settings: appearance, low-stock alerts, backup/restore and the About section.
 *
 * Backup and restore are admin-only. The section is hidden for viewers *and* refused by
 * [SettingsViewModel], which re-checks the role against the database — hiding the card is
 * only the affordance, not the control.
 */
@Composable
fun SettingsScreen(
    isAdmin: Boolean,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.lowStockNotifications.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Creating the document is the picker's job; we only ever get a Uri we may write to, so
    // the app never needs storage permissions for backups.
    val createBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME)
    ) { uri -> if (uri != null) viewModel.createBackup(uri) }

    val openBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.inspectBackup(uri) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Only enable the setting if the user actually allowed it, otherwise the switch would
        // claim alerts are on while the system silently drops every one of them.
        if (granted) viewModel.setLowStockNotifications(true)
    }

    val message = state.message
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(
                context.getString(message.res, *message.args.toTypedArray())
            )
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.settings),
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (state.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // ------------------------------------------------------ appearance
            SettingsCard {
                SectionHeader(stringResource(R.string.theme))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(stringResource(mode.labelRes())) }
                        )
                    }
                }
            }

            // --------------------------------------------------- notifications
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.low_stock_notifications),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(R.string.low_stock_notifications_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { wantsOn ->
                            if (wantsOn && needsNotificationPermission(context)) {
                                notificationPermissionLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            } else {
                                viewModel.setLowStockNotifications(wantsOn)
                            }
                        }
                    )
                }
                if (notificationsEnabled) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = viewModel::checkLowStockNow,
                        enabled = !state.busy
                    ) { Text(stringResource(R.string.check_low_stock_now)) }
                }
            }

            // ------------------------------------------------ backup / restore
            if (isAdmin) {
                SettingsCard {
                    SectionHeader(stringResource(R.string.backup_restore))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.backup_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                createBackupLauncher.launch(viewModel.suggestedBackupName())
                            },
                            enabled = !state.busy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Backup, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.backup_database))
                        }
                        OutlinedButton(
                            onClick = { openBackupLauncher.launch(RESTORE_MIME_FILTER) },
                            enabled = !state.busy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Restore, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.restore_database))
                        }
                    }
                }
            }

            // ----------------------------------------------------------- about
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_brand_mark),
                        contentDescription = null,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(Modifier.size(14.dp))
                    Column {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.app_tagline),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.about_description),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                DetailRow(
                    label = stringResource(R.string.about_version),
                    value = BuildConfig.VERSION_NAME
                )
                DetailRow(
                    label = stringResource(R.string.database_schema_version),
                    value = viewModel.schemaVersion.toString()
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    // The overwrite is only ever reached through this dialog — the file has been validated
    // but nothing has been written yet.
    state.pendingRestore?.let { pending ->
        ConfirmDialog(
            title = stringResource(R.string.restore_confirm_title),
            message = stringResource(R.string.restore_confirm_message) + "\n\n" +
                stringResource(
                    R.string.backup_details,
                    DateUtils.formatDateTime(pending.createdAt),
                    pending.appVersion,
                    pending.imageCount.toString()
                ),
            destructive = true,
            onConfirm = viewModel::confirmRestore,
            onDismiss = viewModel::cancelRestore
        )
    }

    // The Room instance in the DI graph was closed and its file replaced, so the process has
    // to start over. Not dismissable: continuing in this process would read a stale handle.
    if (state.restoreComplete) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = { Text(stringResource(R.string.restore_success)) },
            confirmButton = {
                TextButton(onClick = { restartApp(context) }) {
                    Text(stringResource(R.string.restart_now))
                }
            }
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

/** True when the runtime notification permission is required and not yet granted. */
private fun needsNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    return ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS
    ) != PackageManager.PERMISSION_GRANTED
}

/**
 * Relaunches the app in a fresh process.
 *
 * Killing the process is the point, not a shortcut: the restored database file has been
 * swapped underneath a closed Room instance held by the Hilt singleton graph, and only a new
 * process is guaranteed to open the new file cleanly.
 */
private fun restartApp(context: Context) {
    val intent = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}

private const val BACKUP_MIME = "application/zip"

/**
 * Zip files are reported as `application/zip` by some document providers and as
 * `application/octet-stream` by others (notably Downloads), so both are accepted; the
 * archive itself is validated before anything is restored regardless of what the picker
 * claimed it was.
 */
private val RESTORE_MIME_FILTER = arrayOf("application/zip", "application/octet-stream")
