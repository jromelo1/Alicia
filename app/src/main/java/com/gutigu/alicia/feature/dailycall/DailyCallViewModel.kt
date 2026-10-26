package com.gutigu.alicia.feature.dailycall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gutigu.alicia.data.CircleSessionRepository
import com.gutigu.alicia.data.FirestoreRepository
import com.gutigu.alicia.feature.profile.ProfileRepository
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
    private val circleSessionRepository: CircleSessionRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val circleIdFlow = circleSessionRepository.circleIdFlow

    // null = todavía no llegó ni el primer valor real (ni el de Firestore ni el
    // "sin círculo" de respaldo) — distinto de un CallProfile() vacío legítimo.
    // Antes el valor inicial de stateIn era CallProfile() directamente, así que la
    // pantalla no podía distinguir "cargando" de "cargado pero vacío": si el usuario
    // alcanzaba a verla antes de que llegara el snapshot real de Firestore, el
    // formulario se rellenaba una sola vez (por el guard `loadedOnce`) con ese
    // placeholder vacío y se quedaba así aunque luego llegaran los datos reales.
    val profile: StateFlow<CallProfile?> = circleIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(CallProfile()) else firestoreRepository.observeCallProfile(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
            var toSave = updated
            if (acceptConsent && toSave.consentAcceptedAt == null) {
                toSave = toSave.copy(consentAcceptedAt = System.currentTimeMillis())
            }
            // "Quién hizo el registro" se captura sola, nunca se le pregunta al usuario
            // — y solo la primera vez que se guarda algo, para no pisarlo después.
            if (toSave.registeredBy.isBlank()) {
                val role = profileRepository.getProfile()?.name ?: ""
                toSave = toSave.copy(registeredBy = role)
            }
            firestoreRepository.saveCallProfile(circleId, toSave)
        }
    }
}
