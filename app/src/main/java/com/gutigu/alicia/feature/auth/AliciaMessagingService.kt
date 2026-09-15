package com.gutigu.alicia.feature.auth

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.gutigu.alicia.MainActivity
import com.gutigu.alicia.R
import com.gutigu.alicia.data.CircleSessionRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AliciaMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "AliciaFCM"
        const val FCM_CHANNEL_ID = "fcm_push_channel"
        private const val FCM_CHANNEL_NAME = "Alertas Familiares"
        private var notificationId = 9000
    }

    /**
     * Se llama cuando Firebase asigna un nuevo token a este dispositivo.
     * Lo guardamos en Firestore para que los demás miembros del círculo
     * puedan enviar pushes a este dispositivo.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nuevo token FCM: $token")
        saveTokenToFirestore(token)
    }

    /**
     * Se llama cuando llega un mensaje push mientras la app está en primer plano.
     * En segundo plano, el sistema lo muestra automáticamente.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Círculo de Confianza"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: ""

        Log.d(TAG, "Push recibido: $title — $body")
        showLocalNotification(title, body)
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun saveTokenToFirestore(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val circleId = CircleSessionRepository(applicationContext).getCircleId() ?: run {
                Log.w(TAG, "Sin circleId todavía — token no guardado")
                return@launch
            }
            FirebaseFirestore.getInstance()
                .collection("circles")
                .document(circleId)
                .collection("tokens")
                .document(token)
                .set(mapOf("token" to token, "updatedAt" to System.currentTimeMillis()))
                .addOnSuccessListener { Log.d(TAG, "Token guardado en Firestore") }
                .addOnFailureListener { e -> Log.e(TAG, "Error guardando token", e) }
        }
    }

    private fun showLocalNotification(title: String, body: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, FCM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        manager.notify(notificationId++, notification)
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(FCM_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                FCM_CHANNEL_ID,
                FCM_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas del círculo de confianza"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
