package com.gutigu.alicia.feature.safezones

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ─────────────────────────────────────────────
//  Interface — el ViewModel solo conoce esto
// ─────────────────────────────────────────────

interface GeofenceRepository {
    fun getZones(): Flow<List<SafeZone>>
    suspend fun addZone(zone: SafeZone): Result<SafeZone>
    suspend fun removeZone(zoneId: String): Result<Unit>
    suspend fun setZoneActive(zoneId: String, isActive: Boolean): Result<Unit>
}

// ─────────────────────────────────────────────
//  Mock — gestiona zonas en memoria
//  Pre-cargadas con Casa y Parque como ejemplo
// ─────────────────────────────────────────────

@Singleton
class MockGeofenceRepository @Inject constructor() : GeofenceRepository {

    private val _zones = MutableStateFlow(
        listOf(
            SafeZone(
                id = "zona-casa",
                name = "Casa",
                latitude = 4.7110,
                longitude = -74.0721,
                radiusMeters = 100f,
                isActive = true
            ),
            SafeZone(
                id = "zona-parque",
                name = "Parque Cercano",
                latitude = 4.7134,
                longitude = -74.0700,
                radiusMeters = 200f,
                isActive = true
            )
        )
    )

    override fun getZones(): Flow<List<SafeZone>> = _zones.asStateFlow()

    override suspend fun addZone(zone: SafeZone): Result<SafeZone> {
        val newZone = zone.copy(id = "zona-${UUID.randomUUID().toString().take(8)}")
        _zones.update { current -> current + newZone }
        return Result.success(newZone)
    }

    override suspend fun removeZone(zoneId: String): Result<Unit> {
        _zones.update { current -> current.filter { it.id != zoneId } }
        return Result.success(Unit)
    }

    override suspend fun setZoneActive(zoneId: String, isActive: Boolean): Result<Unit> {
        _zones.update { current ->
            current.map { if (it.id == zoneId) it.copy(isActive = isActive) else it }
        }
        return Result.success(Unit)
    }
}

// ─────────────────────────────────────────────
//  Módulo Hilt — reemplazar MockGeofenceRepository
//  por FirebaseGeofenceRepository cuando esté listo
// ─────────────────────────────────────────────

@Module
@InstallIn(SingletonComponent::class)
abstract class GeofenceModule {
    @Binds
    abstract fun bindGeofenceRepository(impl: MockGeofenceRepository): GeofenceRepository
}
