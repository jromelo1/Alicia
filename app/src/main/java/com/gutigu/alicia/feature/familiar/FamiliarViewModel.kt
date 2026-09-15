package com.gutigu.alicia.feature.familiar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gutigu.alicia.data.CheckInHistoryDao
import com.gutigu.alicia.data.CheckInHistoryEntity
import com.gutigu.alicia.data.CircleSessionRepository
import com.gutigu.alicia.data.FirestoreRepository
import com.gutigu.alicia.data.MedicationEntity
import com.gutigu.alicia.feature.checkin.CheckInRepository
import com.gutigu.alicia.feature.circle.CircleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FamiliarViewModel @Inject constructor(
    private val historyDao: CheckInHistoryDao,
    private val checkInRepository: CheckInRepository,
    private val firestoreRepository: FirestoreRepository,
    private val circleRepository: CircleRepository,
    private val circleSessionRepository: CircleSessionRepository
) : ViewModel() {

    private val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    private val startOfDay: Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    private val endOfDay: Long = startOfDay + 24L * 60 * 60 * 1000

    // ── Flujos de datos ────────────────────────────────────────────────────────

    private val circleIdFlow = circleSessionRepository.circleIdFlow

    private val firestoreCheckInsFlow = circleIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else firestoreRepository.observeCheckIns(id)
    }
    private val panicAlertFlow = circleIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(false) else firestoreRepository.observePanicAlert(id)
    }
    private val appointmentsFlow = circleIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else firestoreRepository.observeAppointments(id)
    }
    // Medicamentos: antes se leían directo de Room (local al dispositivo, nunca
    // llegaba al Familiar en el uso real de dos teléfonos separados). Ahora, igual
    // que Citas médicas, el Adulto Mayor los publica en Firestore.
    private val medicationsFlow = circleIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else firestoreRepository.observeMedications(id)
    }
    private val circleProfileFlow = circleIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(CircleProfileData()) else firestoreRepository.observeCircleProfile(id)
    }

    /** Combina historial local (Room) con Firestore (tiempo real entre dispositivos). */
    private val historyFlow = combine(
        historyDao.getAllHistory(),
        firestoreCheckInsFlow
    ) { roomHistory, firestoreHistory ->
        if (firestoreHistory.isNotEmpty()) {
            val firestoreDates = firestoreHistory.map { it.date }.toSet()
            val roomOnly = roomHistory.filter { it.date !in firestoreDates }
            (firestoreHistory + roomOnly).sortedByDescending { it.scheduledAt }
        } else {
            roomHistory
        }
    }

    /** Combina check-ins + medicinas + nombre local. */
    private data class BaseData(
        val history: List<CheckInHistoryEntity>,
        val meds: List<MedicationEntity>,
        /**
         * Nombre del adulto mayor tal como este familiar lo escribió al unirse con el
         * código — NUNCA el nombre del propio familiar. Es solo un respaldo local
         * mientras llega el nombre real sincronizado desde Firestore (ver [circleProfileFlow]).
         */
        val localElderName: String
    )

    private val baseFlow = combine(
        historyFlow,
        medicationsFlow,
        circleSessionRepository.elderNameFlow
    ) { history, meds, localElderName -> BaseData(history, meds, localElderName) }

    // ── Estado del Dashboard ───────────────────────────────────────────────────

    val dashboardState: StateFlow<DashboardState> = combine(
        baseFlow,
        panicAlertFlow,
        circleProfileFlow,
        appointmentsFlow
    ) { base, panicActive, profile, appointments ->
        val (history, meds, localElderName) = base

        val todayRecord = history.firstOrNull { it.date == today }

        // Calcular estado de bienestar — una alerta de pánico activa siempre gana
        val (wellbeing, alertSource) = when {
            panicActive                            -> WellbeingStatus.ALERT to AlertSource.PANIC
            todayRecord?.status == "RESPONDED"      -> WellbeingStatus.OK to AlertSource.NONE
            todayRecord?.status == "MISSED"         -> WellbeingStatus.ALERT to AlertSource.MISSED_CHECKIN
            todayRecord?.status == "PENDING"        -> WellbeingStatus.PENDING to AlertSource.NONE
            else                                    -> WellbeingStatus.UNKNOWN to AlertSource.NONE
        }

        // Construir la Línea de Vida cronológica
        val activities = buildList {
            // Check-in respondido
            todayRecord?.respondedAt?.let { ts ->
                add(ActivityItem(
                    timestamp   = ts,
                    type        = ActivityType.CHECK_IN,
                    title       = "Check-in de bienestar",
                    description = "Respondió \"¡Estoy bien!\" a las ${formatTime(ts)}"
                ))
            }
            // Medicamentos tomados hoy
            meds.filter { it.takenAt != null && it.takenAt in startOfDay until endOfDay }.forEach { med ->
                add(ActivityItem(
                    timestamp   = med.takenAt!!,
                    type        = ActivityType.MEDICATION,
                    title       = "Tomó ${med.name}",
                    description = "${med.dose} · ${formatTime(med.takenAt)}"
                ))
            }
            // Citas médicas de hoy
            appointments.filter { it.dateTimeMillis in startOfDay until endOfDay }.forEach { appt ->
                val who = if (appt.specialty.isNotBlank()) "${appt.doctorName} · ${appt.specialty}" else appt.doctorName
                add(ActivityItem(
                    timestamp   = appt.dateTimeMillis,
                    type        = ActivityType.APPOINTMENT,
                    title       = "Cita médica: $who",
                    description = appt.location.ifBlank { "Sin lugar registrado" }
                ))
            }
        }.sortedByDescending { it.timestamp }

        val nextAppointment = appointments
            .filter { it.dateTimeMillis >= System.currentTimeMillis() }
            .minByOrNull { it.dateTimeMillis }

        DashboardState(
            // El nombre sincronizado por Firestore (el que el adulto mayor puso en su
            // propio onboarding) es SIEMPRE la fuente de verdad — el nombre local es
            // solo un respaldo mientras llega. Antes esto estaba al revés y mostraba
            // el nombre del propio familiar en vez del adulto mayor que está cuidando.
            userName        = profile.userName.ifBlank { localElderName },
            userPhone       = profile.userPhone,
            wellbeing       = wellbeing,
            alertSource     = alertSource,
            lastCheckInAt   = todayRecord?.respondedAt ?: todayRecord?.scheduledAt,
            todayActivities = activities,
            guardians       = circleRepository.getGuardians(),
            history         = history,
            nextAppointment = nextAppointment
        )
    }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardState()     // userName="" hasta que DataStore emita
        )

    // ── Acción del familiar: enviar nota al adulto mayor ──────────────────────

    /**
     * Publica una nota en Firestore para que Alicia la lea en voz alta al adulto mayor.
     * El Familiar puede enviar avisos sin tener que llamar: citas, recados, recordatorios.
     */
    fun sendNoteToAdult(content: String, authorName: String = "Tu familiar") {
        if (content.isBlank()) return
        viewModelScope.launch {
            val circleId = circleSessionRepository.getCircleId() ?: return@launch
            firestoreRepository.publishFamiliarNote(
                circleId   = circleId,
                content    = content.trim(),
                authorName = authorName.ifBlank { "Tu familiar" }
            )
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun formatTime(ts: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
}
