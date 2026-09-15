package com.gutigu.alicia.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    init {
        val profile = repository.loadUserProfile()
        val history = repository.loadHistory()
        _state.update { it.copy(userProfile = profile, messages = history) }

        // Si no hay historial previo, Alicia saluda según los intereses del usuario
        if (history.isEmpty()) generateGreeting()
    }

    // ── Entrada del usuario ──────────────────────────────────────────────

    fun onInputChange(text: String) = _state.update { it.copy(inputText = text) }

    fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isBlank() || _state.value.isLoading) return

        val historyBeforeSend = _state.value.messages
        val userMessage       = ChatMessage(role = MessageRole.USER, content = text)
        val optimisticMessages = historyBeforeSend + userMessage

        _state.update {
            it.copy(
                messages  = optimisticMessages,
                inputText = "",
                isLoading = true,
                error     = null
            )
        }

        viewModelScope.launch {
            repository.sendMessage(
                userMessage = text,
                history     = historyBeforeSend,
                profile     = _state.value.userProfile
            ).fold(
                onSuccess = { reply ->
                    val assistant     = ChatMessage(role = MessageRole.ASSISTANT, content = reply)
                    val finalMessages = optimisticMessages + assistant
                    _state.update { it.copy(messages = finalMessages, isLoading = false) }
                    repository.saveHistory(finalMessages)
                },
                onFailure = { e ->
                    // Revertir el mensaje optimista si falló
                    _state.update {
                        it.copy(
                            messages  = historyBeforeSend,
                            isLoading = false,
                            error     = e.message ?: "Error al enviar mensaje"
                        )
                    }
                }
            )
        }
    }

    // ── Intereses del usuario ────────────────────────────────────────────

    fun openInterestsSheet()  = _state.update { it.copy(showInterestsSheet = true) }
    fun closeInterestsSheet() = _state.update { it.copy(showInterestsSheet = false) }

    fun saveInterests(interests: List<String>, name: String) {
        val updated = _state.value.userProfile.copy(name = name, interests = interests)
        repository.saveUserProfile(updated)
        _state.update { it.copy(userProfile = updated, showInterestsSheet = false) }
        // Reiniciar la conversación para que Alicia conozca los nuevos intereses
        clearAndGreet()
    }

    // ── Control de conversación ──────────────────────────────────────────

    fun dismissError() = _state.update { it.copy(error = null) }

    fun clearChat() = clearAndGreet()

    // ── Privados ─────────────────────────────────────────────────────────

    private fun clearAndGreet() {
        _state.update { it.copy(messages = emptyList()) }
        repository.saveHistory(emptyList())
        generateGreeting()
    }

    private fun generateGreeting() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val neighbors = repository.loadNeighborInterests()
            val trigger   = buildTriggerMessage()

            repository.generateGreeting(_state.value.userProfile, neighbors).fold(
                onSuccess = { greeting ->
                    // Guardamos el trigger (oculto) + el saludo de Alicia en el historial
                    val messages = listOf(
                        ChatMessage(role = MessageRole.USER,      content = trigger, hidden = true),
                        ChatMessage(role = MessageRole.ASSISTANT, content = greeting)
                    )
                    _state.update { it.copy(messages = messages, isLoading = false) }
                    repository.saveHistory(messages)
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error     = "No pude conectarme con Alicia. Revisa tu conexión."
                        )
                    }
                }
            )
        }
    }

    /** Mensaje trigger oculto, coherente con lo que el repositorio envía a la API. */
    private fun buildTriggerMessage(): String {
        val interests = _state.value.userProfile.interests
        return buildString {
            append("Por favor inicia la conversación con un saludo cálido según la hora del día")
            if (interests.isNotEmpty()) {
                append(", comenta algo sobre ${interests.first()}")
            }
            append(", y propón un tema de conversación. Sé breve y muy cercano.")
        }
    }
}
