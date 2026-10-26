package com.gutigu.alicia.feature.dailycall

/**
 * Perfil para la llamada diaria de Alicia — los mismos datos de la "ficha" que
 * antes solo vivían en el chat, ahora usados por el agente de voz (Retell) para
 * personalizar la llamada. Vive en `circles/{circleId}.callProfile`, editable
 * tanto por el Adulto Mayor como por el Familiar (quien primero llene los datos).
 */
data class CallProfile(
    val fullName: String = "",
    /** Cómo le gusta que le llamen — "trato" (ej. "Doña Rosa", "Don Pepe"). */
    val treatment: String = "",
    /** true = Alicia le habla de "tú", false = de "usted". "Usted" por defecto — más respetuoso al no saberlo. */
    val useTu: Boolean = false,
    /** "es" o "en" — idioma en el que Alicia conversa. */
    val language: String = "es",
    val originCountry: String = "",
    /** Texto libre: gustos, temas de conversación. */
    val interests: String = "",
    /** Texto libre: nombres de familiares y quién es quién, para que Alicia los mencione bien. */
    val familyInfo: String = "",
    /** Texto libre de la familia: temas delicados y correcciones. Alicia le da prioridad sobre su memoria. */
    val familyNotes: String = "",
    val emergencyContactName: String = "",
    val emergencyContactPhone: String = "",
    /** Teléfono al que se marca — en formato E.164 completo (con "+" y código de país). */
    val phoneE164: String = "",
    val preferredHour: Int = 10,
    val preferredMinute: Int = 0,
    /** ID de zona horaria de Android (ej. "America/Bogota") del dispositivo que configuró la hora. */
    val timeZoneId: String = "",
    val callEnabled: Boolean = false,
    /** Epoch ms de cuándo se aceptó el consentimiento específico para la llamada con IA — null = nunca. */
    val consentAcceptedAt: Long? = null,
    /**
     * Quién llenó el formulario — [com.gutigu.alicia.feature.profile.UserProfile.name]
     * ("ADULTO_MAYOR" o "FAMILIAR"). Lo captura la app sola, según la sesión activa al
     * guardar por primera vez; nunca se le pregunta al usuario ni se vuelve a pisar.
     */
    val registeredBy: String = "",
    /**
     * Estado del proceso completo (no confundir con [CallRecord.status], que es el
     * resultado de una sola llamada). Arranca en [CallProfileStatus.PENDING_INTRO] y
     * lo irá avanzando el sistema — ver funciones/ Etapa 2, todavía no implementada.
     */
    val profileStatus: String = CallProfileStatus.PENDING_INTRO,
    /** Resumen de la última llamada — contexto para que la siguiente no empiece de cero. */
    val lastCallNotes: String = "",
    val lastCallAt: Long? = null
)

/**
 * Valores conocidos de [CallProfile.profileStatus]. Se deja como String (no enum
 * cerrado) porque la Etapa 2 va a definir más estados según cómo reaccione Alicia
 * en la primera llamada — un enum habría que ampliarlo a ciegas hoy.
 */
object CallProfileStatus {
    /** Recién configurado, todavía no hubo la primera llamada de presentación. */
    const val PENDING_INTRO = "pendiente_presentacion"
}

enum class CallStatus { COMPLETED, NO_ANSWER, FAILED, IN_VOICEMAIL, UNKNOWN }

/** Quién habla en una línea de la conversación de la llamada. */
enum class CallSpeaker { ALICIA, ELDER }

/** Una intervención de la conversación — en el orden en que ocurrió. */
data class TranscriptLine(val speaker: CallSpeaker, val text: String)

/** Un registro de llamada ya ocurrida — lo escribe únicamente la Cloud Function tras el webhook de Retell. */
data class CallRecord(
    val id: String = "",
    val startedAt: Long = 0L,
    val endedAt: Long = 0L,
    val durationSeconds: Int = 0,
    val summary: String = "",
    val status: CallStatus = CallStatus.UNKNOWN,
    /** Lo que se habló, línea por línea. Vacío en llamadas sin contestar o anteriores a esta función. */
    val transcript: List<TranscriptLine> = emptyList()
)

// ─────────────────────────────────────────────
//  Gustos sugeridos — mismo catálogo que tenía el chat de Alicia, reutilizado
//  como ayuda rápida al llenar el campo de "gustos" del perfil.
// ─────────────────────────────────────────────

data class InterestOption(val label: String, val keyword: String)

val SUGGESTED_INTERESTS = listOf(
    InterestOption("🎸 Metal / Rock", "metal"),
    InterestOption("🎵 Música", "música"),
    InterestOption("✈️ Viajes", "viajes"),
    InterestOption("🍳 Cocina", "cocina"),
    InterestOption("🌱 Jardín", "jardín"),
    InterestOption("📚 Libros", "libros"),
    InterestOption("🎬 Películas", "películas"),
    InterestOption("📷 Fotografía", "fotografía"),
    InterestOption("⚽ Fútbol", "fútbol"),
    InterestOption("🏃 Ejercicio", "ejercicio"),
    InterestOption("🐱 Mascotas", "mascotas"),
    InterestOption("🧩 Manualidades", "manualidades"),
    InterestOption("🎭 Teatro / Arte", "teatro"),
    InterestOption("🌿 Naturaleza", "naturaleza"),
    InterestOption("🧘 Bienestar", "bienestar")
)
