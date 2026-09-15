package com.gutigu.alicia

// ─────────────────────────────────────────────
//  UI States
// ─────────────────────────────────────────────

sealed class PanicState {
    object Idle : PanicState()
    data class Countdown(val secondsRemaining: Int) : PanicState()
    object Sending : PanicState()
    data class Active(
        val alertId: String,
        val smsSentCount: Int,
        val smsTotalCount: Int,
        val locationShared: Boolean
    ) : PanicState()
    object Resolved : PanicState()
    data class Error(val message: String) : PanicState()
}

// ─────────────────────────────────────────────
//  Domain Models
// ─────────────────────────────────────────────

data class LatLng(val latitude: Double, val longitude: Double)

data class PanicAlert(
    val userId: String = "",
    val userName: String = "",
    val circleId: String = "",
    val location: LatLng? = null,
    val addressHint: String? = null,
    val batteryLevel: Int = -1,
    val status: AlertStatus = AlertStatus.ACTIVE,
    val respondedBy: List<String> = emptyList(),
    val emergencyInfo: EmergencyInfo? = null,
    val timestamp: Long = 0L,
    val alertId: String = ""
)

data class EmergencyInfo(
    val bloodType: String = "",
    val medications: String = "",
    val allergies: String = "",
    val doctorName: String = "",
    val doctorPhone: String = ""
)

data class CircleMember(
    val userId: String = "",
    val name: String = "",
    val phone: String = "",
    val photoUrl: String? = null,
    val role: MemberRole = MemberRole.MEMBER,
    val notifyOnPanic: Boolean = true,
    val fcmToken: String? = null
)

enum class AlertStatus { ACTIVE, RESOLVED, FALSE_ALARM }
enum class MemberRole { OWNER, GUARDIAN, MEMBER }

// ─────────────────────────────────────────────
//  Result Wrapper
// ─────────────────────────────────────────────

sealed class PanicResult {
    /**
     * El intento de alerta terminó, pero eso no significa que le haya llegado a alguien:
     * [smsSentCount] es cuántos SMS realmente se lograron enviar de [smsTotalCount]
     * contactos con notificación de pánico activada. La UI debe reflejar esto tal cual,
     * no asumir éxito solo porque no hubo una excepción general.
     */
    data class Success(
        val alertId: String,
        val smsSentCount: Int,
        val smsTotalCount: Int,
        val locationShared: Boolean
    ) : PanicResult()
    data class Failure(val error: Throwable) : PanicResult()
}
