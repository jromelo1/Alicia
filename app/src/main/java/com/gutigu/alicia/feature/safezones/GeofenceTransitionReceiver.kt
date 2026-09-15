package com.gutigu.alicia.feature.safezones

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.gutigu.alicia.feature.checkin.CheckInRepository
import com.gutigu.alicia.feature.circle.CircleRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Recibe transiciones de geocercas desde la Geofencing API.
 * Android lo dispara aunque la app esté en segundo plano.
 * En una salida de zona (EXIT) avisa por SMS a los guardianes del círculo —
 * es un evento puntual real, igual que un check-in perdido, no vigilancia continua.
 */
@AndroidEntryPoint
class GeofenceTransitionReceiver : BroadcastReceiver() {

    @Inject lateinit var circleRepository: CircleRepository
    @Inject lateinit var checkInRepository: CheckInRepository

    companion object {
        private const val TAG = "GeofenceReceiver"
        const val CHANNEL_ID = "safezones_channel"
        const val CHANNEL_NAME = "Zonas Seguras"
    }

    override fun onReceive(context: Context, intent: Intent) {
        @Suppress("DEPRECATION")
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return

        if (geofencingEvent.hasError()) {
            val error = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            Log.e(TAG, "Error en geocerca: $error")
            return
        }

        val transition = geofencingEvent.geofenceTransition
        val triggeringGeofences = geofencingEvent.triggeringGeofences ?: return

        triggeringGeofences.forEach { geofence ->
            val message = buildMessage(geofence.requestId, transition)
            if (message != null) {
                Log.d(TAG, message)
                showNotification(context, geofence.requestId, message)
            }
            if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) {
                notifyGuardiansOfExit(geofence.requestId)
            }
        }
    }

    private fun buildMessage(zoneName: String, transition: Int): String? = when (transition) {
        Geofence.GEOFENCE_TRANSITION_ENTER -> "Llegaste a: $zoneName"
        Geofence.GEOFENCE_TRANSITION_EXIT  -> "Saliste de: $zoneName. Tu círculo fue notificado."
        Geofence.GEOFENCE_TRANSITION_DWELL -> "Llevas un rato en: $zoneName"
        else -> null
    }

    @Suppress("DEPRECATION")
    private fun notifyGuardiansOfExit(zoneName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val guardians = circleRepository.getGuardians().filter { it.phone.isNotBlank() }
            if (guardians.isEmpty()) {
                Log.w(TAG, "Sin guardianes — no se envió aviso de salida de zona")
                return@launch
            }
            val userName = checkInRepository.getConfig().userName
            val message = "Círculo de Confianza: $userName salió de la zona segura \"$zoneName\"."
            val smsManager = SmsManager.getDefault()
            guardians.forEach { guardian ->
                try {
                    smsManager.sendTextMessage(guardian.phone, null, message, null, null)
                    Log.d(TAG, "SMS de salida de zona enviado a ${guardian.name}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error enviando SMS de salida de zona a ${guardian.name}", e)
                }
            }
        }
    }

    private fun showNotification(context: Context, tag: String, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alertas de llegada y salida de tus zonas seguras"
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Círculo de Confianza")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        manager.notify(tag.hashCode(), notification)
    }
}
