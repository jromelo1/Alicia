package com.gutigu.alicia.feature.notes

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.gutigu.alicia.R
import com.gutigu.alicia.ui.theme.AliciaAccent
import com.gutigu.alicia.ui.theme.AliciaAccentDark
import com.gutigu.alicia.ui.theme.AliciaAlert
import com.gutigu.alicia.ui.theme.AliciaInk2
import com.gutigu.alicia.ui.theme.AliciaSuccess
import com.gutigu.alicia.ui.theme.AliciaTextSecondary
import com.gutigu.alicia.ui.theme.AliciaWarningStrong

// ─────────────────────────────────────────────────────────────────────────────
//  Enumeraciones
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Categorías de nota con emoji e información de display.
 * [isActionable] = true indica que puede tener un recordatorio con hora.
 * [labelRes] en vez de texto literal — se resuelve con stringResource() en el
 * idioma activo (ver ui/theme/AppLanguage.kt), igual que las pestañas de MainActivity.
 */
enum class NoteCategory(
    val emoji: String,
    @StringRes val labelRes: Int,
    val color: Color,
    val isActionable: Boolean = false
) {
    SHOPPING ("🛒", R.string.note_cat_shopping,  AliciaInk2, isActionable = false),
    HEALTH   ("🏥", R.string.note_cat_health,    AliciaAlert, isActionable = true),
    FAMILY   ("👨‍👩‍👧", R.string.note_cat_family, AliciaAccent, isActionable = false),
    TASK     ("✅", R.string.note_cat_task,       AliciaSuccess, isActionable = true),
    REMINDER ("⏰", R.string.note_cat_reminder,   AliciaWarningStrong, isActionable = true),
    FAMILIAR ("💌", R.string.note_cat_familiar,   AliciaAccentDark, isActionable = false),
    GENERAL  ("📝", R.string.note_cat_general,    AliciaTextSecondary, isActionable = false)
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
