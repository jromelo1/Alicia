package com.gutigu.alicia.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un registro por día de check-in.
 * Se inserta cuando la alarma dispara (PENDING) y se actualiza
 * cuando el adulto responde (RESPONDED) o el tiempo expira (MISSED).
 */
@Entity(
    tableName = "checkin_history",
    indices   = [Index(value = ["date"], unique = true)]   // 1 registro por día
)
data class CheckInHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,           // "yyyy-MM-dd"  — clave para upsert
    val scheduledAt: Long,      // timestamp cuando disparó la alarma
    val respondedAt: Long? = null,
    val status: String = "PENDING"   // PENDING | RESPONDED | MISSED
)
