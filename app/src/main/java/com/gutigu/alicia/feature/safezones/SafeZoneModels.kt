package com.gutigu.alicia.feature.safezones

// ─────────────────────────────────────────────
//  Domain Models
// ─────────────────────────────────────────────

data class SafeZone(
    val id: String = "",
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Float = 150f,
    val isActive: Boolean = true,
    val notifyOnEnter: Boolean = true,
    val notifyOnExit: Boolean = true
)

enum class GeofenceTransition { ENTER, EXIT, DWELL }

data class GeofenceEvent(
    val zoneId: String,
    val zoneName: String,
    val transition: GeofenceTransition,
    val timestamp: Long = System.currentTimeMillis()
)

// ─────────────────────────────────────────────
//  UI State
// ─────────────────────────────────────────────

sealed class SafeZoneUiState {
    object Loading : SafeZoneUiState()
    data class Ready(
        val zones: List<SafeZone>,
        val lastEvent: GeofenceEvent? = null,
        val locationPermissionGranted: Boolean = false,
        val backgroundPermissionGranted: Boolean = false,
        /** Ubicación actual del dispositivo (null si aún no se obtuvo o sin permiso) */
        val currentLat: Double? = null,
        val currentLng: Double? = null,
        /** true si el círculo no tiene ningún guardián — salir de una zona no le avisaría a nadie. */
        val hasNoGuardians: Boolean = false
    ) : SafeZoneUiState()
    data class Error(val message: String) : SafeZoneUiState()
}
