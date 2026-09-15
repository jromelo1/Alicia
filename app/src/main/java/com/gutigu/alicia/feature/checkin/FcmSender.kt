package com.gutigu.alicia.feature.checkin

import android.util.Log
import com.gutigu.alicia.BuildConfig
import com.gutigu.alicia.data.FirestoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Envía notificaciones push a todos los tokens del círculo via FCM Legacy HTTP API.
 *
 * Requiere FCM_SERVER_KEY en local.properties:
 *   FCM_SERVER_KEY=AAAAXXXXXXXX:APA91b...
 * (se obtiene en Firebase Console → Configuración del proyecto → Cloud Messaging)
 */
@Singleton
class FcmSender @Inject constructor(
    private val firestoreRepository: FirestoreRepository
) {
    companion object {
        private const val TAG = "FcmSender"
        private const val FCM_URL = "https://fcm.googleapis.com/fcm/send"
    }

    suspend fun notifyFamilyOk(circleId: String, adultName: String, timeStr: String) {
        send(
            circleId = circleId,
            title = "✅ $adultName está bien",
            body = "$adultName respondió el check-in de las $timeStr"
        )
    }

    suspend fun notifyFamilyMissed(circleId: String, adultName: String, timeStr: String) {
        send(
            circleId = circleId,
            title = "⚠️ ALERTA — $adultName no respondió",
            body = "$adultName no contestó el check-in de las $timeStr. Por favor contáctale."
        )
    }

    /**
     * Notifica una alerta de pánico (SOS) activada por el adulto mayor.
     * Es la única alerta que, por guardrail de producto, llega a todo el círculo
     * (guardianes y miembros) — igual que el SMS de [com.gutigu.alicia.Panicrepository].
     */
    suspend fun notifyPanic(circleId: String, adultName: String) {
        send(
            circleId = circleId,
            title = "🆘 $adultName necesita ayuda",
            body = "$adultName activó el botón de pánico. Contáctale de inmediato."
        )
    }

    suspend fun notifyMedicationMissed(circleId: String, adultName: String, medName: String) {
        send(
            circleId = circleId,
            title = "⚠️ $adultName no confirmó su medicamento",
            body = "$adultName no confirmó haber tomado $medName. Por favor contáctale."
        )
    }

    // ── Internals ──────────────────────────────────────────────────────────────

    private suspend fun send(circleId: String, title: String, body: String) {
        val serverKey = BuildConfig.FCM_SERVER_KEY
        if (serverKey.isBlank()) {
            Log.w(TAG, "FCM_SERVER_KEY no configurada — push omitido")
            return
        }

        val tokens = firestoreRepository.getCircleTokens(circleId)
        if (tokens.isEmpty()) {
            Log.w(TAG, "Sin tokens en el círculo — push omitido")
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("registration_ids", JSONArray(tokens))
                    put("notification", JSONObject().apply {
                        put("title", title)
                        put("body", body)
                        put("sound", "default")
                        put("priority", "high")
                    })
                    put("data", JSONObject().apply {
                        put("title", title)
                        put("body", body)
                        put("click_action", "FLUTTER_NOTIFICATION_CLICK")
                    })
                    put("priority", "high")
                    put("content_available", true)
                }

                val url = URL(FCM_URL)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "key=$serverKey")
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 10_000
                    readTimeout = 10_000
                }

                OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "Push enviado a ${tokens.size} dispositivos [$title]")
                } else {
                    Log.e(TAG, "FCM respondió $responseCode para [$title]")
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando push FCM", e)
            }
        }
    }
}
