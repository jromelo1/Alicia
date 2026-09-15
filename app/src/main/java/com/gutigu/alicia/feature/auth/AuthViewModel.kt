package com.gutigu.alicia.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val isSignUp: Boolean = false,      // toggle entre Iniciar sesión / Crear cuenta
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val error: String? = null,
    val isAuthenticated: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState(
        isAuthenticated = authRepository.isLoggedIn
    ))
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value, error = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, error = null)
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = value, error = null)
    }

    fun toggleMode() {
        _uiState.value = _uiState.value.copy(
            isSignUp = !_uiState.value.isSignUp,
            error = null
        )
    }

    fun submit() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(error = "Completa todos los campos")
            return
        }
        if (state.isSignUp && state.password != state.confirmPassword) {
            _uiState.value = state.copy(error = "Las contraseñas no coinciden")
            return
        }
        if (state.password.length < 6) {
            _uiState.value = state.copy(error = "La contraseña debe tener al menos 6 caracteres")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            val result = if (state.isSignUp) {
                authRepository.signUp(state.email.trim(), state.password)
            } else {
                authRepository.signIn(state.email.trim(), state.password)
            }
            result.fold(
                onSuccess = { _uiState.value = _uiState.value.copy(isLoading = false, isAuthenticated = true) },
                onFailure = { e ->
                    val msg = when {
                        e.message?.contains("no user record") == true ||
                        e.message?.contains("INVALID_LOGIN_CREDENTIALS") == true ->
                            "Email o contraseña incorrectos"
                        e.message?.contains("email address is already in use") == true ->
                            "Este email ya tiene una cuenta. Inicia sesión."
                        e.message?.contains("badly formatted") == true ->
                            "Email no válido"
                        else -> e.message ?: "Error desconocido"
                    }
                    _uiState.value = _uiState.value.copy(isLoading = false, error = msg)
                }
            )
        }
    }
}
