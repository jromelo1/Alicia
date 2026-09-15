package com.gutigu.alicia.feature.profile

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gutigu.alicia.data.profileDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// ─────────────────────────────────────────────────────────────────────────────
//  Claves del DataStore (privadas a este archivo)
// ─────────────────────────────────────────────────────────────────────────────

private val PREF_PROFILE    = stringPreferencesKey("user_profile")
private val PREF_USER_NAME  = stringPreferencesKey("user_name")

// ─────────────────────────────────────────────────────────────────────────────
//  Repositorio de perfil — respaldado por Preferences DataStore
// ─────────────────────────────────────────────────────────────────────────────

@Singleton
class ProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val store = context.profileDataStore

    // ── Flows reactivos ───────────────────────────────────────────────────────

    /** Emite el perfil del usuario cada vez que cambia. null = sin perfil guardado. */
    val profileFlow: Flow<UserProfile?> = store.data.map { prefs ->
        prefs[PREF_PROFILE]?.let { name ->
            try { UserProfile.valueOf(name) } catch (_: Exception) { null }
        }
    }

    /** Emite el nombre del usuario cada vez que cambia. */
    val userNameFlow: Flow<String> = store.data.map { prefs ->
        prefs[PREF_USER_NAME] ?: ""
    }

    // ── Reads suspending ──────────────────────────────────────────────────────

    /** Lee el perfil actual (una sola lectura, suspending). */
    suspend fun getProfile(): UserProfile? = profileFlow.first()

    /** Lee el nombre del usuario (una sola lectura, suspending). */
    suspend fun getUserName(): String = userNameFlow.first()

    // ── Writes suspending ─────────────────────────────────────────────────────

    /** Guarda el perfil y un nombre legible (suspending). */
    suspend fun saveProfile(profile: UserProfile, userName: String = "") {
        store.edit { prefs ->
            prefs[PREF_PROFILE]   = profile.name
            prefs[PREF_USER_NAME] = userName.ifBlank {
                if (profile == UserProfile.ADULTO_MAYOR) "Abuelita" else "Familiar"
            }
        }
    }

    /** Borra todos los datos del perfil, por ejemplo al cerrar sesión (suspending). */
    suspend fun clearProfile() {
        store.edit { it.clear() }
    }
}
