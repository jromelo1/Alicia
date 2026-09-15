package com.gutigu.alicia.feature.chat

import android.content.Context
import android.util.Log
import com.gutigu.alicia.BuildConfig
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_CHAT    = "chat_prefs"
private const val KEY_MESSAGES  = "chat_messages"
private const val KEY_PROFILE   = "chat_profile"
private const val MAX_HISTORY   = 30
private const val TAG           = "ChatRepository"

private const val API_URL = "https://api.anthropic.com/v1/messages"
private const val MODEL   = "claude-opus-4-6"

// ─────────────────────────────────────────────
//  Interface
// ─────────────────────────────────────────────

interface ChatRepository {
    suspend fun generateGreeting(profile: UserProfile, neighbors: List<NeighborInterest>): Result<String>
    suspend fun sendMessage(userMessage: String, history: List<ChatMessage>, profile: UserProfile): Result<String>
    fun saveHistory(messages: List<ChatMessage>)
    fun loadHistory(): List<ChatMessage>
    fun saveUserProfile(profile: UserProfile)
    fun loadUserProfile(): UserProfile
    fun loadNeighborInterests(): List<NeighborInterest>
}

// ─────────────────────────────────────────────
//  Implementación — llama a Claude via HTTP
// ─────────────────────────────────────────────

@Singleton
class ClaudeChatRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : ChatRepository {

    private val prefs get() = context.getSharedPreferences(PREFS_CHAT, Context.MODE_PRIVATE)

    // ── API principal ────────────────────────────────────────────────────

    /**
     * Genera el saludo inicial de bienvenida.
     * El trigger es un mensaje USER oculto que no se muestra en la UI.
     * Se incluye en el historial para que la conversación siga siendo coherente.
     */
    override suspend fun generateGreeting(
        profile: UserProfile,
        neighbors: List<NeighborInterest>
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val trigger = buildGreetingTrigger(profile, neighbors)
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", trigger)
                })
            }
            callClaude(buildSystemPrompt(profile, neighbors), messages)
        }
    }

    override suspend fun sendMessage(
        userMessage: String,
        history: List<ChatMessage>,
        profile: UserProfile
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val neighbors = loadNeighborInterests()
            val allMessages = history + ChatMessage(role = MessageRole.USER, content = userMessage)
            callClaude(buildSystemPrompt(profile, neighbors), buildMessagesJson(allMessages))
        }
    }

    // ── Construcción de prompts ──────────────────────────────────────────

    private fun buildSystemPrompt(profile: UserProfile, neighbors: List<NeighborInterest>): String {
        val interestsText = if (profile.interests.isEmpty())
            "aún no ha indicado intereses"
        else
            profile.interests.joinToString(", ")

        val sharedText = neighbors
            .filter { n -> n.interests.any { it in profile.interests } }
            .takeIf { it.isNotEmpty() }
            ?.joinToString("\n") { "- ${it.name}: ${it.interests.joinToString(", ")}" }
            ?.let { "\n\nVecinos del círculo con intereses en común:\n$it" }
            ?: ""

        return """
Eres Alicia, la asistente de IA del app "Círculo de Confianza" — una red vecinal de apoyo mutuo en Colombia, diseñada especialmente para adultos mayores.

Tu misión es conectar vecinos, fomentar la solidaridad y alegrar el día a día de la comunidad.

Usuario: ${profile.name}
Sus intereses: $interestsText$sharedText

Reglas de comportamiento:
- Habla SIEMPRE en español, de forma cálida, cercana y positiva.
- Usa oraciones cortas y lenguaje sencillo. Evita tecnicismos.
- Cuando haya intereses en común con vecinos, menciónalo naturalmente para fomentar conexión.
- Propón temas de conversación, actividades o formas de ayudar a vecinos cuando sea natural.
- Si alguien necesita ayuda urgente, recuérdale usar el Botón de Pánico de la app.
- No des consejos médicos, legales ni financieros.
- Regla estricta e innegociable sobre medicamentos y salud: JAMÁS expliques para qué sirve un medicamento, qué significa una dosis, des un diagnóstico, u opines qué podría pasar si no se toma algo. Si preguntan sobre alguno de estos temas, responde con calidez que eso lo debe consultar con su familia o su doctor, y no lo profundices aunque insistan.
- Si la conversación se estanca, sugiere un tema nuevo basado en los intereses del usuario.
- Sé proactivo: puedes preguntar sobre su día, proponer platicar de algo, o mencionar un vecino que podría conectar.
        """.trimIndent()
    }

    private fun buildGreetingTrigger(profile: UserProfile, neighbors: List<NeighborInterest>): String {
        val sharedTopics = neighbors
            .filter { n -> n.interests.any { it in profile.interests } }
            .flatMap { it.interests.filter { i -> i in profile.interests } }
            .distinct()
            .take(2)

        return buildString {
            append("Por favor inicia la conversación con un saludo cálido según la hora del día")
            if (sharedTopics.isNotEmpty()) {
                append(", menciona que hay vecinos del círculo que comparten el interés en ${sharedTopics.joinToString(" y ")}")
            } else if (profile.interests.isNotEmpty()) {
                append(", comenta algo interesante sobre ${profile.interests.first()}")
            }
            append(", y propón un tema de conversación. Sé breve (2-3 oraciones) y muy cercano.")
        }
    }

    // ── Construcción del array de mensajes para la API ───────────────────

    private fun buildMessagesJson(messages: List<ChatMessage>): JSONArray {
        // La API exige que el primer mensaje sea de role "user"
        val toSend = messages.takeLast(MAX_HISTORY).let { list ->
            if (list.firstOrNull()?.role == MessageRole.ASSISTANT) {
                listOf(ChatMessage(role = MessageRole.USER, content = "Hola", hidden = true)) + list
            } else list
        }
        return JSONArray().apply {
            toSend.forEach { msg ->
                put(JSONObject().apply {
                    put("role", if (msg.role == MessageRole.USER) "user" else "assistant")
                    put("content", msg.content)
                })
            }
        }
    }

    // ── Llamada HTTP a Anthropic Messages API ────────────────────────────

    private fun callClaude(systemPrompt: String, messages: JSONArray): String {
        val apiKey = BuildConfig.ANTHROPIC_API_KEY
        check(apiKey.isNotBlank()) {
            "Falta ANTHROPIC_API_KEY. Agrégala en local.properties: ANTHROPIC_API_KEY=sk-ant-..."
        }

        val body = JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", 1024)
            put("system", systemPrompt)
            put("messages", messages)
        }.toString()

        val conn = URL(API_URL).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-api-key", apiKey)
            conn.setRequestProperty("anthropic-version", "2023-06-01")
            conn.doOutput = true
            conn.connectTimeout = 30_000
            conn.readTimeout   = 60_000

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

            val code = conn.responseCode
            if (code != 200) {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "sin detalle"
                error("Error API $code: $err")
            }

            val response = conn.inputStream.bufferedReader().readText()
            JSONObject(response)
                .getJSONArray("content")
                .getJSONObject(0)
                .getString("text")

        } finally {
            conn.disconnect()
        }
    }

    // ── Persistencia en SharedPreferences ───────────────────────────────

    override fun saveHistory(messages: List<ChatMessage>) {
        val arr = JSONArray()
        messages.takeLast(MAX_HISTORY).forEach { msg ->
            arr.put(JSONObject().apply {
                put("id",        msg.id)
                put("role",      msg.role.name)
                put("content",   msg.content)
                put("timestamp", msg.timestamp)
                put("hidden",    msg.hidden)
            })
        }
        prefs.edit().putString(KEY_MESSAGES, arr.toString()).apply()
    }

    override fun loadHistory(): List<ChatMessage> {
        val json = prefs.getString(KEY_MESSAGES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getJSONObject(it).toMessage() }
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando historial", e)
            emptyList()
        }
    }

    override fun saveUserProfile(profile: UserProfile) {
        val arr = JSONArray(profile.interests)
        prefs.edit()
            .putString(KEY_PROFILE, JSONObject().apply {
                put("name",      profile.name)
                put("interests", arr)
            }.toString())
            .apply()
    }

    override fun loadUserProfile(): UserProfile {
        val json = prefs.getString(KEY_PROFILE, null) ?: return UserProfile()
        return try {
            val obj = JSONObject(json)
            val arr = obj.getJSONArray("interests")
            UserProfile(
                name      = obj.getString("name"),
                interests = (0 until arr.length()).map { arr.getString(it) }
            )
        } catch (e: Exception) {
            UserProfile()
        }
    }

    /**
     * Datos de demostración — en producción vendría de Firestore
     * circles/{id}/members con perfiles de intereses.
     */
    override fun loadNeighborInterests(): List<NeighborInterest> = listOf(
        NeighborInterest("Don Roberto",   listOf("metal", "historia", "fútbol")),
        NeighborInterest("Doña Carmen",   listOf("cocina", "jardín", "bienestar")),
        NeighborInterest("Miguel",        listOf("viajes", "fotografía", "metal")),
        NeighborInterest("Señora Lupe",   listOf("libros", "teatro", "manualidades")),
    )

    // ── Helpers de deserialización ───────────────────────────────────────

    private fun JSONObject.toMessage() = ChatMessage(
        id        = getString("id"),
        role      = MessageRole.valueOf(getString("role")),
        content   = getString("content"),
        timestamp = getLong("timestamp"),
        hidden    = optBoolean("hidden", false)
    )
}

// ─────────────────────────────────────────────
//  Módulo Hilt
// ─────────────────────────────────────────────

@Module
@InstallIn(SingletonComponent::class)
abstract class ChatModule {
    @Binds
    abstract fun bindChatRepository(impl: ClaudeChatRepository): ChatRepository
}
