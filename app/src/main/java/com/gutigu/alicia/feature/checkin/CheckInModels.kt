package com.gutigu.alicia.feature.checkin

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

// ─────────────────────────────────────────────
//  Constantes compartidas entre Worker, Receiver y ViewModel
// ─────────────────────────────────────────────

const val DAILY_WORK_NAME   = "check_in_daily"
const val TIMEOUT_WORK_NAME = "check_in_timeout"

const val CHECKIN_NOTIFICATION_ID = 2001
const val CHECKIN_CHANNEL_ID      = "checkin_channel"
const val CHECKIN_CHANNEL_NAME    = "Check-in de Bienestar"

// Canal de alta prioridad para la alarma (sonido + pantalla completa)
const val ALARM_CHANNEL_ID   = "checkin_alarm_channel"
const val ALARM_CHANNEL_NAME = "Alarma de Bienestar"

// Segundos que el usuario tiene para responder una vez que abre la app
const val ALARM_COUNTDOWN_SECONDS = 30

// Canal y notificaciones para el grupo familiar (confirmación y alerta)
const val FAMILY_CHANNEL_ID           = "family_notifications"
const val FAMILY_CHANNEL_NAME         = "Notificaciones Familiares"
const val FAMILY_NOTIFICATION_OK_ID   = 2010   // adulto mayor respondió ✅
const val FAMILY_NOTIFICATION_MISS_ID = 2011   // adulto mayor no respondió ⚠️

const val ACTION_CHECKIN_OK = "com.gutigu.alicia.ACTION_CHECKIN_OK"

// SharedPreferences — solo para registros diarios (recordScheduled/Response/Missed)
const val PREFS_CHECKIN    = "checkin_prefs"

// DataStore Preferences keys — configuración del check-in
// Usadas por: CheckInRepository, CheckInWorker, BootReceiver
val PREF_CHECKIN_ENABLED     = booleanPreferencesKey("enabled")
val PREF_CHECKIN_HOUR        = intPreferencesKey("hour")
val PREF_CHECKIN_MINUTE      = intPreferencesKey("minute")
val PREF_CHECKIN_TIMEOUT_MIN = intPreferencesKey("timeout_minutes")
val PREF_CHECKIN_USER_NAME   = stringPreferencesKey("user_name")
val PREF_CHECKIN_USER_PHONE  = stringPreferencesKey("user_phone")


// ─────────────────────────────────────────────
//  Domain Models
// ─────────────────────────────────────────────

data class CheckInConfig(
    val isEnabled: Boolean = true,
    val scheduledHour: Int = 10,
    val scheduledMinute: Int = 0,
    val timeoutMinutes: Int = 120,   // después de cuánto se notifica al círculo
    val userName: String = "Vecin@",
    /** Teléfono propio — para que la familia pueda llamarte directamente en una alerta. */
    val userPhone: String = ""
)

/**
 * Horario del check-in tal como lo define el Familiar/Guardián, sincronizado
 * por Firestore (circles/{circleId}). El dispositivo del Adulto Mayor solo lo
 * lee y reprograma su alarma local — no lo edita.
 */
data class CheckInScheduleData(
    val enabled: Boolean = false,
    val hour: Int = 10,
    val minute: Int = 0,
    val timeoutMinutes: Int = 120
)

enum class CheckInStatus { PENDING, RESPONDED, MISSED }

/**
 * Fases de la "Confirmación Calmada":
 *   AWAKENING     — 0-5s:   vibración suave, ámbar, Alicia saluda
 *   MAIN          — 5-20s:  botón gigante ocupa el 60%, latido animado
 *   FINAL_WARNING — 20-30s: cuenta regresiva grande, Alicia advierte
 *   RESPONDED     — botón presionado: pantalla verde, mensaje de cierre
 */
enum class AlarmPhase { AWAKENING, MAIN, FINAL_WARNING, RESPONDED }

data class CheckInRecord(
    val scheduledAt: Long = 0L,
    val respondedAt: Long? = null,
    val status: CheckInStatus = CheckInStatus.PENDING
)

// ─────────────────────────────────────────────
//  UI State
// ─────────────────────────────────────────────

sealed class CheckInUiState {
    object Loading : CheckInUiState()
    data class Ready(
        val config: CheckInConfig,
        val todayRecord: CheckInRecord? = null
    ) : CheckInUiState()
}
