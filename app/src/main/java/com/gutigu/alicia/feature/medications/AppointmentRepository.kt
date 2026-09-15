package com.gutigu.alicia.feature.medications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.gutigu.alicia.data.AppointmentDao
import com.gutigu.alicia.data.AppointmentEntity
import com.gutigu.alicia.data.CircleSessionRepository
import com.gutigu.alicia.data.FirestoreRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppointmentRepository @Inject constructor(
    private val dao: AppointmentDao,
    @ApplicationContext private val context: Context,
    private val firestoreRepository: FirestoreRepository,
    private val circleSessionRepository: CircleSessionRepository
) {
    val appointments: Flow<List<AppointmentEntity>> = dao.getAllAppointments()

    suspend fun addAppointment(
        doctorName: String,
        specialty: String,
        location: String,
        note: String,
        dateTimeMillis: Long
    ): AppointmentEntity {
        val entity = AppointmentEntity(
            doctorName = doctorName,
            specialty = specialty,
            location = location,
            note = note,
            dateTimeMillis = dateTimeMillis
        )
        val id = dao.insertAppointment(entity).toInt()
        val saved = entity.copy(id = id)
        scheduleReminder(saved)
        // El familiar no ve nada del círculo con solo Room (vive en el dispositivo del
        // Adulto Mayor) — sin esto la cita quedaría invisible para la familia, igual que
        // ya pasa hoy con Medicamentos y Notas (bug conocido, fuera de este cambio).
        circleSessionRepository.getCircleId()?.let { circleId ->
            firestoreRepository.publishAppointment(circleId, saved)
        }
        return saved
    }

    suspend fun deleteAppointment(appointment: AppointmentEntity) {
        dao.deleteAppointment(appointment)
        cancelReminder(appointment.id)
        circleSessionRepository.getCircleId()?.let { circleId ->
            firestoreRepository.deleteAppointmentRemote(circleId, appointment.id)
        }
    }

    fun scheduleReminder(appt: AppointmentEntity) {
        // La cita ya pasó (ej. se está reprogramando tras un reinicio) — no hay nada que avisar.
        val delayMs = appt.dateTimeMillis - System.currentTimeMillis()
        if (delayMs <= 0) return

        val data = workDataOf(
            AppointmentWorker.KEY_APPT_ID     to appt.id,
            AppointmentWorker.KEY_DOCTOR_NAME to appt.doctorName,
            AppointmentWorker.KEY_SPECIALTY   to appt.specialty,
            AppointmentWorker.KEY_LOCATION    to appt.location
        )
        val work = OneTimeWorkRequestBuilder<AppointmentWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "appointment_${appt.id}",
            ExistingWorkPolicy.REPLACE,
            work
        )
    }

    private fun cancelReminder(id: Int) {
        WorkManager.getInstance(context).cancelUniqueWork("appointment_$id")
    }
}
