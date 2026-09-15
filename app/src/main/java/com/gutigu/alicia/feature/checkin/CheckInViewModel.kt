package com.gutigu.alicia.feature.checkin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.gutigu.alicia.data.CheckInHistoryDao
import com.gutigu.alicia.data.CircleSessionRepository
import com.gutigu.alicia.data.FirestoreRepository
import com.gutigu.alicia.feature.circle.CircleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CheckInViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: CheckInRepository,
    private val circleRepository: CircleRepository,
    private val historyDao: CheckInHistoryDao,
    private val fcmSender: FcmSender,
    private val firestoreRepository: FirestoreRepository,
    private val circleSessionRepository: CircleSessionRepository
) : ViewModel() {

    companion object {
        private const val TAG = "CheckInViewModel"
    }

    private val _uiState = MutableStateFlow<CheckInUiState>(CheckInUiState.Loading)
    val uiState: StateFlow<CheckInUiState> = _uiState.asStateFlow()

    private val _countdownSeconds = MutableStateFlow(ALARM_COUNTDOWN_SECONDS)
    val countdownSeconds: StateFlow<Int> = _countdownSeconds.asStateFlow()

    /** Fase actual de la "Confirmación Calmada" (alimenta la UI de la alarma activa). */
    private val _alarmPhase = MutableStateFlow(AlarmPhase.AWAKENING)
    val alarmPhase: StateFlow<AlarmPhase> = _alarmPhase.asStateFlow()

    /**
     * true cuando el usuario acaba de responder — la UI muestra la pantalla de cierre
     * y el TTS dice "¡Perfecto! Seguimos conectados."
     */
    private val _respondedHappily = MutableStateFlow(false)
    val respondedHappily: StateFlow<Boolean> = _respondedHappily.asStateFlow()

    /** true si el círculo no tiene a nadie configurado todavía — activar la alarma no avisaría a nadie. */
    val hasNoContacts: StateFlow<Boolean> = circleRepository.observeMembers()
        .map { it.isEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var countdownJob: Job? = null

    init {
        loadState()
        observeRemoteSchedule()
    }

    private fun loadState() {
        viewModelScope.launch {
            _uiState.update {
                CheckInUiState.Ready(
                    config      = repository.getConfig(),
                    todayRecord = repository.getTodayRecord()
                )
            }
        }
    }

    /**
     * El horario (activar/desactivar, hora, umbral de aviso) ahora lo define el
     * Familiar desde su propia pestaña de Bienestar — este dispositivo solo lo
     * escucha por Firestore, lo guarda localmente y reprograma su alarma.
     */
    private fun observeRemoteSchedule() {
        viewModelScope.launch {
            circleSessionRepository.circleIdFlow
                .flatMapLatest { circleId ->
                    if (circleId == null) emptyFlow() else firestoreRepository.observeCheckInSchedule(circleId)
                }
                .collect { schedule ->
                    val updated = repository.getConfig().copy(
                        isEnabled = schedule.enabled,
                        scheduledHour = schedule.hour,
                        scheduledMinute = schedule.minute,
                        timeoutMinutes = schedule.timeoutMinutes
                    )
                    repository.saveConfig(updated)
                    _uiState.update { current ->
                        if (current is CheckInUiState.Ready) current.copy(config = updated)
                        else CheckInUiState.Ready(config = updated, todayRecord = repository.getTodayRecord())
                    }
                    if (schedule.enabled) schedulePeriodicWork(updated) else cancelAllWork()
                    Log.d(TAG, "Horario de check-in actualizado desde el Familiar: $schedule")
                }
        }
    }

    /** Inicia el countdown de 30s con transiciones de fase automáticas. */
    fun startInAppCountdown() {
        if (countdownJob?.isActive == true) return
        _countdownSeconds.value = ALARM_COUNTDOWN_SECONDS
        _alarmPhase.value = AlarmPhase.AWAKENING
        _respondedHappily.value = false
        countdownJob = viewModelScope.launch {
            for (remaining in ALARM_COUNTDOWN_SECONDS downTo 0) {
                _countdownSeconds.value = remaining
                // Transiciones de fase según tiempo transcurrido
                val elapsed = ALARM_COUNTDOWN_SECONDS - remaining
                _alarmPhase.value = when {
                    elapsed < 5  -> AlarmPhase.AWAKENING
                    elapsed < 20 -> AlarmPhase.MAIN
                    else         -> AlarmPhase.FINAL_WARNING
                }
                delay(1_000)
            }
            onCountdownExpired()
        }
    }

    /** El usuario tocó "¡Estoy bien!" — registrar + notificar a la familia. */
    fun respondNow() {
        countdownJob?.cancel()
        _alarmPhase.value = AlarmPhase.RESPONDED
        _respondedHappily.value = true
        val respondedAt = System.currentTimeMillis()
        repository.recordResponse()
        cancelTimeoutWork()
        dismissAlarmNotification()

        viewModelScope.launch {
            // Guardar en historial Room
            val today = todayDate()
            historyDao.markResponded(today, respondedAt)
            historyDao.pruneOldRecords()

            // Notificar a la familia
            val config = repository.getConfig()
            val timeStr = formatTime(respondedAt)
            notifyFamilyOk(config.userName, timeStr)
            sendFamilySms(
                message = "✅ Círculo de Confianza: ${config.userName} está bien hoy. " +
                        "Respondió el check-in a las $timeStr."
            )

            // Sincronizar con Firestore + enviar push FCM a otros dispositivos
            circleSessionRepository.getCircleId()?.let { circleId ->
                firestoreRepository.publishCheckIn(
                    circleId = circleId,
                    date = today,
                    status = "RESPONDED",
                    scheduledAt = respondedAt,
                    respondedAt = respondedAt
                )
                fcmSender.notifyFamilyOk(circleId, config.userName, timeStr)
            }
        }

        Log.d(TAG, "Usuario confirmó bienestar")
        loadState()
    }

    /** El countdown expiró sin respuesta — alertar a la familia. */
    private fun onCountdownExpired() {
        val now = System.currentTimeMillis()
        repository.recordMissed()
        cancelTimeoutWork()
        dismissAlarmNotification()

        viewModelScope.launch {
            val today = todayDate()
            historyDao.markMissed(today)
            historyDao.pruneOldRecords()

            val config  = repository.getConfig()
            val timeStr = formatTime(now)
            notifyFamilyMissed(config.userName, timeStr)
            sendFamilySms(
                message = "⚠️ ALERTA - Círculo de Confianza: ${config.userName} no respondió " +
                        "el check-in de las $timeStr. Por favor comunícate con él/ella."
            )

            // Sincronizar con Firestore + enviar push FCM a otros dispositivos
            circleSessionRepository.getCircleId()?.let { circleId ->
                firestoreRepository.publishCheckIn(
                    circleId = circleId,
                    date = today,
                    status = "MISSED",
                    scheduledAt = now
                )
                fcmSender.notifyFamilyMissed(circleId, config.userName, timeStr)
            }
        }

        Log.d(TAG, "Countdown expirado — familia alertada")
        loadState()
    }

    /** Actualiza nombre/teléfono propios (el horario ya no se edita aquí, lo define el Familiar). */
    fun updateConfig(config: CheckInConfig) {
        _uiState.update { current ->
            if (current is CheckInUiState.Ready) current.copy(config = config) else current
        }
        viewModelScope.launch {
            repository.saveConfig(config)
            // Sincroniza nombre y teléfono al perfil del círculo en Firestore,
            // para que el dispositivo Familiar pueda mostrar y marcar el número correcto.
            circleSessionRepository.getCircleId()?.let { circleId ->
                firestoreRepository.saveCircleProfile(circleId, config.userName, config.userPhone)
            }
            if (config.isEnabled) schedulePeriodicWork(config)
        }
    }

    fun triggerNow() {
        val work = OneTimeWorkRequestBuilder<CheckInWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "check_in_test",
            ExistingWorkPolicy.REPLACE,
            work
        )
        Log.d(TAG, "Check-in de prueba activado")
    }

    fun refresh() = loadState()

    // ─── Notificaciones locales para el grupo familiar ────────────────────

    private fun ensureFamilyChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(FAMILY_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            FAMILY_CHANNEL_ID,
            FAMILY_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Alertas sobre el estado del adulto mayor" }
        manager.createNotificationChannel(channel)
    }

    private fun notifyFamilyOk(name: String, timeStr: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureFamilyChannel(manager)
        val notification = NotificationCompat.Builder(context, FAMILY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("✅ $name está bien")
            .setContentText("Respondió el check-in a las $timeStr")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(FAMILY_NOTIFICATION_OK_ID, notification)
    }

    private fun notifyFamilyMissed(name: String, timeStr: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureFamilyChannel(manager)
        val notification = NotificationCompat.Builder(context, FAMILY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ $name no respondió")
            .setContentText("No respondió el check-in de las $timeStr")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$name no respondió el check-in de las $timeStr.\n\nPor favor comunícate con él/ella."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(FAMILY_NOTIFICATION_MISS_ID, notification)
    }

    // ─── SMS al grupo familiar ────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private suspend fun sendFamilySms(message: String) {
        val guardians = circleRepository.getGuardians()
        if (guardians.isEmpty()) {
            Log.w(TAG, "Sin guardianes — no se enviaron SMS")
            return
        }
        val smsManager = SmsManager.getDefault()
        guardians.forEach { member ->
            try {
                smsManager.sendTextMessage(member.phone, null, message, null, null)
                Log.d(TAG, "SMS enviado a ${member.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando SMS a ${member.name}", e)
            }
        }
    }

    // ─── WorkManager ──────────────────────────────────────────────────────

    private fun schedulePeriodicWork(config: CheckInConfig) {
        val delay = minutesUntilNextScheduled(config.scheduledHour, config.scheduledMinute)
        Log.d(TAG, "Próxima alarma en ${delay / 60}h ${delay % 60}min")
        val work = PeriodicWorkRequestBuilder<CheckInWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            work
        )
    }

    private fun cancelAllWork() {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(DAILY_WORK_NAME)
            cancelUniqueWork(TIMEOUT_WORK_NAME)
        }
    }

    private fun cancelTimeoutWork() =
        WorkManager.getInstance(context).cancelUniqueWork(TIMEOUT_WORK_NAME)

    private fun dismissAlarmNotification() {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(CHECKIN_NOTIFICATION_ID)
    }

    // ─── Utilidades ───────────────────────────────────────────────────────

    private fun minutesUntilNextScheduled(hour: Int, minute: Int): Long {
        val now    = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DATE, 1)
        }
        return (target.timeInMillis - now.timeInMillis) / 60_000
    }

    private fun todayDate() =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun formatTime(ts: Long) =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
}
