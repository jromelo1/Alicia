package com.gutigu.alicia.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "appointments")
data class AppointmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    /** Nombre del médico o de con quién es la cita (ej. "Dra. Pérez"). */
    val doctorName: String,
    /** Ej. "Cardiología" — opcional. */
    val specialty: String = "",
    /** Ej. "Clínica del Country, consultorio 302" — opcional. */
    val location: String = "",
    /** Instrucción o nota adicional — opcional. */
    val note: String = "",
    /** Epoch ms de la fecha y hora de la cita — también cuándo suena el recordatorio. */
    val dateTimeMillis: Long
)
