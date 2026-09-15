package com.gutigu.alicia.feature.medications

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gutigu.alicia.MemberRole
import com.gutigu.alicia.data.MedicationEntity
import com.gutigu.alicia.data.StorageRepository
import com.gutigu.alicia.feature.circle.CircleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado de la subida de foto a Firebase Storage. */
sealed class PhotoUploadState {
    object Idle      : PhotoUploadState()
    object Uploading : PhotoUploadState()
    data class Done(val url: String) : PhotoUploadState()
    data class Error(val message: String) : PhotoUploadState()
}

@HiltViewModel
class MedicationViewModel @Inject constructor(
    private val repository: MedicationRepository,
    private val storageRepository: StorageRepository,
    private val circleRepository: CircleRepository
) : ViewModel() {

    companion object { private const val TAG = "MedicationViewModel" }

    val medications = repository.medications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** true si el círculo no tiene ningún guardián — un medicamento sin confirmar no le avisaría a nadie. */
    val hasNoGuardians: StateFlow<Boolean> = circleRepository.observeMembers()
        .map { members -> members.none { it.role == MemberRole.GUARDIAN } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _photoUploadState = MutableStateFlow<PhotoUploadState>(PhotoUploadState.Idle)
    val photoUploadState: StateFlow<PhotoUploadState> = _photoUploadState.asStateFlow()

    /**
     * Añade un medicamento y, si se proporcionó [photoUri], sube la foto a Storage.
     * El workaround de reintentar el schedule tras obtener la URL se hace
     * vía [updatePhotoAndReschedule].
     */
    fun addMedication(
        name: String,
        dose: String,
        hour: Int,
        minute: Int,
        withFood: Boolean = false,
        note: String = "",
        photoUri: Uri? = null
    ) {
        viewModelScope.launch {
            val med = repository.addMedication(
                name = name, dose = dose,
                hour = hour, minute = minute,
                withFood = withFood, note = note
            )

            if (photoUri != null) {
                _photoUploadState.value = PhotoUploadState.Uploading
                storageRepository.uploadPillPhoto(photoUri, med.id)
                    .onSuccess { url ->
                        repository.updatePhotoUrl(med.id, url)
                        _photoUploadState.value = PhotoUploadState.Done(url)
                        Log.d(TAG, "Foto de pastilla guardada: $url")
                    }
                    .onFailure { err ->
                        _photoUploadState.value = PhotoUploadState.Error(
                            err.message ?: "Error al subir la foto"
                        )
                        Log.e(TAG, "Error subiendo foto de pastilla", err)
                    }
            } else {
                _photoUploadState.value = PhotoUploadState.Idle
            }
        }
    }

    fun deleteMedication(med: MedicationEntity) {
        viewModelScope.launch { repository.deleteMedication(med) }
    }

    fun toggleActive(med: MedicationEntity) {
        viewModelScope.launch { repository.toggleActive(med) }
    }

    fun resetUploadState() {
        _photoUploadState.value = PhotoUploadState.Idle
    }
}
