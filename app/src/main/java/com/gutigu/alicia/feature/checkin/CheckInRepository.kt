package com.gutigu.alicia.feature.checkin

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.gutigu.alicia.data.checkInDataStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

// ─────────────────────────────────────────────
//  Interface
// ─────────────────────────────────────────────

interface CheckInRepository {
    /** Flow reactivo de la configuración (se emite cuando cambia). */
    val configFlow: Flow<CheckInConfig>

    /** Lee la configuración actual (suspending). */
    suspend fun getConfig(): CheckInConfig

    /** Guarda la configuración (suspending). */
    suspend fun saveConfig(config: CheckInConfig)

    // Los registros diarios siguen siendo síncronos (SharedPreferences efímeras)
    fun recordScheduled()
    fun recordResponse()
    fun recordMissed()
    fun getTodayRecord(): CheckInRecord?
}

// ─────────────────────────────────────────────
//  Implementación
//  · Configuración  → Preferences DataStore
//  · Registros diarios → SharedPreferences (ephemeral, también en Room)
// ─────────────────────────────────────────────

@Singleton
class DataStoreCheckInRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : CheckInRepository {

    private val store = context.checkInDataStore
    private val dailyPrefs get() = context.getSharedPreferences(PREFS_CHECKIN, Context.MODE_PRIVATE)
    private val todayKey   get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    // ── Configuración (DataStore) ─────────────────────────────────────────────

    override val configFlow: Flow<CheckInConfig> = store.data.map { prefs ->
        CheckInConfig(
            isEnabled       = prefs[PREF_CHECKIN_ENABLED]     ?: true,
            scheduledHour   = prefs[PREF_CHECKIN_HOUR]        ?: 10,
            scheduledMinute = prefs[PREF_CHECKIN_MINUTE]      ?: 0,
            timeoutMinutes  = prefs[PREF_CHECKIN_TIMEOUT_MIN] ?: 120,
            userName        = prefs[PREF_CHECKIN_USER_NAME]   ?: "Vecin@",
            userPhone       = prefs[PREF_CHECKIN_USER_PHONE]  ?: ""
        )
    }

    override suspend fun getConfig(): CheckInConfig = configFlow.first()

    override suspend fun saveConfig(config: CheckInConfig) {
        store.edit { prefs ->
            prefs[PREF_CHECKIN_ENABLED]     = config.isEnabled
            prefs[PREF_CHECKIN_HOUR]        = config.scheduledHour
            prefs[PREF_CHECKIN_MINUTE]      = config.scheduledMinute
            prefs[PREF_CHECKIN_TIMEOUT_MIN] = config.timeoutMinutes
            prefs[PREF_CHECKIN_USER_NAME]   = config.userName
            prefs[PREF_CHECKIN_USER_PHONE]  = config.userPhone
        }
    }

    // ── Registros diarios (SharedPreferences) ─────────────────────────────────

    override fun recordScheduled() {
        dailyPrefs.edit()
            .putString("${todayKey}_status",       CheckInStatus.PENDING.name)
            .putLong("${todayKey}_scheduled_at",   System.currentTimeMillis())
            .apply()
    }

    override fun recordResponse() {
        dailyPrefs.edit()
            .putString("${todayKey}_status",       CheckInStatus.RESPONDED.name)
            .putLong("${todayKey}_responded_at",   System.currentTimeMillis())
            .apply()
    }

    override fun recordMissed() {
        if (dailyPrefs.getString("${todayKey}_status", null) == CheckInStatus.PENDING.name) {
            dailyPrefs.edit()
                .putString("${todayKey}_status", CheckInStatus.MISSED.name)
                .apply()
        }
    }

    override fun getTodayRecord(): CheckInRecord? {
        val statusStr = dailyPrefs.getString("${todayKey}_status", null) ?: return null
        return CheckInRecord(
            scheduledAt = dailyPrefs.getLong("${todayKey}_scheduled_at", 0L),
            respondedAt = dailyPrefs.getLong("${todayKey}_responded_at", -1L).takeIf { it > 0 },
            status      = CheckInStatus.valueOf(statusStr)
        )
    }
}

// ─────────────────────────────────────────────
//  Módulo Hilt
// ─────────────────────────────────────────────

@Module
@InstallIn(SingletonComponent::class)
abstract class CheckInModule {
    @Binds
    abstract fun bindCheckInRepository(impl: DataStoreCheckInRepository): CheckInRepository
}
