package com.gutigu.alicia.feature.memories

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gutigu.alicia.data.CircleSessionRepository
import com.gutigu.alicia.data.FirestoreRepository
import com.gutigu.alicia.data.StorageRepository
import com.gutigu.alicia.feature.profile.ProfileRepository
import com.gutigu.alicia.feature.profile.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
//  Estado de subida de foto
// ─────────────────────────────────────────────────────────────────────────────

sealed class MemoryPhotoUploadState {
    object Idle : MemoryPhotoUploadState()
    object Uploading : MemoryPhotoUploadState()
    data class Error(val message: String) : MemoryPhotoUploadState()
}

// ─────────────────────────────────────────────────────────────────────────────
//  UI State
// ─────────────────────────────────────────────────────────────────────────────

data class MemoriesUiState(
    val memories: List<Memory> = emptyList(),
    // El Familiar puede agregar/editar/eliminar; el Adulto Mayor solo ve el feed.
    val isFamiliar: Boolean = false
)

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MemoriesViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val storageRepository: StorageRepository,
    private val circleSessionRepository: CircleSessionRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val memoriesFlow = circleSessionRepository.circleIdFlow.flatMapLatest { circleId ->
        if (circleId == null) emptyFlow() else firestoreRepository.observeMemories(circleId)
    }

    val state: StateFlow<MemoriesUiState> = combine(
        memoriesFlow,
        profileRepository.profileFlow
    ) { memories, profile ->
        MemoriesUiState(memories = memories, isFamiliar = profile == UserProfile.FAMILIAR)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MemoriesUiState()
    )

    private val _uploadState = MutableStateFlow<MemoryPhotoUploadState>(MemoryPhotoUploadState.Idle)
    val uploadState: StateFlow<MemoryPhotoUploadState> = _uploadState.asStateFlow()

    /** Publica un recuerdo nuevo. Si hay foto local, la sube primero a Storage. */
    fun addMemory(
        title: String,
        description: String,
        eventDate: Long,
        tags: List<MemoryTag>,
        photoUri: Uri?
    ) {
        viewModelScope.launch {
            val circleId = circleSessionRepository.getCircleId() ?: return@launch
            val memoryId = firestoreRepository.newMemoryId(circleId)

            var photoUrl: String? = null
            if (photoUri != null) {
                _uploadState.value = MemoryPhotoUploadState.Uploading
                storageRepository.uploadMemoryPhoto(photoUri, memoryId)
                    .onSuccess { photoUrl = it }
                    .onFailure {
                        _uploadState.value = MemoryPhotoUploadState.Error(it.message ?: "Error al subir la foto")
                    }
                if (_uploadState.value !is MemoryPhotoUploadState.Error) {
                    _uploadState.value = MemoryPhotoUploadState.Idle
                }
            }

            firestoreRepository.publishMemory(
                circleId,
                Memory(
                    id = memoryId,
                    photoUrl = photoUrl,
                    title = title.trim(),
                    description = description.trim(),
                    eventDate = eventDate,
                    tags = tags,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            val circleId = circleSessionRepository.getCircleId() ?: return@launch
            firestoreRepository.deleteMemory(circleId, memoryId)
        }
    }

    fun dismissUploadError() {
        _uploadState.value = MemoryPhotoUploadState.Idle
    }
}
