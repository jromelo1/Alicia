package com.gutigu.alicia.feature.notes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ─────────────────────────────────────────────────────────────────────────────
//  Estado del motor de voz
// ─────────────────────────────────────────────────────────────────────────────

sealed class VoiceInputState {
    /** Micrófono inactivo. */
    object Idle : VoiceInputState()

    /** Escuchando — [partial] se actualiza en tiempo real con cada frase parcial. */
    data class Listening(val partial: String = "") : VoiceInputState()

    /** Transcripción final disponible. */
    data class Result(val text: String) : VoiceInputState()

    /** Error — [message] es legible por el usuario. */
    data class Error(val message: String) : VoiceInputState()
}

// ─────────────────────────────────────────────────────────────────────────────
//  Manager
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Envuelve [SpeechRecognizer] con una interfaz basada en StateFlow.
 *
 * ⚠️  Debe crearse y usarse en el hilo principal (MainThread).
 *     El ciclo de vida está ligado al ViewModel que lo crea.
 */
class VoiceInputManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceInputManager"
    }

    private val _state = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null

    // ── API pública ────────────────────────────────────────────────────────────

    /** Inicia el reconocimiento. Llama desde el hilo principal. */
    fun startListening() {
        if (_state.value is VoiceInputState.Listening) return

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,   RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE,         "es-MX")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,  true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,      1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1_500L)
        }

        recognizer?.startListening(intent)
        Log.d(TAG, "startListening()")
    }

    /** Detiene el reconocimiento anticipadamente. */
    fun stopListening() {
        recognizer?.stopListening()
        Log.d(TAG, "stopListening()")
    }

    /** Vuelve al estado Idle sin guardar nada. */
    fun reset() {
        recognizer?.cancel()
        _state.value = VoiceInputState.Idle
    }

    /** Libera los recursos. Llamar desde onCleared() del ViewModel. */
    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        Log.d(TAG, "destroy()")
    }

    // ── RecognitionListener ────────────────────────────────────────────────────

    private val listener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = VoiceInputState.Listening()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: return
            _state.value = VoiceInputState.Listening(partial)
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.trim() ?: ""
            Log.d(TAG, "Resultado final: \"$text\"")
            _state.value = if (text.isNotBlank()) {
                VoiceInputState.Result(text)
            } else {
                VoiceInputState.Error("No se entendió el audio. Intenta de nuevo.")
            }
        }

        override fun onError(error: Int) {
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH              -> "No se reconoció ninguna palabra"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT        -> "No se detectó voz"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permiso de micrófono denegado"
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT       -> "Sin conexión para el reconocimiento"
                SpeechRecognizer.ERROR_AUDIO                 -> "Error de audio del micrófono"
                SpeechRecognizer.ERROR_SERVER                -> "Error del servidor de voz"
                else                                         -> "Error $error"
            }
            Log.e(TAG, "onError($error): $msg")
            _state.value = VoiceInputState.Error(msg)
        }

        // Callbacks obligatorios sin implementación relevante
        override fun onBeginningOfSpeech()              = Unit
        override fun onEndOfSpeech()                    = Unit
        override fun onRmsChanged(rmsdB: Float)         = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
