package com.gutigu.alicia.feature.notes

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gutigu.alicia.data.CircleSessionRepository
import com.gutigu.alicia.data.FirestoreRepository
import com.gutigu.alicia.data.NoteDao
import com.gutigu.alicia.data.NoteEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
//  Estado de la nota pendiente de confirmación
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Nota transcrita (voz) o manual que espera confirmación del usuario antes
 * de guardarse definitivamente. Si [askForReminder] = true, se mostrará
 * el selector de hora.
 */
data class PendingNote(
    val content: String,
    val category: NoteCategory,
    val source: NoteSource,
    val remindAt: Long? = null,
    val askForReminder: Boolean = false
)

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val noteDao: NoteDao,
    private val firestoreRepository: FirestoreRepository,
    private val circleSessionRepository: CircleSessionRepository
) : ViewModel() {

    companion object {
        private const val TAG = "NotesViewModel"
    }

    // ── Notas ─────────────────────────────────────────────────────────────────

    val notes = noteDao.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unreadFamiliarCount = noteDao.countUnreadFamiliar()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ── Estado de voz ─────────────────────────────────────────────────────────

    private val voiceManager = VoiceInputManager(context)
    val voiceState: StateFlow<VoiceInputState> = voiceManager.state

    private val _pendingNote = MutableStateFlow<PendingNote?>(null)
    val pendingNote: StateFlow<PendingNote?> = _pendingNote.asStateFlow()

    // ── TTS ───────────────────────────────────────────────────────────────────

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        // TTS para anunciar notas del familiar
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale.Builder().setLanguage("es").setRegion("CO").build()
        }

        // Observar resultados de voz y crear nota pendiente
        viewModelScope.launch {
            voiceManager.state.collect { state ->
                if (state is VoiceInputState.Result) {
                    onVoiceResult(state.text)
                }
            }
        }

        // Observar notas del familiar desde Firestore
        viewModelScope.launch {
            circleSessionRepository.circleIdFlow
                .flatMapLatest { id ->
                    if (id == null) flowOf(emptyList()) else firestoreRepository.observeFamiliarNotes(id)
                }
                .collect { remoteNotes ->
                    remoteNotes.forEach { remote -> importFamiliarNote(remote) }
                }
        }
    }

    // ── Voz: ciclo start → pending → confirm/discard ──────────────────────────

    /** Inicia el reconocimiento de voz. Debe llamarse desde el hilo principal. */
    fun startVoiceInput() {
        voiceManager.startListening()
    }

    /** Detiene la escucha anticipadamente. */
    fun stopVoiceInput() {
        voiceManager.stopListening()
    }

    /** Descarta la nota pendiente y vuelve al estado Idle. */
    fun discardPendingNote() {
        _pendingNote.value = null
        voiceManager.reset()
    }

    /** El usuario ajustó la hora del recordatorio para la nota pendiente. */
    fun setPendingReminder(epochMs: Long?) {
        _pendingNote.update { it.copy(remindAt = epochMs, askForReminder = false) }
    }

    /** El usuario confirmó — guarda la nota definitivamente. */
    fun confirmPendingNote() {
        val note = _pendingNote.value ?: return
        _pendingNote.value = null
        voiceManager.reset()
        viewModelScope.launch {
            noteDao.insertNote(
                NoteEntity(
                    content   = note.content,
                    category  = note.category.name,
                    remindAt  = note.remindAt,
                    source    = note.source.name,
                    isRead    = true
                )
            )
            Log.d(TAG, "Nota guardada: ${note.content} [${note.category}]")
        }
    }

    // ── Notas manuales (teclado) ──────────────────────────────────────────────

    fun addNoteManual(content: String) {
        if (content.isBlank()) return
        val category = NoteCategorizer.categorize(content)
        viewModelScope.launch {
            noteDao.insertNote(
                NoteEntity(
                    content  = content.trim(),
                    category = category.name,
                    source   = NoteSource.TEXT.name
                )
            )
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch { noteDao.deleteNote(note) }
    }

    fun markNoteRead(note: NoteEntity) {
        viewModelScope.launch { noteDao.markRead(note.id) }
    }

    // ── Internos ──────────────────────────────────────────────────────────────

    private fun onVoiceResult(text: String) {
        val category = NoteCategorizer.categorize(text)
        _pendingNote.value = PendingNote(
            content         = text,
            category        = category,
            source          = NoteSource.VOICE,
            askForReminder  = category.isActionable
        )
    }

    private suspend fun importFamiliarNote(remote: FamiliarNoteData) {
        // Evitar duplicados
        val existing = noteDao.getByRemoteId(remote.id)
        if (existing != null) return

        val entity = NoteEntity(
            content   = "${remote.authorName} anotó: ${remote.content}",
            category  = NoteCategory.FAMILIAR.name,
            source    = NoteSource.FAMILIAR.name,
            isRead    = false,
            remoteId  = remote.id,
            createdAt = remote.createdAt
        )
        noteDao.insertNote(entity)

        // Marcar como leída en Firestore para no reenviar
        circleSessionRepository.getCircleId()?.let {
            firestoreRepository.markFamiliarNoteRead(it, remote.id)
        }

        // Anunciar con TTS
        withContext(Dispatchers.Main) {
            speak("${remote.authorName} te dejó una nota: ${remote.content}")
        }
        Log.d(TAG, "Nota del familiar importada: ${remote.id}")
    }

    private fun speak(text: String) {
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "familiar_note")
        }
    }

    override fun onCleared() {
        voiceManager.destroy()
        tts?.shutdown()
        super.onCleared()
    }
}

// ─── extensión privada ────────────────────────────────────────────────────────

private fun <T> MutableStateFlow<T?>.update(transform: (T) -> T) {
    value = value?.let(transform)
}
