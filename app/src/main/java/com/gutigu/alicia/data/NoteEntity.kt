package com.gutigu.alicia.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** NoteCategory.name — GENERAL, SHOPPING, HEALTH, TASK, REMINDER, FAMILY, FAMILIAR */
    val category: String = "GENERAL",
    /** Epoch ms de un recordatorio programado (null = sin alarma) */
    val remindAt: Long? = null,
    /** NoteSource.name — VOICE, TEXT, FAMILIAR */
    val source: String = "TEXT",
    /** false para notas de familiar recién llegadas (sin leer aún) */
    val isRead: Boolean = true,
    /** ID remoto Firestore si la nota vino del familiar — evita duplicados */
    val remoteId: String? = null
)
