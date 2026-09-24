package com.gutigu.alicia.feature.dailycall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gutigu.alicia.data.CircleSessionRepository
import com.gutigu.alicia.data.FirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DailyCallViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val circleSessionRepository: CircleSessionRepository
) : ViewModel() {

    private val circleIdFlow = circleSessionRepository.circleIdFlow

    val profile: StateFlow<CallProfile> = circleIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(CallProfile()) else firestoreRepository.observeCallProfile(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CallProfile())

    val history: StateFlow<List<CallRecord>> = circleIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else firestoreRepository.observeCallHistory(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Guarda el perfil completo. [acceptConsent] solo debe llegar en true la primera
     * vez que el usuario marca la casilla de consentimiento — una vez aceptado, no se
     * vuelve a pisar la fecha aunque se sigan editando otros campos del perfil.
     */
    fun saveProfile(updated: CallProfile, acceptConsent: Boolean) {
        viewModelScope.launch {
            val circleId = circleSessionRepository.getCircleId() ?: return@launch
            val withConsent = if (acceptConsent && updated.consentAcceptedAt == null) {
                updated.copy(consentAcceptedAt = System.currentTimeMillis())
            } else {
                updated
            }
            firestoreRepository.saveCallProfile(circleId, withConsent)
        }
    }
}
