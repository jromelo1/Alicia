package com.gutigu.alicia.feature.medications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.gutigu.alicia.data.CircleSessionRepository
import com.gutigu.alicia.data.FirestoreRepository
import com.gutigu.alicia.data.MedicationDao
import com.gutigu.alicia.data.MedicationEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationRepository @Inject constructor(
    private val dao: MedicationDao,
    @ApplicationContext private val context: Context,
    private val firestoreRepository: FirestoreRepository,
    private val circleSessionRepository: CircleSessionRepository
) {
    val medications: Flow<List<MedicationEntity>> = dao.getAllMedications()

    suspend fun addMedication(
        name: String,
        dose: String,
        hour: Int,
        minute: Int,
        withFood: Boolean = false,
        note: String = ""
    ): MedicationEntity {
        val entity = MedicationEntity(
            name = name, dose = dose,
            hour = hour, minute = minute,
            withFood = withFood, note = note
        )
        val id = dao.insertMedication(entity).toInt()
        val saved = entity.copy(id = id)
        scheduleReminder(saved)
        publishRemote(saved)
        return saved
    }

    /**
     * Actualiza la URL de la foto y reprograma el worker para que incluya
     * la URL actualizada en sus datos de entrada.
     */
    suspend fun updatePhotoUrl(medId: Int, url: String) {
        dao.updatePhotoUrl(medId, url)
        // Re-schedular con la URL ya incluida en el WorkData
        val updated = dao.getById(medId)
        if (updated != null) {
            scheduleReminder(updated)
            publishRemote(updated)
        }
    }

    suspend fun deleteMedication(med: MedicationEntity) {
        dao.deleteMedication(med)
        cancelReminder(med.id)
        circleSessionRepository.getCircleId()?.let { circleId ->
            firestoreRepository.deleteMedicationRemote(circleId, med.id)
        }
    }

    suspend fun toggleActive(med: MedicationEntity) {
        val updated = med.copy(isActive = !med.isActive)
        dao.updateMedication(updated)
        if (updated.isActive) scheduleReminder(updated) else cancelReminder(updated.id)
        publishRemote(updated)
    }

    /**
     * El Adulto Mayor tocó "Ya la tomé" — desde la alarma en pantalla completa
     * ([com.gutigu.alicia.feature.medications.MedicationAlarmViewModel]) o desde la
     * acción rápida de la notificación de respaldo ([MedicationResponseReceiver]).
     * Punto único para que ambos caminos reflejen el mismo dato al Familiar.
     */
    suspend fun markTaken(medId: Int) {
        dao.markTaken(medId, System.currentTimeMillis())
        dao.getById(medId)?.let { publishRemote(it) }
    }

    private suspend fun publishRemote(med: MedicationEntity) {
        circleSessionRepository.getCircleId()?.let { circleId ->
            firestoreRepository.publishMedication(circleId, med)
        }
    }

    private fun scheduleReminder(med: MedicationEntity) {
        if (!med.isActive) return
        val delay = minutesUntilNext(med.hour, med.minute)
        val data  = workDataOf(
            MedicationWorker.KEY_MED_ID       to med.id,
            MedicationWorker.KEY_MED_NAME     to med.name,
            MedicationWorker.KEY_MED_DOSE     to med.dose,
            MedicationWorker.KEY_PHOTO_URL    to (med.photoUrl ?: ""),
            MedicationWorker.KEY_WITH_FOOD    to med.withFood,
            MedicationWorker.KEY_NOTE         to med.note
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
    }

    private fun cancelReminder(id: Int) {
        WorkManager.getInstance(context).cancelUniqueWork("medication_$id")
    }

    private fun minutesUntilNext(hour: Int, minute: Int): Long {
        val now    = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DATE, 1)
        }
        return (target.timeInMillis - now.timeInMillis) / 60_000
    }
}
