package com.gutigu.alicia.feature.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.gutigu.alicia.data.AppDatabase
import com.gutigu.alicia.data.checkInDataStore
import com.gutigu.alicia.feature.checkin.CheckInWorker
import com.gutigu.alicia.feature.checkin.DAILY_WORK_NAME
import com.gutigu.alicia.feature.checkin.PREF_CHECKIN_ENABLED
import com.gutigu.alicia.feature.checkin.PREF_CHECKIN_HOUR
import com.gutigu.alicia.feature.checkin.PREF_CHECKIN_MINUTE
import com.gutigu.alicia.feature.medications.AppointmentWorker
import com.gutigu.alicia.feature.medications.MedicationWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Se dispara al arrancar el dispositivo y vuelve a encolar los workers de
 * WorkManager que el sistema habría cancelado durante el apagado.
 *
 * No usa Hilt: accede a Room vía [AppDatabase.getInstance] y a las
 * preferencias de check-in directamente a través de SharedPreferences.
 *
 * Usa [goAsync] para completar trabajo asíncrono (consulta Room) sin que
 * Android mate el proceso antes de que termine el `onReceive`.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.d(TAG, "BOOT_COMPLETED recibido — reprogramando alarmas...")

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                rescheduleCheckIn(context)
                rescheduleMedications(context)
                rescheduleAppointments(context)
                Log.d(TAG, "Reprogramación completada.")
            } catch (e: Exception) {
                Log.e(TAG, "Error al reprogramar tras reinicio", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    // ── Check-in diario ───────────────────────────────────────────────────────

    private suspend fun rescheduleCheckIn(context: Context) {
        val prefs   = context.checkInDataStore.data.first()
        val enabled = prefs[PREF_CHECKIN_ENABLED] ?: true

        if (!enabled) {
            Log.d(TAG, "Check-in desactivado — omitido")
            return
        }

        val hour   = prefs[PREF_CHECKIN_HOUR]   ?: 10
        val minute = prefs[PREF_CHECKIN_MINUTE] ?: 0
        val delay  = minutesUntilNext(hour, minute)

        Log.d(TAG, "Check-in → %02d:%02d, espera %d min".format(hour, minute, delay))

        val work = PeriodicWorkRequestBuilder<CheckInWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            work
        )
    }

    // ── Medicamentos ──────────────────────────────────────────────────────────

    private suspend fun rescheduleMedications(context: Context) {
        val dao  = AppDatabase.getInstance(context).medicationDao()
        val meds = dao.getActiveMedications()

        Log.d(TAG, "Reprogramando ${meds.size} medicamento(s)")

        meds.forEach { med ->
            val delay = minutesUntilNext(med.hour, med.minute)
            val data  = workDataOf(
                MedicationWorker.KEY_MED_ID    to med.id,
                MedicationWorker.KEY_MED_NAME  to med.name,
                MedicationWorker.KEY_MED_DOSE  to med.dose,
                MedicationWorker.KEY_PHOTO_URL to (med.photoUrl ?: ""),
                MedicationWorker.KEY_WITH_FOOD to med.withFood,
                MedicationWorker.KEY_NOTE      to med.note
            )
            val work = PeriodicWorkRequestBuilder<MedicationWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "medication_${med.id}",
                ExistingPeriodicWorkPolicy.UPDATE,
                work
            )
            Log.d(TAG, "  💊 ${med.name} → %02d:%02d, espera %d min"
                .format(med.hour, med.minute, delay))
        }
    }

    // ── Citas médicas ─────────────────────────────────────────────────────────

    private suspend fun rescheduleAppointments(context: Context) {
        val now  = System.currentTimeMillis()
        val dao  = AppDatabase.getInstance(context).appointmentDao()
        val appts = dao.getUpcoming(now)

        Log.d(TAG, "Reprogramando ${appts.size} cita(s) médica(s)")

        appts.forEach { appt ->
            val data = workDataOf(
                AppointmentWorker.KEY_APPT_ID     to appt.id,
                AppointmentWorker.KEY_DOCTOR_NAME to appt.doctorName,
                AppointmentWorker.KEY_SPECIALTY   to appt.specialty,
                AppointmentWorker.KEY_LOCATION    to appt.location
            )
            val work = OneTimeWorkRequestBuilder<AppointmentWorker>()
                .setInitialDelay(appt.dateTimeMillis - now, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "appointment_${appt.id}",
                ExistingWorkPolicy.REPLACE,
                work
            )
        }
    }

    // ── Utilidad ──────────────────────────────────────────────────────────────

    /** Minutos hasta la próxima ocurrencia de [hour]:[minute] (mínimo 1 min). */
    private fun minutesUntilNext(hour: Int, minute: Int): Long {
        val now    = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DATE, 1)
        }
        return ((target.timeInMillis - now.timeInMillis) / 60_000L).coerceAtLeast(1L)
    }
}
