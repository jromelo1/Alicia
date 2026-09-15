package com.gutigu.alicia.feature.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gutigu.alicia.data.CircleSessionRepository
import com.gutigu.alicia.data.FirestoreRepository
import com.gutigu.alicia.feature.circle.CircleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * El Familiar/Guardián define aquí el horario del check-in diario del Adulto
 * Mayor — se guarda en Firestore y el dispositivo del Adulto Mayor lo escucha
 * y reprograma su propia alarma (ver CheckInViewModel.observeRemoteSchedule).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FamiliarCheckInViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val circleSessionRepository: CircleSessionRepository,
    circleRepository: CircleRepository
) : ViewModel() {

    val schedule: StateFlow<CheckInScheduleData> = circleSessionRepository.circleIdFlow
        .flatMapLatest { circleId ->
            if (circleId == null) emptyFlow() else firestoreRepository.observeCheckInSchedule(circleId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CheckInScheduleData())

    /** true si el círculo no tiene a nadie que reciba el aviso — activar la alarma no avisaría a nadie. */
    val hasNoContacts: StateFlow<Boolean> = circleRepository.observeMembers()
        .map { it.isEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun saveSchedule(schedule: CheckInScheduleData) {
        viewModelScope.launch {
            circleSessionRepository.getCircleId()?.let { circleId ->
                firestoreRepository.saveCheckInSchedule(circleId, schedule)
            }
        }
    }
}
