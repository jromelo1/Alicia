package com.gutigu.alicia.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInHistoryDao {

    /** Flujo de todo el historial, del más reciente al más antiguo. */
    @Query("SELECT * FROM checkin_history ORDER BY scheduledAt DESC")
    fun getAllHistory(): Flow<List<CheckInHistoryEntity>>

    /**
     * Inserta el registro de "alarma disparada" del día.
     * IGNORE si ya existe una entrada para esa fecha (evita duplicados en pruebas).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertScheduled(record: CheckInHistoryEntity)

    /** Marca el día como RESPONDIDO y guarda la hora de respuesta. */
    @Query("""
        UPDATE checkin_history
        SET status = 'RESPONDED', respondedAt = :respondedAt
        WHERE date = :date
    """)
    suspend fun markResponded(date: String, respondedAt: Long)

    /**
     * Marca el día como PERDIDO sólo si aún está PENDIENTE
     * (para no sobreescribir si el usuario respondió justo a tiempo).
     */
    @Query("""
        UPDATE checkin_history
        SET status = 'MISSED'
        WHERE date = :date AND status = 'PENDING'
    """)
    suspend fun markMissed(date: String)

    /** Elimina registros más allá de los últimos 60 para no crecer indefinidamente. */
    @Query("""
        DELETE FROM checkin_history
        WHERE id NOT IN (
            SELECT id FROM checkin_history
            ORDER BY scheduledAt DESC
            LIMIT 60
        )
    """)
    suspend fun pruneOldRecords()
}
