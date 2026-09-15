package com.gutigu.alicia.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.privacyConsentDataStore by preferencesDataStore(name = "privacy_consent")
private val PREF_ACCEPTED_VERSION = stringPreferencesKey("accepted_version")

/**
 * Sube este número cada vez que cambie el texto del aviso de privacidad de forma
 * material — vuelve a pedir consentimiento a todos los usuarios existentes.
 */
const val CURRENT_PRIVACY_POLICY_VERSION = "2026-08-v1"

@Singleton
class PrivacyConsentRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val store = context.privacyConsentDataStore

    val hasAcceptedCurrentFlow: Flow<Boolean> = store.data.map { prefs ->
        prefs[PREF_ACCEPTED_VERSION] == CURRENT_PRIVACY_POLICY_VERSION
    }

    suspend fun hasAcceptedCurrent(): Boolean = hasAcceptedCurrentFlow.first()

    suspend fun acceptCurrent() {
        store.edit { prefs -> prefs[PREF_ACCEPTED_VERSION] = CURRENT_PRIVACY_POLICY_VERSION }
    }
}
