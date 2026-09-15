package com.gutigu.alicia.feature.chat

import java.util.UUID

// ─────────────────────────────────────────────
//  Modelos
// ─────────────────────────────────────────────

enum class MessageRole { USER, ASSISTANT }

/**
 * Mensaje individual del chat.
 * [hidden] = true → se incluye en el contexto de la API pero NO se muestra en la UI.
 * Se usa para el "trigger" inicial que pide el saludo de bienvenida.
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val hidden: Boolean = false
)

/** Perfil del usuario: nombre e intereses para personalizar el chat. */
data class UserProfile(
    val name: String = "Vecino",
    val interests: List<String> = emptyList()   // ej. ["metal", "viajes", "cocina"]
)

/** Intereses de un vecino del círculo — para sugerir temas compartidos. */
data class NeighborInterest(
    val name: String,
    val interests: List<String>
)

/** Estado completo de la pantalla de chat. */
data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val userProfile: UserProfile = UserProfile(),
    val showInterestsSheet: Boolean = false
)

// ─────────────────────────────────────────────
//  Intereses disponibles (label → keyword)
// ─────────────────────────────────────────────

data class InterestOption(val label: String, val keyword: String)

val AVAILABLE_INTERESTS = listOf(
    InterestOption("🎸 Metal / Rock",    "metal"),
    InterestOption("🎵 Música",          "música"),
    InterestOption("✈️ Viajes",           "viajes"),
    InterestOption("🍳 Cocina",          "cocina"),
    InterestOption("🌱 Jardín",          "jardín"),
    InterestOption("📚 Libros",          "libros"),
    InterestOption("🎬 Películas",       "películas"),
    InterestOption("📷 Fotografía",      "fotografía"),
    InterestOption("⚽ Fútbol",          "fútbol"),
    InterestOption("🏃 Ejercicio",       "ejercicio"),
    InterestOption("🐱 Mascotas",        "mascotas"),
    InterestOption("🧩 Manualidades",    "manualidades"),
    InterestOption("🎭 Teatro / Arte",   "teatro"),
    InterestOption("🌿 Naturaleza",      "naturaleza"),
    InterestOption("🕹️ Videojuegos",     "videojuegos"),
    InterestOption("🧘 Bienestar",       "bienestar")
)
