package com.gutigu.alicia.feature.medications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gutigu.alicia.data.CircleSessionRepository
import com.gutigu.alicia.feature.checkin.CheckInRepository
import com.gutigu.alicia.feature.checkin.FAMILY_CHANNEL_ID
import com.gutigu.alicia.feature.checkin.FAMILY_CHANNEL_NAME
import com.gutigu.alicia.feature.checkin.FcmSender
import com.gutigu.alicia.feature.circle.CircleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Segundos que tiene el usuario para confirmar antes de que se marque como vencida. */
private const val MEDICATION_TIMEOUT_SECONDS = 120

/** ID de la notificación local de "medicamento sin confirmar" — fuera del rango 3000+id de MedicationWorker. */
private const val MED_FAMILY_NOTIFICATION_ID = 3_900_000

@HiltViewModel
class MedicationAlarmViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val medicationRepository: MedicationRepository,
    private val checkInRepository: CheckInRepository,
    private val circleRepository: CircleRepository,
    private val circleSessionRepository: CircleSessionRepository,
    private val fcmSender: FcmSender
) : ViewModel() {

    companion object {
        private const val TAG = "MedAlarmVM"
    }

    private val _isTaken      = MutableStateFlow(false)
    val isTaken: StateFlow<Boolean> = _isTaken.asStateFlow()

    private val _isExpired    = MutableStateFlow(false)
    val isExpired: StateFlow<Boolean> = _isExpired.asStateFlow()

    private val _countdown    = MutableStateFlow(MEDICATION_TIMEOUT_SECONDS)
    val countdown: StateFlow<Int> = _countdown.asStateFlow()

    private var countdownJob: Job? = null

    // ── API pública ───────────────────────────────────────────────────────────

    /** Inicia la cuenta regresiva de 2 min. Llamar una sola vez desde la pantalla. */
    fun startCountdown(medId: Int, medName: String) {
        if (countdownJob?.isActive == true) return
        countdownJob = viewModelScope.launch {
            for (remaining in MEDICATION_TIMEOUT_SECONDS downTo 0) {
                _countdown.value = remaining
                delay(1_000)
            }
            onTimeout(medId, medName)
        }
    }

    /** El usuario tocó "Ya la tomé". */
    fun confirmTaken(medId: Int) {
        if (_isTaken.value) return
        countdownJob?.cancel()
        _isTaken.value = true
        viewModelScope.launch {
            medicationRepository.markTaken(medId)
            Log.d(TAG, "Medicamento $medId confirmado como tomado")
        }
    }

    // ── Internos ──────────────────────────────────────────────────────────────

    private fun onTimeout(medId: Int, medName: String) {
        _isExpired.value = true
        viewModelScope.launch {
            val adultName = checkInRepository.getConfig().userName
            notifyFamily(adultName, medName)
        }
        Log.d(TAG, "Med $medId ($medName) no confirmado — alertando al círculo")
    }

    /** Alerta real al círculo: SMS a los guardianes + push FCM, igual que el check-in perdido. */
    private suspend fun notifyFamily(adultName: String, medName: String) {
        val guardians = circleRepository.getGuardians()
        if (guardians.isEmpty()) {
            Log.w(TAG, "Sin guardianes en el círculo — no se pudo alertar sobre $medName sin confirmar")
        } else {
            sendFamilySms(adultName, medName, guardians.map { it.phone })
            showFamilyNotification(adultName, medName)
        }

        circleSessionRepository.getCircleId()?.let { circleId ->
            fcmSender.notifyMedicationMissed(circleId, adultName, medName)
        }
    }

    @Suppress("DEPRECATION")
    private fun sendFamilySms(adultName: String, medName: String, phones: List<String>) {
        val message = "⚠️ ALERTA - Círculo de Confianza: $adultName no confirmó haber tomado " +
                "$medName. Por favor comunícate con él/ella."
        val smsManager = SmsManager.getDefault()
        phones.filter { it.isNotBlank() }.forEach { phone ->
            try {
                smsManager.sendTextMessage(phone, null, message, null, null)
                Log.d(TAG, "SMS de medicamento sin confirmar enviado a $phone")
            } catch (e: Exception) {
                Log.e(TAG, "Error al enviar SMS a $phone", e)
            }
        }
    }

    private fun showFamilyNotification(adultName: String, medName: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(FAMILY_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                FAMILY_CHANNEL_ID,
                FAMILY_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Alertas sobre el estado del adulto mayor" }
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, FAMILY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ $adultName no confirmó su medicamento")
            .setContentText("No confirmó haber tomado $medName")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$adultName no confirmó haber tomado $medName a tiempo.\n" +
                    "Por favor comunícate con él/ella."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(MED_FAMILY_NOTIFICATION_ID, notification)
    }
}
