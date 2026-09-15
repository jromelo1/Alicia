package com.gutigu.alicia.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val dose: String,
    val hour: Int,
    val minute: Int,
    val isActive: Boolean = true,
    /** Epoch ms en que el usuario tocó "Tomado" — null si aún no la ha tomado hoy */
    val takenAt: Long? = null,
    /** URL de descarga en Firebase Storage de la foto real de la pastilla */
    val photoUrl: String? = null,
    /** true = tomar con comida; false = en ayunas */
    val withFood: Boolean = false,
    /** Instrucción adicional (ej. "Con un vaso de agua") */
    val note: String = ""
)
