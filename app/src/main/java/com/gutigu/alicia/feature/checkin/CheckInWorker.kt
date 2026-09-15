package com.gutigu.alicia.feature.checkin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gutigu.alicia.MainActivity
import com.gutigu.alicia.data.AppDatabase
import com.gutigu.alicia.data.CheckInHistoryEntity
import com.gutigu.alicia.data.checkInDataStore
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Worker diario: emite la alarma de check-in con sonido, muestra saludo según la hora
 * del día y programa el timeout por si el usuario no abre la app.
 */
class CheckInWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "CheckInWorker"
    }

    override suspend fun doWork(): Result {
        val dsPrefs    = applicationContext.checkInDataStore.data.first()
        val userName   = dsPrefs[PREF_CHECKIN_USER_NAME]   ?: "Abuelita"
        val timeoutMin = (dsPrefs[PREF_CHECKIN_TIMEOUT_MIN] ?: 120).toLong()

        val now = System.currentTimeMillis()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now))

        // 1. Registrar check-in del día como PENDIENTE (SharedPrefs + Room history)
        DataStoreCheckInRepository(applicationContext).recordScheduled()
        AppDatabase.getInstance(applicationContext).checkInHistoryDao()
            .insertScheduled(CheckInHistoryEntity(date = today, scheduledAt = now))
        Log.d(TAG, "Alarma de check-in activada — timeout en ${timeoutMin} min")

        // 2. Programar worker de timeout (si el usuario no abre la app)
        val timeoutWork = OneTimeWorkRequestBuilder<CheckInTimeoutWorker>()
            .setInitialDelay(timeoutMin, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            TIMEOUT_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            timeoutWork
        )

        // 3. Mostrar notificación de alarma con sonido
        showAlarmNotification(userName)

        return Result.success()
    }

    private fun showAlarmNotification(userName: String) {
        val manager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        ensureAlarmChannel(manager)

        // Intent para abrir la app directamente al tab de Bienestar
        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", 1) // tab Bienestar
        }
        val openPending = PendingIntent.getActivity(
            applicationContext, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // PendingIntent para la acción "Estoy bien" desde la notificación
        val responseIntent = Intent(ACTION_CHECKIN_OK).apply {
            setPackage(applicationContext.packageName)
        }
        val responsePending = PendingIntent.getBroadcast(
            applicationContext, 0, responseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (title, body) = greetingForCurrentHour(userName)

        val notification = NotificationCompat.Builder(applicationContext, ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$body\n\nToca para abrir la app y confirmar que estás bien."))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openPending)
            .setFullScreenIntent(openPending, true) // despierta pantalla bloqueada
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(android.R.drawable.ic_menu_send, "✓ Estoy bien", responsePending)
            .build()

        manager.notify(CHECKIN_NOTIFICATION_ID, notification)
    }

    private fun greetingForCurrentHour(userName: String): Pair<String, String> {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "Buenos días, $userName 🌅" to "Hola, ¿cómo estás hoy?"
            hour < 19 -> "Buenas tardes, $userName 🌤️"  to "Hola, ¿cómo va tu día?"
            else      -> "Buenas noches, $userName 🌙"   to "Espero hayas tenido un buen día, que pases una buena noche"
        }
    }

    private fun ensureAlarmChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(ALARM_CHANNEL_ID) != null) return

        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            ALARM_CHANNEL_ID,
            ALARM_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alarma diaria del check-in de bienestar"
            setSound(alarmUri, audioAttributes)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 300, 500, 300, 500)
        }
        manager.createNotificationChannel(channel)

        // Mantener también el canal normal para notificaciones de estado
        if (manager.getNotificationChannel(CHECKIN_CHANNEL_ID) == null) {
            val normalChannel = NotificationChannel(
                CHECKIN_CHANNEL_ID,
                CHECKIN_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Notificaciones de bienestar del Círculo" }
            manager.createNotificationChannel(normalChannel)
        }
    }
}
