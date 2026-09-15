package com.gutigu.alicia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gutigu.alicia.feature.circle.CircleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PanicViewModel @Inject constructor(
    private val repository: PanicRepository,
    circleRepository: CircleRepository
) : ViewModel() {

    companion object {
        const val COUNTDOWN_SECONDS = 5
        private const val RESOLVED_DISPLAY_MS = 3000L
    }

    private val _panicState = MutableStateFlow<PanicState>(PanicState.Idle)
    val panicState: StateFlow<PanicState> = _panicState.asStateFlow()

    /** true si el círculo no tiene a nadie configurado todavía — SOS no llegaría a nadie. */
    val hasNoContacts: StateFlow<Boolean> = circleRepository.observeMembers()
        .map { it.isEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var countdownJob: Job? = null
    private var currentAlertId: String? = null

    // ─────────────────────────────────────────────
    //  Arrancar la secuencia: cuenta regresiva → enviar
    // ─────────────────────────────────────────────

    fun startPanicSequence() {
        if (_panicState.value !is PanicState.Idle) return

        countdownJob = viewModelScope.launch {
            for (seconds in COUNTDOWN_SECONDS downTo 1) {
                _panicState.value = PanicState.Countdown(seconds)
                delay(1000)
            }
            sendPanic()
        }
    }

    // ─────────────────────────────────────────────
    //  Cancelar durante la cuenta regresiva
    // ─────────────────────────────────────────────

    fun cancelPanic() {
        countdownJob?.cancel()
        countdownJob = null
        _panicState.value = PanicState.Idle
    }

    // ─────────────────────────────────────────────
    //  Resolver la alerta activa
    // ─────────────────────────────────────────────

    fun resolveAlert(isFalseAlarm: Boolean) {
        val alertId = currentAlertId ?: return
        viewModelScope.launch {
            repository.resolveAlert(alertId, isFalseAlarm)
            currentAlertId = null
            _panicState.value = PanicState.Resolved
            delay(RESOLVED_DISPLAY_MS)
            _panicState.value = PanicState.Idle
        }
    }

    // ─────────────────────────────────────────────
    //  Privado: disparar alertas via repositorio
    // ─────────────────────────────────────────────

    private suspend fun sendPanic() {
        _panicState.value = PanicState.Sending
        when (val result = repository.triggerPanic()) {
            is PanicResult.Success -> {
                currentAlertId = result.alertId
                _panicState.value = PanicState.Active(
                    alertId = result.alertId,
                    smsSentCount = result.smsSentCount,
                    smsTotalCount = result.smsTotalCount,
                    locationShared = result.locationShared
                )
            }
            is PanicResult.Failure -> {
                _panicState.value = PanicState.Error(
                    result.error.message ?: "No se pudo contactar al círculo"
                )
            }
        }
    }
}
