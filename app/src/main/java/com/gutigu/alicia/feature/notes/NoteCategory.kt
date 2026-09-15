package com.gutigu.alicia.feature.notes

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
//  Enumeraciones
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Categorías de nota con emoji e información de display.
 * [isActionable] = true indica que puede tener un recordatorio con hora.
 */
enum class NoteCategory(
    val emoji: String,
    val label: String,
    val color: Color,
    val isActionable: Boolean = false
) {
    SHOPPING ("🛒", "Compras",      Color(0xFF1E6FA8), isActionable = false),
    HEALTH   ("🏥", "Salud",        Color(0xFFD32F2F), isActionable = true),
    FAMILY   ("👨‍👩‍👧", "Familia",   Color(0xFFC0622E), isActionable = false),
    TASK     ("✅", "Tarea",        Color(0xFF2E7D32), isActionable = true),
    REMINDER ("⏰", "Recordatorio", Color(0xFFB7891A), isActionable = true),
    FAMILIAR ("💌", "Del familiar", Color(0xFF7A1F8A), isActionable = false),
    GENERAL  ("📝", "General",      Color(0xFF6B6880), isActionable = false)
}

enum class NoteSource { VOICE, TEXT, FAMILIAR }

// ─────────────────────────────────────────────────────────────────────────────
//  Auto-categorización por palabras clave
// ─────────────────────────────────────────────────────────────────────────────

object NoteCategorizer {

    /**
     * Lista de reglas ordenadas por prioridad.
     * La primera categoría cuyos keywords aparezcan en el texto gana.
     */
    private val rules = listOf(
        NoteCategory.HEALTH to setOf(
            "médico", "medico", "doctor", "doctora", "cita médica", "cita medica",
            "farmacia", "pastilla", "pastillas", "medicamento", "medicina", "hospital",
            "enfermero", "enfermera", "análisis", "analisis", "examen médico",
            "examen medico", "receta", "vacuna", "consultorio", "especialista",
            "operación", "operacion", "cirugía", "cirugia", "inyección", "inyeccion"
        ),
        NoteCategory.SHOPPING to setOf(
            "comprar", "compra", "tienda", "mercado", "supermercado", "pan",
            "leche", "azúcar", "azucar", "café", "cafe", "frutas", "verduras",
            "carne", "pollo", "pescado", "jabón", "jabon", "detergente", "shampoo",
            "papel", "huevos", "arroz", "frijoles", "aceite", "sal", "lista de compras"
        ),
        NoteCategory.FAMILY to setOf(
            "hijo", "hija", "nieto", "nieta", "familiar", "visita", "visitante",
            "abuela", "abuelo", "mamá", "mama", "papá", "papa", "hermano", "hermana",
            "sobrino", "sobrina", "primo", "prima", "tío", "tia", "cumpleaños",
            "aniversario", "boda", "fiesta familiar", "viene", "van a venir", "vienen"
        ),
        NoteCategory.REMINDER to setOf(
            "recordar", "no olvidar", "no olvides", "acordarme", "acordar",
            "mañana", "pasado mañana", "esta tarde", "esta noche", "esta semana",
            "próximo", "proximo", "la próxima", "la proxima"
        ),
        NoteCategory.TASK to setOf(
            "llamar", "llamarle", "llamarles", "pagar", "recoger", "llevar",
            "traer", "enviar", "mandar", "buscar", "ir a", "pasar por",
            "hacer", "arreglar", "cancelar", "confirmar", "revisar",
            "limpiar", "reparar", "entregar", "devolver", "renovar"
        )
    )

    fun categorize(text: String): NoteCategory {
        val lower = text.lowercase().trim()
        for ((category, keywords) in rules) {
            if (keywords.any { lower.contains(it) }) return category
        }
        return NoteCategory.GENERAL
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Datos de nota remota del familiar (Firestore)
// ─────────────────────────────────────────────────────────────────────────────

data class FamiliarNoteData(
    val id: String,
    val content: String,
    val authorName: String,
    val createdAt: Long
)
