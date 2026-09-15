package com.gutigu.alicia.feature.medications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

const val ACTION_MEDICATION_TAKEN    = "com.gutigu.alicia.ACTION_MEDICATION_TAKEN"
const val MED_CHANNEL_ID             = "medication_channel"
private const val MED_CHANNEL_NAME   = "Recordatorios de Medicamentos"
const val MED_NOTIFICATION_BASE      = 3000   // base para IDs de notificación

class MedicationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_MED_ID    = "med_id"
        const val KEY_MED_NAME  = "med_name"
        const val KEY_MED_DOSE  = "med_dose"
        const val KEY_PHOTO_URL = "photo_url"
        const val KEY_WITH_FOOD = "with_food"
        const val KEY_NOTE      = "note"
    }

    override suspend fun doWork(): Result {
        val id       = inputData.getInt(KEY_MED_ID, -1)
        val name     = inputData.getString(KEY_MED_NAME) ?: return Result.failure()
        val dose     = inputData.getString(KEY_MED_DOSE) ?: ""
        val photoUrl = inputData.getString(KEY_PHOTO_URL)?.ifBlank { null }
        val withFood = inputData.getBoolean(KEY_WITH_FOOD, false)
        val note     = inputData.getString(KEY_NOTE) ?: ""

        // 1. Mostrar la pantalla de alarma visual (full-screen)
        launchAlarmScreen(id, name, dose, photoUrl, withFood, note)

        // 2. Mostrar también una notificación heads-up como respaldo
        showNotification(id, name, dose)

        return Result.success()
    }

    /** Lanza MedicationAlarmActivity para mostrar la foto de la pastilla. */
    private fun launchAlarmScreen(
        id: Int, name: String, dose: String,
        photoUrl: String?, withFood: Boolean, note: String
    ) {
        val intent = Intent(applicationContext, MedicationAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MedicationAlarmActivity.EXTRA_MED_ID,    id)
            putExtra(MedicationAlarmActivity.EXTRA_MED_NAME,  name)
            putExtra(MedicationAlarmActivity.EXTRA_MED_DOSE,  dose)
            putExtra(MedicationAlarmActivity.EXTRA_PHOTO_URL, photoUrl)
            putExtra(MedicationAlarmActivity.EXTRA_WITH_FOOD, withFood)
            putExtra(MedicationAlarmActivity.EXTRA_NOTE,      note)
        }
        applicationContext.startActivity(intent)
    }

    /** Notificación de respaldo con acción rápida "Tomado". */
    private fun showNotification(id: Int, name: String, dose: String) {
        val manager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (manager.getNotificationChannel(MED_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                MED_CHANNEL_ID,
                MED_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Hora de tomar tus medicamentos" }
            manager.createNotificationChannel(channel)
        }

        val takenIntent = Intent(ACTION_MEDICATION_TAKEN).apply {
            setPackage(applicationContext.packageName)
            putExtra(KEY_MED_ID, id)
        }
        val takenPending = PendingIntent.getBroadcast(
            applicationContext,
            id,
            takenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, MED_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("💊 Hora de tu medicamento")
            .setContentText(if (dose.isNotBlank()) "$name — $dose" else name)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, "✓  Tomado", takenPending)
            .build()

        manager.notify(MED_NOTIFICATION_BASE + id, notification)
    }
}
