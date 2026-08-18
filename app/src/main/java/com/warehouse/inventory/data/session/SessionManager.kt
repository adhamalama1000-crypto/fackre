package com.warehouse.inventory.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.warehouse.inventory.data.local.entity.UserRole
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "session")

/** The current signed-in user, exposed to the whole app. */
data class SessionUser(
    val id: Long,
    val name: String,
    val email: String,
    val role: UserRole
) {
    val isAdmin: Boolean get() = role == UserRole.ADMIN
}

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val USER_ID = longPreferencesKey("user_id")
        val NAME = stringPreferencesKey("user_name")
        val EMAIL = stringPreferencesKey("user_email")
        val ROLE = stringPreferencesKey("user_role")
    }

    val currentUser: Flow<SessionUser?> = context.dataStore.data.map { prefs ->
        val id = prefs[Keys.USER_ID] ?: return@map null
        SessionUser(
            id = id,
            name = prefs[Keys.NAME].orEmpty(),
            email = prefs[Keys.EMAIL].orEmpty(),
            role = runCatching { UserRole.valueOf(prefs[Keys.ROLE].orEmpty()) }
                .getOrDefault(UserRole.VIEWER)
        )
    }

    suspend fun signIn(user: SessionUser) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USER_ID] = user.id
            prefs[Keys.NAME] = user.name
            prefs[Keys.EMAIL] = user.email
            prefs[Keys.ROLE] = user.role.name
        }
    }

    suspend fun signOut() {
        context.dataStore.edit { it.clear() }
    }
}
