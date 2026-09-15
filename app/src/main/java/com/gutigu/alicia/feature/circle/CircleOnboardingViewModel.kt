package com.gutigu.alicia.feature.circle

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gutigu.alicia.MemberRole
import com.gutigu.alicia.data.CircleSessionRepository
import com.gutigu.alicia.data.FirestoreRepository
import com.gutigu.alicia.feature.auth.AuthRepository
import com.gutigu.alicia.feature.checkin.CheckInRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

private const val NETWORK_TIMEOUT_MS = 15_000L
private const val TAG = "CircleOnboardingVM"

sealed class CircleOnboardingState {
    /** Recién entrando a la pantalla — todavía no se disparó ninguna acción. */
    object Idle : CircleOnboardingState()
    /** Hay una operación de verdad en curso (creando círculo, uniéndose con código). */
    object Loading : CircleOnboardingState()
    /**
     * [circleId] es null hasta que el Adulto Mayor cree su círculo o el Familiar se una a uno.
     * [recovered] distingue un círculo recién creado (false) de uno reconectado en un
     * teléfono nuevo tras perder la sesión anónima anterior (true) — la UI muestra copy
     * distinto en cada caso.
     */
    data class Ready(val circleId: String?, val recovered: Boolean = false) : CircleOnboardingState()
    data class Error(val message: String) : CircleOnboardingState()
}

/**
 * Maneja la creación (Adulto Mayor) o vinculación por código (Familiar) del círculo,
 * ya que cada perfil vive en su propio dispositivo con su propia cuenta.
 */
@HiltViewModel
class CircleOnboardingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val firestoreRepository: FirestoreRepository,
    private val circleSessionRepository: CircleSessionRepository,
    private val checkInRepository: CheckInRepository
) : ViewModel() {

    private val _state = MutableStateFlow<CircleOnboardingState>(CircleOnboardingState.Idle)
    val state: StateFlow<CircleOnboardingState> = _state.asStateFlow()

    /** Revisa si este dispositivo ya tiene un círculo cacheado (p. ej. tras reabrir la app). */
    fun checkExistingCircle() {
        viewModelScope.launch {
            _state.value = CircleOnboardingState.Ready(circleSessionRepository.getCircleId())
        }
    }

    /** Adulto Mayor: sesión anónima + crear círculo nuevo (si no existe ya uno). */
    fun ensureCircleAsOwner(userName: String) {
        viewModelScope.launch {
            _state.value = CircleOnboardingState.Loading
            try {
                withTimeout(NETWORK_TIMEOUT_MS) {
                    val cached = circleSessionRepository.getCircleId()
                    if (cached != null) {
                        _state.value = CircleOnboardingState.Ready(cached)
                        return@withTimeout
                    }

                    authRepository.signInAnonymously().onFailure { e ->
                        Log.e(TAG, "signInAnonymously falló", e)
                        _state.value = CircleOnboardingState.Error("No se pudo iniciar tu sesión: ${e.message}")
                        return@withTimeout
                    }

                    // El nombre del onboarding es la fuente de verdad — antes de esto,
                    // CheckInConfig.userName se quedaba en su default ("Vecin@") para siempre,
                    // porque nada lo sincronizaba con el nombre real que el adulto mayor
                    // acaba de escribir. Sin este paso, ese default terminaba sobrescribiendo
                    // el nombre correcto en Firestore la primera vez que tocara cualquier
                    // ajuste de Check-in.
                    if (userName.isNotBlank()) {
                        val currentConfig = checkInRepository.getConfig()
                        if (currentConfig.userName != userName) {
                            checkInRepository.saveConfig(currentConfig.copy(userName = userName))
                        }
                    }

                    firestoreRepository.createCircle(userName).fold(
                        onSuccess = { code ->
                            circleSessionRepository.setCircleId(code)
                            _state.value = CircleOnboardingState.Ready(code)
                        },
                        onFailure = { e ->
                            Log.e(TAG, "createCircle falló", e)
                            _state.value = CircleOnboardingState.Error("No se pudo crear tu círculo: ${e.message}")
                        }
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "ensureCircleAsOwner: tiempo de espera agotado", e)
                _state.value = CircleOnboardingState.Error(
                    "No hay respuesta del servidor. Revisa tu conexión a internet e intenta de nuevo."
                )
            }
        }
    }

    /**
     * Adulto Mayor: recupera un círculo existente por su código en un teléfono nuevo —
     * la sesión anónima del teléfono anterior no sobrevive una reinstalación ni un
     * cambio de equipo, así que esto crea una sesión anónima nueva y la reconecta al
     * mismo círculo. No se pierde nada de lo que ya vive en Firestore (nombre, horario
     * de check-in, historial reciente, miembros); lo único que no se recupera es lo que
     * solo vivía en el teléfono anterior (notas, medicamentos, citas — Room local).
     */
    fun recoverCircleAsElder(code: String, userName: String) {
        viewModelScope.launch {
            _state.value = CircleOnboardingState.Loading
            try {
                withTimeout(NETWORK_TIMEOUT_MS) {
                    authRepository.signInAnonymously().onFailure { e ->
                        Log.e(TAG, "signInAnonymously falló (recuperación)", e)
                        _state.value = CircleOnboardingState.Error("No se pudo iniciar tu sesión: ${e.message}")
                        return@withTimeout
                    }

                    firestoreRepository.recoverElderCircle(code).fold(
                        onSuccess = { circleId ->
                            circleSessionRepository.setCircleId(circleId)
                            if (userName.isNotBlank()) {
                                val currentConfig = checkInRepository.getConfig()
                                if (currentConfig.userName != userName) {
                                    checkInRepository.saveConfig(currentConfig.copy(userName = userName))
                                }
                                firestoreRepository.saveCircleProfile(circleId, userName)
                            }
                            _state.value = CircleOnboardingState.Ready(circleId, recovered = true)
                        },
                        onFailure = { e ->
                            Log.e(TAG, "recoverElderCircle falló", e)
                            _state.value = CircleOnboardingState.Error(e.message ?: "Código inválido")
                        }
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "recoverCircleAsElder: tiempo de espera agotado", e)
                _state.value = CircleOnboardingState.Error(
                    "No hay respuesta del servidor. Revisa tu conexión a internet e intenta de nuevo."
                )
            }
        }
    }

    /**
     * Familiar: se une a un círculo existente con el código de invitación.
     * [elderName] es cómo el propio familiar se refiere al adulto mayor — se guarda
     * solo en este dispositivo como respaldo mientras sincroniza el nombre real desde
     * Firestore; nunca sobrescribe el nombre que el adulto mayor puso en el suyo.
     */
    fun joinWithCode(code: String, memberName: String, memberPhone: String, role: MemberRole, elderName: String) {
        viewModelScope.launch {
            _state.value = CircleOnboardingState.Loading
            try {
                withTimeout(NETWORK_TIMEOUT_MS) {
                    firestoreRepository.joinCircle(code, memberName, memberPhone, role).fold(
                        onSuccess = {
                            val normalized = code.trim().uppercase()
                            circleSessionRepository.setCircleId(normalized)
                            if (elderName.isNotBlank()) circleSessionRepository.setElderName(elderName)
                            _state.value = CircleOnboardingState.Ready(normalized)
                        },
                        onFailure = { e ->
                            Log.e(TAG, "joinCircle falló", e)
                            _state.value = CircleOnboardingState.Error(e.message ?: "Código inválido")
                        }
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "joinWithCode: tiempo de espera agotado", e)
                _state.value = CircleOnboardingState.Error(
                    "No hay respuesta del servidor. Revisa tu conexión a internet e intenta de nuevo."
                )
            }
        }
    }
}
