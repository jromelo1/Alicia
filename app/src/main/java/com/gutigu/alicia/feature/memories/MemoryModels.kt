package com.gutigu.alicia.feature.memories

/** Una persona etiquetada en un recuerdo, ej. name="Gustavo", relationship="hijo". */
data class MemoryTag(
    val name: String = "",
    val relationship: String = ""
)

/** Un recuerdo de la Línea de Tiempo (Firestore: circles/{circleId}/memories/{id}). */
data class Memory(
    val id: String = "",
    val photoUrl: String? = null,
    val title: String = "",
    val description: String = "",
    val eventDate: Long = System.currentTimeMillis(),
    val tags: List<MemoryTag> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)
