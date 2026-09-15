package com.gutigu.alicia.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val PREF_CIRCLE_ID  = stringPreferencesKey("circle_id")
private val PREF_ELDER_NAME = stringPreferencesKey("elder_name")

/**
 * Caché local de a qué círculo pertenece ESTE dispositivo.
 *
 * Necesaria porque el circleId ya no se puede derivar del uid logueado (adulto mayor
 * y familiar tienen cuentas separadas) y porque componentes que corren fuera de un
 * ViewModel — [com.gutigu.alicia.feature.auth.AliciaMessagingService],
 * [com.gutigu.alicia.feature.safezones.GeofenceTransitionReceiver],
 * [com.gutigu.alicia.feature.checkin.CheckInTimeoutWorker], [com.gutigu.alicia.Panicrepository] —
 * necesitan saber el circleId sin depender de una consulta a Firestore en el momento
 * de una alerta real.
 */
@Singleton
class CircleSessionRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val store = context.circleSessionDataStore

    val circleIdFlow: Flow<String?> = store.data.map { prefs -> prefs[PREF_CIRCLE_ID] }

    suspend fun getCircleId(): String? = circleIdFlow.first()

    suspend fun setCircleId(circleId: String) {
        store.edit { prefs -> prefs[PREF_CIRCLE_ID] = circleId }
    }

    suspend fun clearCircleId() {
        store.edit { prefs -> prefs.remove(PREF_CIRCLE_ID) }
    }

    /**
     * Nombre del adulto mayor tal como lo escribió ESTE familiar al unirse con el
     * código — es solo una etiqueta local de respaldo para este dispositivo, útil
     * antes de que el nombre real (el que el propio adulto mayor puso en su
     * onboarding) termine de sincronizar por Firestore. Nunca se usa para decidir
     * quién es quién — [com.gutigu.alicia.feature.familiar.FamiliarViewModel] siempre
     * prioriza el nombre sincronizado.
     */
    val elderNameFlow: Flow<String> = store.data.map { prefs -> prefs[PREF_ELDER_NAME] ?: "" }

    suspend fun setElderName(name: String) {
        store.edit { prefs -> prefs[PREF_ELDER_NAME] = name }
    }
}
