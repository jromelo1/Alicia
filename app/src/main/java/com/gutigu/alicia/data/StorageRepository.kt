package com.gutigu.alicia.data

import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sube fotos a Firebase Storage:
 *  - pastillas:  /circles/{circleId}/medications/{medId}/photo.jpg
 *  - recuerdos:  /circles/{circleId}/memories/{memoryId}/photo.jpg
 *
 * Requiere que el dispositivo ya tenga un círculo (creado o unido por código).
 */
@Singleton
class StorageRepository @Inject constructor(
    private val storage: FirebaseStorage,
    private val circleSessionRepository: CircleSessionRepository
) {
    companion object {
        private const val TAG = "StorageRepository"
    }

    /**
     * Sube una foto de pastilla desde [localUri] (FileProvider o content URI)
     * y devuelve la URL pública de descarga como [Result].
     */
    suspend fun uploadPillPhoto(localUri: Uri, medId: Int): Result<String> = runCatching {
        val circleId = circleSessionRepository.getCircleId()
            ?: error("Sin círculo todavía — no se puede subir la foto")
        val ref = storage.reference
            .child("circles/$circleId/medications/$medId/photo.jpg")
        Log.d(TAG, "Subiendo foto de pastilla → ${ref.path}")
        ref.putFile(localUri).await()
        val downloadUrl = ref.downloadUrl.await().toString()
        Log.d(TAG, "✅ Foto subida: $downloadUrl")
        downloadUrl
    }

    /**
     * Sube la foto de un recuerdo desde [localUri] (FileProvider o content URI)
     * y devuelve la URL pública de descarga como [Result].
     */
    suspend fun uploadMemoryPhoto(localUri: Uri, memoryId: String): Result<String> = runCatching {
        val circleId = circleSessionRepository.getCircleId()
            ?: error("Sin círculo todavía — no se puede subir la foto")
        val ref = storage.reference
            .child("circles/$circleId/memories/$memoryId/photo.jpg")
        Log.d(TAG, "Subiendo foto de recuerdo → ${ref.path}")
        ref.putFile(localUri).await()
        val downloadUrl = ref.downloadUrl.await().toString()
        Log.d(TAG, "✅ Foto subida: $downloadUrl")
        downloadUrl
    }
}
