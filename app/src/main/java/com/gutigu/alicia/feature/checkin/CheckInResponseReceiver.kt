package com.gutigu.alicia.feature.checkin

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Recibe la acción "Estoy bien" desde la notificación de check-in.
 * 1. Cancela el worker de timeout
 * 2. Registra la respuesta (SharedPrefs diarios)
 * 3. Reemplaza la notificación con un mensaje de confirmación
 *
 * Usa [goAsync] porque necesita leer el nombre del usuario desde DataStore (suspend).
 */
class CheckInResponseReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CheckInResponseReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CHECKIN_OK) return

        Log.d(TAG, "Usuario respondió el check-in")
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = DataStoreCheckInRepository(context)

                // 1. Cancelar el timeout
                WorkManager.getInstance(context).cancelUniqueWork(TIMEOUT_WORK_NAME)

                // 2. Registrar respuesta en SharedPrefs diarios
                repo.recordResponse()

                // 3. Reemplazar notificación con confirmación breve
                val config = repo.getConfig()
                showConfirmationNotification(context, config.userName)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showConfirmationNotification(context: Context, userName: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHECKIN_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentTitle("Gracias, $userName")
            .setContentText("Tu círculo ya sabe que estás bien.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setTimeoutAfter(5_000) // se auto-descarta en 5 segundos
            .build()
        manager.notify(CHECKIN_NOTIFICATION_ID, notification)
    }
}
