package com.gutigu.alicia.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications ORDER BY hour, minute")
    fun getAllMedications(): Flow<List<MedicationEntity>>

    @Insert
    suspend fun insertMedication(med: MedicationEntity): Long

    @Update
    suspend fun updateMedication(med: MedicationEntity)

    @Delete
    suspend fun deleteMedication(med: MedicationEntity)

    /** Registra que el usuario tomó este medicamento (desde la notificación o manualmente). */
    @Query("UPDATE medications SET takenAt = :takenAt WHERE id = :id")
    suspend fun markTaken(id: Int, takenAt: Long)

    /** Devuelve los medicamentos tomados desde el inicio del día (epoch ms). */
    @Query("SELECT * FROM medications WHERE takenAt >= :startOfDay ORDER BY takenAt ASC")
    fun getTakenToday(startOfDay: Long): Flow<List<MedicationEntity>>

    /** Actualiza la URL de la foto de la pastilla tras subirla a Storage. */
    @Query("UPDATE medications SET photoUrl = :url WHERE id = :id")
    suspend fun updatePhotoUrl(id: Int, url: String)

    /** Devuelve un medicamento por ID (para la pantalla de alarma). */
    @Query("SELECT * FROM medications WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): MedicationEntity?

    /** Devuelve todos los medicamentos activos (para reprogramar tras reinicio). */
    @Query("SELECT * FROM medications WHERE isActive = 1 ORDER BY hour, minute")
    suspend fun getActiveMedications(): List<MedicationEntity>
}
