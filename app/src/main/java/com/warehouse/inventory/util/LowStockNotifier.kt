package com.warehouse.inventory.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.warehouse.inventory.MainActivity
import com.warehouse.inventory.R
import com.warehouse.inventory.data.local.entity.ProductEntity
import com.warehouse.inventory.data.repository.ProductRepository
import com.warehouse.inventory.data.session.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts a notification when a product falls to or below its low-stock threshold.
 *
 * Duplicate suppression works per low-stock *episode*, not per event. The set of product ids
 * already notified about is persisted; a product only produces a notification on the
 * transition into low stock, stays quiet while it remains low no matter how many further
 * movements it sees, and is dropped from the set once it is restocked above the threshold —
 * so a later dip does notify again. Recovered products also get their notification cancelled,
 * so the shade never shows a warning that is no longer true.
 *
 * Called after every stock mutation, which makes the warning immediate rather than waiting
 * for a periodic job.
 */
@Singleton
class LowStockNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val productRepository: ProductRepository,
    private val preferences: AppPreferences
) {

    /**
     * Recomputes low stock and reconciles the notification shade with it.
     * Safe to call often; it is a no-op when nothing crossed a threshold.
     */
    suspend fun refresh() {
        val enabled = runCatching { preferences.lowStockNotificationsEnabled.first() }
            .getOrDefault(true)
        if (!enabled) return
        val lowStock = runCatching { productRepository.getLowStockProducts() }.getOrNull() ?: return
        val currentIds = lowStock.map { it.id }.toSet()
        val alreadyNotified = preferences.notifiedLowStockIds()

        val newlyLow = lowStock.filter { it.id !in alreadyNotified }
        val recovered = alreadyNotified - currentIds

        // Record the new state first: if posting fails (permission revoked, for instance)
        // we still must not re-notify the same products on the next stock change.
        preferences.setNotifiedLowStockIds(currentIds)

        val manager = NotificationManagerCompat.from(context)
        recovered.forEach { manager.cancel(notificationId(it)) }

        if (newlyLow.isEmpty() || !canPostNotifications()) return
        ensureChannel()
        newlyLow.forEach { product -> post(manager, product) }
    }

    /** Clears the suppression set, e.g. after a database restore replaced all the data. */
    suspend fun reset() {
        preferences.setNotifiedLowStockIds(emptySet())
        NotificationManagerCompat.from(context).cancelAll()
    }

    private fun post(manager: NotificationManagerCompat, product: ProductEntity) {
        val unitLabel = context.getString(ProductUnitLabels.stringRes(product.unit))
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId(product.id),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_low_stock)
            .setContentTitle(context.getString(R.string.notification_low_stock_title))
            .setContentText(
                context.getString(
                    R.string.notification_low_stock_text,
                    product.name,
                    product.quantity.toString(),
                    unitLabel
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        runCatching { manager.notify(notificationId(product.id), notification) }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_low_stock),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_low_stock_desc)
            }
        )
    }

    /**
     * From API 33 posting requires a runtime permission; before that it only requires the
     * user not to have switched notifications off for the app.
     */
    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Per-product notification id, so each product replaces only its own warning. */
    private fun notificationId(productId: Long): Int =
        (NOTIFICATION_ID_BASE + productId).toInt()

    private companion object {
        const val CHANNEL_ID = "low_stock"
        const val NOTIFICATION_ID_BASE = 5_000L
    }
}
