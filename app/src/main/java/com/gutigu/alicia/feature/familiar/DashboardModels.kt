package com.gutigu.alicia.feature.familiar

import androidx.compose.ui.graphics.Color
import com.gutigu.alicia.CircleMember
import com.gutigu.alicia.data.AppointmentEntity
import com.gutigu.alicia.data.CheckInHistoryEntity

// ─────────────────────────────────────────────────────────────────────────────
//  Estado de bienestar del adulto mayor (alimenta el Hero del dashboard)
// ─────────────────────────────────────────────────────────────────────────────

enum class WellbeingStatus {
    /** ✅ Respondió el check-in — todo en orden */
    OK,
    /** 🕐 La alarma sonó pero aún no ha respondido */
    PENDING,
    /** ⚠️ No respondió, o activó el botón de pánico — alerta activa */
    ALERT,
    /** ⬜ Sin datos para hoy todavía */
    UNKNOWN
}

/** De dónde viene una alerta activa — cambia el mensaje mostrado en modo rojo. */
enum class AlertSource { NONE, MISSED_CHECKIN, PANIC }

/** Nombre y teléfono del adulto mayor, sincronizados vía Firestore. */
data class CircleProfileData(
    val userName: String = "",
    val userPhone: String = ""
)

// ─────────────────────────────────────────────────────────────────────────────
//  Ítem de la Línea de Vida
// ─────────────────────────────────────────────────────────────────────────────

// NOTE fue removido a propósito: las notas del Adulto Mayor son un diario privado
// ("no se comparte con tu familia", spec §8.2) — nunca deben alimentar este dashboard.
enum class ActivityType { CHECK_IN, MEDICATION, APPOINTMENT }

data class ActivityItem(
    val timestamp: Long,
    val type: ActivityType,
    val title: String,
    val description: String
)

// ─────────────────────────────────────────────────────────────────────────────
//  Estado completo del Dashboard Familiar
// ─────────────────────────────────────────────────────────────────────────────

data class DashboardState(
    val userName: String = "",
    /** Teléfono del adulto mayor — el botón de llamada debe marcar este número. */
    val userPhone: String = "",
    val wellbeing: WellbeingStatus = WellbeingStatus.UNKNOWN,
    /** Origen de la alerta activa (ninguna, check-in perdido, o pánico/SOS). */
    val alertSource: AlertSource = AlertSource.NONE,
    /** Hora del último check-in (epoch ms) — null si no hay registro */
    val lastCheckInAt: Long? = null,
    /** Actividades cronológicas del día para la Línea de Vida */
    val todayActivities: List<ActivityItem> = emptyList(),
    /** Guardianes del círculo (para coordinarse; no son el número principal a marcar) */
    val guardians: List<CircleMember> = emptyList(),
    /** Historial completo de check-ins (para la sección expandida) */
    val history: List<CheckInHistoryEntity> = emptyList(),
    /** Próxima cita médica futura (la más cercana), null si no hay ninguna programada. */
    val nextAppointment: AppointmentEntity? = null
)

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers de color y texto según el estado
// ─────────────────────────────────────────────────────────────────────────────

fun WellbeingStatus.heroColor(): Color = when (this) {
    WellbeingStatus.OK      -> Color(0xFF27AE60)
    WellbeingStatus.PENDING -> Color(0xFFE67E22)
    WellbeingStatus.ALERT   -> Color(0xFFE74C3C)
    WellbeingStatus.UNKNOWN -> Color(0xFF3A5068)
}

fun WellbeingStatus.heroEmoji(): String = when (this) {
    WellbeingStatus.OK      -> "✅"
    WellbeingStatus.PENDING -> "🕐"
    WellbeingStatus.ALERT   -> "⚠️"
    WellbeingStatus.UNKNOWN -> "❓"
}
