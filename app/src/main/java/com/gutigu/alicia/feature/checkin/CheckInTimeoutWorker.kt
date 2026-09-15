package com.gutigu.alicia.feature.checkin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.gutigu.alicia.data.AppDatabase
import com.gutigu.alicia.data.CircleSessionRepository
import com.gutigu.alicia.data.FirestoreRepository
import com.gutigu.alicia.feature.circle.FirestoreCircleRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Se ejecuta si el usuario no abrió la app dentro del tiempo configurado.
 * Complementa al countdown de 30 s en pantalla — este cubre el caso
 * en que el teléfono nunca se desbloqueó.
 *
 * Acciones:
 *  1. Actualiza el historial Room → MISSED
 *  2. Muestra notificación ⚠️ local (visible para el Familiar en el mismo dispositivo)
 *  3. Envía SMS a los guardianes del grupo familiar
 */
class CheckInTimeoutWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "CheckInTimeoutWorker"
        private const val CIRCLE_NOTIFICATION_ID = 2002
    }

    override suspend fun doWork(): Result {
        val repo   = DataStoreCheckInRepository(applicationContext)
        val record = repo.getTodayRecord()

        if (record?.status != CheckInStatus.PENDING) {
            Log.d(TAG, "Timeout ignorado — ya respondió o no hay check-in activo")
            return Result.success()
        }

        val now   = System.currentTimeMillis()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now))
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(record.scheduledAt))

        // 1. Actualizar SharedPrefs + historial Room
        repo.recordMissed()
        AppDatabase.getInstance(applicationContext)
            .checkInHistoryDao()
            .markMissed(today)

        Log.d(TAG, "Timeout: marcando MISSED y alertando al círculo")

        // 2. Cancelar la notificación de alarma (ya no tiene sentido)
        val manager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(CHECKIN_NOTIFICATION_ID)

        // 3. Mostrar notificación de alerta para el Familiar (mismo dispositivo)
        val config = repo.getConfig()     // suspend — ok porque estamos en CoroutineWorker
        val firestoreRepository = FirestoreRepository(Firebase.firestore, Firebase.auth, FirebaseMessaging.getInstance())
        val circleRepository = FirestoreCircleRepository(
            firestoreRepository,
            CircleSessionRepository(applicationContext)
        )
        val guardians = circleRepository.getGuardians()
        notifyFamilyMissed(manager, config.userName, timeStr, guardians.map { it.name })

        // 4. Enviar SMS a los guardianes
        sendSmsMissed(config.userName, timeStr, guardians.map { it.phone })

        return Result.success()
    }

    // ─── Notificación local ───────────────────────────────────────────────

    private fun notifyFamilyMissed(
        manager: NotificationManager,
        name: String,
        timeStr: String,
        guardianNames: List<String>
    ) {
        ensureFamilyChannel(manager)

        val guardianLine = if (guardianNames.isNotEmpty())
            "\n\nGuardianes a contactar: ${guardianNames.joinToString(", ")}"
        else
            "\n\nAún no hay guardianes registrados en el círculo."

        val notification = NotificationCompat.Builder(applicationContext, FAMILY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ $name no respondió")
            .setContentText("No respondió el check-in de las $timeStr")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$name no respondió el check-in de las $timeStr.\n" +
                    "Por favor comunícate con él/ella.$guardianLine"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(FAMILY_NOTIFICATION_MISS_ID, notification)
    }

    private fun ensureFamilyChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(FAMILY_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            FAMILY_CHANNEL_ID,
            FAMILY_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Alertas sobre el estado del adulto mayor" }
        manager.createNotificationChannel(channel)
    }

    // ─── SMS ─────────────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun sendSmsMissed(name: String, timeStr: String, phones: List<String>) {
        if (phones.isEmpty()) return
        val message = "⚠️ ALERTA - Círculo de Confianza: $name no respondió " +
                "el check-in de las $timeStr. Por favor comunícate con él/ella."
        val smsManager = SmsManager.getDefault()
        phones.forEach { phone ->
            try {
                smsManager.sendTextMessage(phone, null, message, null, null)
                Log.d(TAG, "SMS de alerta enviado a $phone")
            } catch (e: Exception) {
                Log.e(TAG, "Error al enviar SMS a $phone", e)
            }
        }
    }
}
