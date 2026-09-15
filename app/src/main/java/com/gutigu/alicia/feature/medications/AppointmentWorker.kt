package com.gutigu.alicia.feature.medications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

const val APPOINTMENT_CHANNEL_ID           = "appointment_channel"
private const val APPOINTMENT_CHANNEL_NAME = "Recordatorios de Citas Médicas"
const val APPOINTMENT_NOTIFICATION_BASE    = 4000   // base para IDs de notificación

/**
 * Recordatorio de cita médica — a diferencia de [MedicationWorker], solo muestra una
 * notificación normal: una cita es informativa (avisar y ya), no necesita el flujo de
 * alarma en pantalla completa ni una confirmación de "hecho" como tomar un medicamento.
 */
class AppointmentWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_APPT_ID    = "appt_id"
        const val KEY_DOCTOR_NAME = "doctor_name"
        const val KEY_SPECIALTY  = "specialty"
        const val KEY_LOCATION   = "location"
    }

    override suspend fun doWork(): Result {
        val id         = inputData.getInt(KEY_APPT_ID, -1)
        val doctorName = inputData.getString(KEY_DOCTOR_NAME) ?: return Result.failure()
        val specialty  = inputData.getString(KEY_SPECIALTY) ?: ""
        val location   = inputData.getString(KEY_LOCATION) ?: ""

        showNotification(id, doctorName, specialty, location)
        return Result.success()
    }

    private fun showNotification(id: Int, doctorName: String, specialty: String, location: String) {
        val manager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (manager.getNotificationChannel(APPOINTMENT_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                APPOINTMENT_CHANNEL_ID,
                APPOINTMENT_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Aviso de una cita médica próxima" }
            manager.createNotificationChannel(channel)
        }

        val who = if (specialty.isNotBlank()) "$doctorName — $specialty" else doctorName
        val body = if (location.isNotBlank()) "$who\n📍 $location" else who

        val notification = NotificationCompat.Builder(applicationContext, APPOINTMENT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("🩺 Tienes una cita médica")
            .setContentText(who)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(APPOINTMENT_NOTIFICATION_BASE + id, notification)
    }
}
