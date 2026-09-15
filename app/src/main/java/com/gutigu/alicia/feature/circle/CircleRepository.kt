package com.gutigu.alicia.feature.circle

import com.gutigu.alicia.CircleMember
import com.gutigu.alicia.MemberRole
import com.gutigu.alicia.data.CircleSessionRepository
import com.gutigu.alicia.data.FirestoreRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

// ─────────────────────────────────────────────
//  Interface pública
// ─────────────────────────────────────────────

interface CircleRepository {
    /** Flujo reactivo de miembros del círculo (vacío hasta que este dispositivo tenga circleId). */
    fun observeMembers(): Flow<List<CircleMember>>

    suspend fun getMembers(): List<CircleMember>
    suspend fun getGuardians(): List<CircleMember>   // GUARDIAN — todas las notificaciones
    suspend fun addMember(member: CircleMember): CircleMember
    suspend fun updateMember(member: CircleMember)
    suspend fun removeMember(userId: String)
}

// ─────────────────────────────────────────────
//  Implementación — respaldada por Firestore, resuelve el circleId de este
//  dispositivo vía CircleSessionRepository antes de leer/escribir.
// ─────────────────────────────────────────────

@Singleton
class FirestoreCircleRepository @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val circleSessionRepository: CircleSessionRepository
) : CircleRepository {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeMembers(): Flow<List<CircleMember>> =
        circleSessionRepository.circleIdFlow.flatMapLatest { circleId ->
            if (circleId == null) emptyFlow() else firestoreRepository.observeCircleMembers(circleId)
        }

    override suspend fun getMembers(): List<CircleMember> {
        val circleId = circleSessionRepository.getCircleId() ?: return emptyList()
        return firestoreRepository.getCircleMembers(circleId)
    }

    override suspend fun getGuardians(): List<CircleMember> =
        getMembers().filter { it.role == MemberRole.GUARDIAN }

    override suspend fun addMember(member: CircleMember): CircleMember {
        val circleId = circleSessionRepository.getCircleId() ?: return member
        return firestoreRepository.addCircleMember(circleId, member)
    }

    override suspend fun updateMember(member: CircleMember) {
        val circleId = circleSessionRepository.getCircleId() ?: return
        firestoreRepository.updateCircleMember(circleId, member)
    }

    override suspend fun removeMember(userId: String) {
        val circleId = circleSessionRepository.getCircleId() ?: return
        firestoreRepository.removeCircleMember(circleId, userId)
    }
}

// ─────────────────────────────────────────────
//  Módulo Hilt
// ─────────────────────────────────────────────

@Module
@InstallIn(SingletonComponent::class)
abstract class CircleModule {
    @Binds
    abstract fun bindCircleRepository(impl: FirestoreCircleRepository): CircleRepository
}
