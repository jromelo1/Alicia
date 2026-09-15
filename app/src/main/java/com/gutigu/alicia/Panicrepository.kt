package com.gutigu.alicia

import android.content.Context
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.gutigu.alicia.data.CircleSessionRepository
import com.gutigu.alicia.data.FirestoreRepository
import com.gutigu.alicia.feature.checkin.CheckInRepository
import com.gutigu.alicia.feature.checkin.FcmSender
import com.gutigu.alicia.feature.circle.CircleRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ─────────────────────────────────────────────
//  Interfaz — el ViewModel solo conoce esto
// ─────────────────────────────────────────────

interface PanicRepository {
    suspend fun triggerPanic(): PanicResult
    suspend fun resolveAlert(alertId: String, isFalseAlarm: Boolean)
    suspend fun respondToAlert(alertId: String)
}

// ─────────────────────────────────────────────
//  Implementación (SMS real + ubicación GPS)
// ─────────────────────────────────────────────

@Singleton
class MockPanicRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val checkInRepository: CheckInRepository,   // ← fuente correcta del nombre
    private val firestoreRepository: FirestoreRepository,
    private val fcmSender: FcmSender,
    private val circleRepository: CircleRepository,
    private val circleSessionRepository: CircleSessionRepository
) : PanicRepository {

    companion object {
        private const val TAG = "PanicRepository"
    }

    override suspend fun triggerPanic(): PanicResult {
        return try {
            delay(1500)
            val alertId = "alerta-${UUID.randomUUID().toString().take(8)}"

            // FIX #3 — nombre leído desde el DataStore correcto
            val userName = checkInRepository.getConfig().userName

            // FIX #2 — obtener ubicación GPS real
            val location = getCurrentLocation()

            Log.d(TAG, "Alerta $alertId | usuario=$userName | batería=${getBatteryLevel()}% | ubicación=$location")

            val smsOutcome = sendSmsToCircle(userName, location)
            val circleId = circleSessionRepository.getCircleId()
            if (circleId != null) {
                firestoreRepository.publishPanicAlert(circleId, alertId)
                fcmSender.notifyPanic(circleId, userName)
            } else {
                Log.w(TAG, "Sin circleId — no se pudo publicar la alerta para el Familiar")
            }

            PanicResult.Success(
                alertId = alertId,
                smsSentCount = smsOutcome.sent,
                smsTotalCount = smsOutcome.total,
                locationShared = location != null
            )
        } catch (e: Exception) {
            Log.e(TAG, "triggerPanic falló", e)
            PanicResult.Failure(e)
        }
    }

    // ─── Ubicación GPS ────────────────────────────────────────────────────

    private suspend fun getCurrentLocation(): Location? {
        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
        } catch (e: SecurityException) {
            Log.w(TAG, "Permiso de ubicación no concedido, intentando última ubicación conocida")
            try {
                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                fusedClient.lastLocation.await()
            } catch (ex: Exception) {
                Log.e(TAG, "No se pudo obtener ninguna ubicación: ${ex.message}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo ubicación: ${e.message}")
            null
        }
    }

    // ─── SMS a los miembros del círculo ───────────────────────────────────

    /** Cuántos SMS realmente se lograron entregar al sistema de telefonía, de cuántos se intentaron. */
    private data class SmsOutcome(val sent: Int, val total: Int)

    private suspend fun sendSmsToCircle(userName: String, location: Location?): SmsOutcome {
        val members = circleRepository.getMembers()
            .filter { it.notifyOnPanic && it.phone.isNotBlank() }

        if (members.isEmpty()) {
            Log.w(TAG, "Sin contactos para notificar — agrega miembros al Círculo")
            return SmsOutcome(sent = 0, total = 0)
        }

        // FIX #4 — usar la API correcta según versión de Android
        val smsManager: SmsManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

        if (smsManager == null) {
            Log.e(TAG, "SmsManager no disponible en este dispositivo")
            return SmsOutcome(sent = 0, total = members.size)
        }

        // FIX #2 — incluir link de Google Maps en el SMS
        val locationText = if (location != null) {
            "📍 Ubicacion: https://maps.google.com/?q=${location.latitude},${location.longitude}"
        } else {
            "(Ubicacion no disponible)"
        }

        val message = "🆘 ALERTA Circulo de Confianza: $userName necesita ayuda ahora. " +
            "Por favor contactale de inmediato.\n$locationText"

        var sentCount = 0
        members.forEach { member ->
            try {
                // FIX #4 — dividir en partes si el mensaje supera 160 caracteres
                val parts = smsManager.divideMessage(message)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(member.phone, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(member.phone, null, message, null, null)
                }
                sentCount++
                Log.d(TAG, "SMS enviado a ${member.name} (${member.phone})")
            } catch (e: Exception) {
                // Falta de permiso SEND_SMS, número inválido, sin señal, etc. — no lo escondemos:
                // el resultado agregado (sentCount vs members.size) llega hasta la UI.
                Log.e(TAG, "SMS fallido para ${member.name}: ${e.message}")
            }
        }
        return SmsOutcome(sent = sentCount, total = members.size)
    }

    override suspend fun resolveAlert(alertId: String, isFalseAlarm: Boolean) {
        delay(500)
        val status = if (isFalseAlarm) AlertStatus.FALSE_ALARM else AlertStatus.RESOLVED
        circleSessionRepository.getCircleId()?.let { firestoreRepository.clearPanicAlert(it) }
        Log.d(TAG, "Alerta $alertId marcada como: $status")
    }

    override suspend fun respondToAlert(alertId: String) {
        delay(300)
        Log.d(TAG, "Respuesta a alerta: $alertId")
    }

    private fun getBatteryLevel(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
}

// ─────────────────────────────────────────────
//  Módulo Hilt — cambia MockPanicRepository
//  por FirebasePanicRepository cuando esté listo
// ─────────────────────────────────────────────

@Module
@InstallIn(SingletonComponent::class)
abstract class PanicModule {
    @Binds
    abstract fun bindPanicRepository(impl: MockPanicRepository): PanicRepository
}
