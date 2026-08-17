package com.warehouse.inventory.data.session

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

/**
 * App-level settings and small bits of bookkeeping that are not database rows.
 *
 * Kept in its own DataStore file, separate from the session, so signing out (which clears
 * the session) does not reset the user's preferences.
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val LOW_STOCK_NOTIFICATIONS = booleanPreferencesKey("low_stock_notifications")
        /** Comma-separated product ids already notified about, to suppress repeats. */
        val NOTIFIED_LOW_STOCK = stringPreferencesKey("notified_low_stock_ids")
    }

    val lowStockNotificationsEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.LOW_STOCK_NOTIFICATIONS] ?: true }

    suspend fun setLowStockNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.LOW_STOCK_NOTIFICATIONS] = enabled }
    }

    /**
     * Product ids the user has already been notified about while they were low.
     *
     * This is what keeps a product from re-notifying on every single stock change. An id is
     * removed once the product climbs back above its threshold, so a product that dips low
     * again later does notify again — the suppression is per low-stock episode, not forever.
     */
    suspend fun notifiedLowStockIds(): Set<Long> =
        context.settingsDataStore.data.first()[Keys.NOTIFIED_LOW_STOCK]
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.toSet()
            .orEmpty()

    suspend fun setNotifiedLowStockIds(ids: Set<Long>) {
        context.settingsDataStore.edit {
            it[Keys.NOTIFIED_LOW_STOCK] = ids.joinToString(",")
        }
    }
}
