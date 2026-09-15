package com.gutigu.alicia.feature.safezones

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.gutigu.alicia.MemberRole
import com.gutigu.alicia.feature.circle.CircleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class GeofenceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: GeofenceRepository,
    private val circleRepository: CircleRepository
) : ViewModel() {

    companion object {
        private const val TAG = "GeofenceViewModel"
    }

    private val _uiState = MutableStateFlow<SafeZoneUiState>(SafeZoneUiState.Loading)
    val uiState: StateFlow<SafeZoneUiState> = _uiState.asStateFlow()

    private val geofencingClient by lazy {
        LocationServices.getGeofencingClient(context)
    }

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    /** Callback de actualizaciones de ubicación en tiempo real */
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            _uiState.update { current ->
                if (current is SafeZoneUiState.Ready) {
                    current.copy(currentLat = loc.latitude, currentLng = loc.longitude)
                } else current
            }
            // La ubicación ya NO se publica en Firestore en tiempo real.
            // Solo se comparte cuando el adulto mayor activa una alerta (SOS o check-in perdido),
            // en ese caso se incluyen las coordenadas en el SMS enviado al círculo.
        }
    }

    // Un solo PendingIntent para todas las geocercas (recomendado por Google)
    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceTransitionReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    init {
        observeZones()
    }

    private fun observeZones() {
        viewModelScope.launch {
            repository.getZones()
                .combine(circleRepository.observeMembers()) { zones, members -> zones to members }
                .collect { (zones, members) ->
                    _uiState.update { current ->
                        val prev = current as? SafeZoneUiState.Ready
                        SafeZoneUiState.Ready(
                            zones = zones,
                            locationPermissionGranted = hasFineLocationPermission(),
                            backgroundPermissionGranted = hasBackgroundPermission(),
                            // conservar la ubicación ya obtenida si existía
                            currentLat = prev?.currentLat,
                            currentLng = prev?.currentLng,
                            hasNoGuardians = members.none { it.role == MemberRole.GUARDIAN }
                        )
                    }
                }
        }
    }

    /** Inicia actualizaciones de ubicación en tiempo real (cada 10 segundos). */
    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (!hasFineLocationPermission()) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L)
            .setMinUpdateIntervalMillis(5_000L)
            .build()
        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    /** Detiene actualizaciones para ahorrar batería cuando la pantalla no es visible. */
    fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onCleared() {
        super.onCleared()
        stopLocationUpdates()
    }

    /** Obtiene la ubicación actual y crea la zona centrada ahí. */
    fun addZoneAtCurrentLocation(name: String, radiusMeters: Float = 150f) {
        if (!hasFineLocationPermission()) {
            Log.w(TAG, "No hay permiso de ubicación para agregar zona")
            return
        }
        viewModelScope.launch {
            val (lat, lng) = fetchCurrentLocation() ?: run {
                Log.e(TAG, "No se pudo obtener la ubicación actual")
                return@launch
            }
            // Actualizar marcador en el mapa con la ubicación recién obtenida
            _uiState.update { current ->
                if (current is SafeZoneUiState.Ready) current.copy(currentLat = lat, currentLng = lng)
                else current
            }
            val zone = SafeZone(
                name = name,
                latitude = lat,
                longitude = lng,
                radiusMeters = radiusMeters
            )
            repository.addZone(zone).onSuccess { savedZone ->
                if (hasBackgroundPermission()) {
                    registerGeofence(savedZone)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchCurrentLocation(): Pair<Double, Double>? {
        return try {
            // Intenta primero con la última ubicación conocida (rápido y sin batería extra)
            val last = fusedLocationClient.lastLocation.await()
            if (last != null) {
                last.latitude to last.longitude
            } else {
                // Si no hay caché, solicita una ubicación fresca (única petición)
                val result = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY, null
                ).await()
                result?.let { it.latitude to it.longitude }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo ubicación: ${e.message}")
            null
        }
    }

    fun removeZone(zoneId: String) {
        viewModelScope.launch {
            unregisterGeofence(zoneId)
            repository.removeZone(zoneId)
        }
    }

    fun toggleZone(zoneId: String, isActive: Boolean) {
        viewModelScope.launch {
            repository.setZoneActive(zoneId, isActive)
            if (isActive) {
                val state = _uiState.value as? SafeZoneUiState.Ready ?: return@launch
                state.zones.find { it.id == zoneId }?.let { zone ->
                    if (hasBackgroundPermission()) registerGeofence(zone)
                }
            } else {
                unregisterGeofence(zoneId)
            }
        }
    }

    /** Llamar después de que el usuario otorgue permisos de ubicación */
    fun onPermissionsUpdated() {
        _uiState.update { current ->
            if (current is SafeZoneUiState.Ready) {
                current.copy(
                    locationPermissionGranted = hasFineLocationPermission(),
                    backgroundPermissionGranted = hasBackgroundPermission()
                )
            } else current
        }
        if (hasBackgroundPermission()) {
            registerAllActiveZones()
        }
    }

    private fun registerAllActiveZones() {
        viewModelScope.launch {
            val state = _uiState.value as? SafeZoneUiState.Ready ?: return@launch
            state.zones.filter { it.isActive }.forEach { registerGeofence(it) }
        }
    }

    private suspend fun registerGeofence(zone: SafeZone) {
        val geofence = Geofence.Builder()
            .setRequestId(zone.id)
            .setCircularRegion(zone.latitude, zone.longitude, zone.radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
            )
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        try {
            geofencingClient.addGeofences(request, geofencePendingIntent).await()
            Log.d(TAG, "Geocerca registrada: ${zone.name} (radio: ${zone.radiusMeters}m)")
        } catch (e: Exception) {
            Log.e(TAG, "Error al registrar geocerca '${zone.name}': ${e.message}")
        }
    }

    private suspend fun unregisterGeofence(zoneId: String) {
        try {
            geofencingClient.removeGeofences(listOf(zoneId)).await()
            Log.d(TAG, "Geocerca eliminada: $zoneId")
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar geocerca $zoneId: ${e.message}")
        }
    }

    private fun hasFineLocationPermission() =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun hasBackgroundPermission() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            hasFineLocationPermission()
        }
}
