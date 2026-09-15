package com.gutigu.alicia.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Insert
    suspend fun insertNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    /** Notas creadas desde el inicio del día (para la Línea de Vida del Dashboard). */
    @Query("SELECT * FROM notes WHERE createdAt >= :startOfDay ORDER BY createdAt DESC")
    fun getNotesToday(startOfDay: Long): Flow<List<NoteEntity>>

    /** Busca por ID remoto Firestore — evita importar la misma nota del familiar dos veces. */
    @Query("SELECT * FROM notes WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): NoteEntity?

    /** Marca una nota del familiar como leída. */
    @Query("UPDATE notes SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Int)

    /** Cuenta notas del familiar sin leer. */
    @Query("SELECT COUNT(*) FROM notes WHERE source = 'FAMILIAR' AND isRead = 0")
    fun countUnreadFamiliar(): Flow<Int>
}
