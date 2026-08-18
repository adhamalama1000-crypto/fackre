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
 * Which colour scheme the app should use.
 *
 * [SYSTEM] is the default and follows the device setting, which is what most warehouse
 * phones are configured for. The explicit options exist because a device left on automatic
 * day/night switching will flip the app mid-shift, and staff working under fixed
 * fluorescent light generally want one of the two pinned.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    /** Resolves to an actual dark/light decision given the current system setting. */
    fun isDark(systemInDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDark
        LIGHT -> false
        DARK -> true
    }
}

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
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LOW_STOCK_NOTIFICATIONS = booleanPreferencesKey("low_stock_notifications")
        /** Comma-separated product ids already notified about, to suppress repeats. */
        val NOTIFIED_LOW_STOCK = stringPreferencesKey("notified_low_stock_ids")
    }

    /** Falls back to [ThemeMode.SYSTEM] for a missing or unrecognised stored value. */
    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
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
