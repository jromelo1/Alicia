package com.gutigu.alicia.data

import android.util.Log
import com.gutigu.alicia.CircleMember
import com.gutigu.alicia.MemberRole
import com.gutigu.alicia.feature.checkin.CheckInScheduleData
import com.gutigu.alicia.feature.familiar.CircleProfileData
import com.gutigu.alicia.feature.memories.Memory
import com.gutigu.alicia.feature.memories.MemoryTag
import com.gutigu.alicia.feature.notes.FamiliarNoteData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Estructura Firestore:
 *
 * /circles/{circleId}/                    circleId = código de invitación (6-8 caracteres)
 *   - ownerUid, userName, userPhone, createdAt
 *   - lastCheckin: { date, status, timestamp }
 *   - checkInEnabled, checkInHour, checkInMinute, checkInTimeoutMinutes
 *     (horario del check-in diario — lo define el Familiar, lo lee el Adulto Mayor)
 *
 * /circles/{circleId}/tokens/{fcmToken}
 * /circles/{circleId}/checkins/{date}
 * /circles/{circleId}/panic/active
 * /circles/{circleId}/familiar_notes/{noteId}
 * /circles/{circleId}/members/{memberId}   memberId = uid real si se unió por código,
 *                                            o un id generado si el adulto mayor lo
 *                                            agregó a mano (sin cuenta propia)
 * /circles/{circleId}/memories/{memoryId}  Línea de Tiempo — el familiar publica,
 *                                            el adulto mayor solo lee
 * /circles/{circleId}/appointments/{apptId} apptId = id local (Room) de la cita —
 *                                            el adulto mayor es el único que escribe
 *                                            (crea/borra), el familiar solo lee
 * /circles/{circleId}/medications/{medId}   medId = id local (Room) del medicamento —
 *                                            mismo patrón: el adulto mayor escribe
 *                                            (crea/edita/marca tomado/borra), el
 *                                            familiar solo lee. Notas NO se replica aquí
 *                                            a propósito: es un diario privado (§8.2 del
 *                                            spec), nunca debe llegar al familiar.
 *
 * /users/{uid}/                            un doc por cuenta autenticada
 *   - circleId, profile ("ADULTO_MAYOR" | "FAMILIAR")
 *
 * IMPORTANTE: las reglas de seguridad de Firestore viven en la consola de Firebase,
 * no en este repo. Deben restringir todo lo bajo /circles/{circleId}/ a quien tenga
 * users/{request.auth.uid}.circleId == circleId, y /users/{uid} solo al propio uid.
 */
@Singleton
class FirestoreRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val messaging: FirebaseMessaging
) {
    companion object {
        private const val TAG = "FirestoreRepo"
        private const val COLLECTION_CIRCLES  = "circles"
        private const val COLLECTION_USERS    = "users"
        private const val SUB_TOKENS          = "tokens"
        private const val SUB_CHECKINS        = "checkins"
        private const val SUB_FAMILIAR_NOTES  = "familiar_notes"
        private const val SUB_PANIC           = "panic"
        private const val SUB_MEMBERS         = "members"
        private const val SUB_MEMORIES        = "memories"
        private const val SUB_APPOINTMENTS    = "appointments"
        private const val SUB_MEDICATIONS     = "medications"
        private const val DOC_PANIC_ACTIVE    = "active"

        // Sin 0/O ni 1/I para que no se confundan al leerlo/dictarlo en voz alta.
        private const val INVITE_CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        private const val INVITE_CODE_LENGTH   = 8
    }

    val uid: String? get() = auth.currentUser?.uid

    private fun circleDoc(circleId: String) =
        firestore.collection(COLLECTION_CIRCLES).document(circleId)

    // ── Creación / vinculación del círculo ───────────────────────────────────────

    /**
     * Crea un círculo nuevo (lo llama el dispositivo del Adulto Mayor durante el
     * onboarding) y devuelve el código de invitación — que también es el circleId.
     * Reintenta si el código generado ya existe (colisión, muy improbable).
     */
    suspend fun createCircle(userName: String): Result<String> = suspendRunCatching {
        Log.d(TAG, "createCircle: iniciando para userName=$userName")
        val myUid = uid ?: error("Sin sesión activa")
        var code: String
        var attempts = 0
        do {
            code = generateInviteCode()
            attempts++
        } while (attempts < 5 && circleDoc(code).get().await().exists())

        circleDoc(code).set(
            mapOf(
                "ownerUid" to myUid,
                "userName" to userName,
                "userPhone" to "",
                "createdAt" to System.currentTimeMillis()
            )
        ).await()

        firestore.collection(COLLECTION_USERS).document(myUid)
            .set(mapOf("circleId" to code, "profile" to "ADULTO_MAYOR"))
            .await()

        Log.d(TAG, "Círculo creado: $code")
        code
    }

    /**
     * Un familiar se une a un círculo existente con el código de invitación.
     * Falla si el código no corresponde a ningún círculo.
     */
    suspend fun joinCircle(
        code: String,
        memberName: String,
        memberPhone: String,
        role: MemberRole
    ): Result<Unit> = suspendRunCatching {
        Log.d(TAG, "joinCircle: iniciando para code=$code")
        val myUid = uid ?: error("Sin sesión activa")
        val normalizedCode = code.trim().uppercase()
        val snapshot = circleDoc(normalizedCode).get().await()
        if (!snapshot.exists()) error("Ese código no corresponde a ningún círculo")

        val member = CircleMember(
            userId = myUid,
            name = memberName,
            phone = memberPhone,
            role = role,
            notifyOnPanic = true
        )
        circleDoc(normalizedCode).collection(SUB_MEMBERS).document(myUid)
            .set(member.toFirestoreMap())
            .await()

        firestore.collection(COLLECTION_USERS).document(myUid)
            .set(mapOf("circleId" to normalizedCode, "profile" to "FAMILIAR"))
            .await()

        Log.d(TAG, "Unido al círculo: $normalizedCode")
    }

    /**
     * Recupera el círculo del Adulto Mayor en un dispositivo nuevo (reinstalación o
     * cambio de teléfono) — la sesión anónima anterior no sobrevive, así que esto crea
     * una sesión anónima nueva y la reconecta al mismo círculo existente por su código,
     * sin perder nada de lo que ya vive en Firestore bajo ese circleId (nombre, teléfono,
     * horario de check-in, historial, miembros). Falla si el código no corresponde a
     * ningún círculo.
     */
    suspend fun recoverElderCircle(code: String): Result<String> = suspendRunCatching {
        Log.d(TAG, "recoverElderCircle: iniciando para code=$code")
        val myUid = uid ?: error("Sin sesión activa")
        val normalizedCode = code.trim().uppercase()
        val snapshot = circleDoc(normalizedCode).get().await()
        if (!snapshot.exists()) error("Ese código no corresponde a ningún círculo")

        circleDoc(normalizedCode).set(mapOf("ownerUid" to myUid), SetOptions.merge()).await()

        firestore.collection(COLLECTION_USERS).document(myUid)
            .set(mapOf("circleId" to normalizedCode, "profile" to "ADULTO_MAYOR"))
            .await()

        Log.d(TAG, "Círculo recuperado: $normalizedCode")
        normalizedCode
    }

    /** Busca en Firestore el circleId asociado a este uid (fallback si se perdió la caché local). */
    suspend fun resolveMyCircleId(): String? {
        val myUid = uid ?: return null
        return try {
            firestore.collection(COLLECTION_USERS).document(myUid).get().await()
                .getString("circleId")
        } catch (e: Exception) {
            Log.e(TAG, "Error resolviendo circleId", e)
            null
        }
    }

    private fun generateInviteCode(): String =
        (1..INVITE_CODE_LENGTH)
            .map { INVITE_CODE_ALPHABET.random() }
            .joinToString("")

    // ── Miembros del círculo ──────────────────────────────────────────────────────

    fun observeCircleMembers(circleId: String): Flow<List<CircleMember>> = callbackFlow {
        val listener = circleDoc(circleId).collection(SUB_MEMBERS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error escuchando miembros del círculo", error)
                    return@addSnapshotListener
                }
                val members = snapshot?.documents?.mapNotNull { it.toCircleMember() } ?: emptyList()
                trySend(members.sortedWith(compareBy({ it.role.ordinal }, { it.name })))
            }
        awaitClose { listener.remove() }
    }

    suspend fun getCircleMembers(circleId: String): List<CircleMember> = try {
        circleDoc(circleId).collection(SUB_MEMBERS).get().await()
            .documents.mapNotNull { it.toCircleMember() }
    } catch (e: Exception) {
        Log.e(TAG, "Error obteniendo miembros del círculo", e)
        emptyList()
    }

    suspend fun addCircleMember(circleId: String, member: CircleMember): CircleMember {
        val id = member.userId.ifBlank { circleDoc(circleId).collection(SUB_MEMBERS).document().id }
        val withId = member.copy(userId = id)
        circleDoc(circleId).collection(SUB_MEMBERS).document(id)
            .set(withId.toFirestoreMap())
            .await()
        return withId
    }

    suspend fun updateCircleMember(circleId: String, member: CircleMember) {
        circleDoc(circleId).collection(SUB_MEMBERS).document(member.userId)
            .set(member.toFirestoreMap())
            .await()
    }

    suspend fun removeCircleMember(circleId: String, memberId: String) {
        circleDoc(circleId).collection(SUB_MEMBERS).document(memberId).delete().await()
    }

    private fun CircleMember.toFirestoreMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "name" to name,
        "phone" to phone,
        "photoUrl" to (photoUrl ?: ""),
        "role" to role.name,
        "notifyOnPanic" to notifyOnPanic,
        "fcmToken" to (fcmToken ?: "")
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toCircleMember(): CircleMember? {
        return try {
            val memberName = getString("name") ?: return null
            CircleMember(
                userId = getString("userId") ?: id,
                name = memberName,
                phone = getString("phone") ?: "",
                photoUrl = getString("photoUrl")?.takeIf { it.isNotBlank() },
                role = MemberRole.valueOf(getString("role") ?: "MEMBER"),
                notifyOnPanic = getBoolean("notifyOnPanic") ?: true,
                fcmToken = getString("fcmToken")?.takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando miembro del círculo", e)
            null
        }
    }

    // ── Token FCM ──────────────────────────────────────────────────────────────

    /** Registra el token FCM de este dispositivo bajo su círculo. */
    suspend fun saveFcmToken(circleId: String) {
        try {
            val token = messaging.token.await()
            circleDoc(circleId).collection(SUB_TOKENS).document(token)
                .set(mapOf("token" to token, "updatedAt" to System.currentTimeMillis()))
                .await()
            Log.d(TAG, "Token FCM guardado para circleId=$circleId")
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando token FCM", e)
        }
    }

    /** Obtiene todos los tokens FCM registrados para el círculo. */
    suspend fun getCircleTokens(circleId: String): List<String> = try {
        circleDoc(circleId).collection(SUB_TOKENS).get().await()
            .documents.mapNotNull { it.getString("token") }
    } catch (e: Exception) {
        Log.e(TAG, "Error obteniendo tokens del círculo", e)
        emptyList()
    }

    // ── Perfil del círculo ─────────────────────────────────────────────────────

    suspend fun saveCircleProfile(circleId: String, userName: String, userPhone: String? = null) {
        try {
            val data = mutableMapOf<String, Any>("userName" to userName)
            userPhone?.let { data["userPhone"] = it }
            circleDoc(circleId).set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando perfil del círculo", e)
        }
    }

    // ── Horario de check-in (lo define el Familiar) ───────────────────────────

    /** El Familiar guarda el horario y el umbral de aviso del check-in diario. */
    suspend fun saveCheckInSchedule(circleId: String, schedule: CheckInScheduleData) {
        try {
            circleDoc(circleId).set(
                mapOf(
                    "checkInEnabled" to schedule.enabled,
                    "checkInHour" to schedule.hour,
                    "checkInMinute" to schedule.minute,
                    "checkInTimeoutMinutes" to schedule.timeoutMinutes
                ),
                SetOptions.merge()
            ).await()
            Log.d(TAG, "Horario de check-in guardado: $schedule")
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando horario de check-in", e)
        }
    }

    /** El Adulto Mayor escucha en tiempo real el horario que definió su Familiar. */
    fun observeCheckInSchedule(circleId: String): Flow<CheckInScheduleData> = callbackFlow {
        val listener = circleDoc(circleId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error escuchando horario de check-in", error)
                    return@addSnapshotListener
                }
                trySend(
                    CheckInScheduleData(
                        enabled = snapshot?.getBoolean("checkInEnabled") ?: false,
                        hour = snapshot?.getLong("checkInHour")?.toInt() ?: 10,
                        minute = snapshot?.getLong("checkInMinute")?.toInt() ?: 0,
                        timeoutMinutes = snapshot?.getLong("checkInTimeoutMinutes")?.toInt() ?: 120
                    )
                )
            }
        awaitClose { listener.remove() }
    }

    /** Observa el perfil del círculo (nombre y teléfono del adulto mayor) en tiempo real. */
    fun observeCircleProfile(circleId: String): Flow<CircleProfileData> = callbackFlow {
        val listener = circleDoc(circleId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error escuchando perfil del círculo", error)
                    return@addSnapshotListener
                }
                trySend(
                    CircleProfileData(
                        userName = snapshot?.getString("userName") ?: "",
                        userPhone = snapshot?.getString("userPhone") ?: ""
                    )
                )
            }
        awaitClose { listener.remove() }
    }

    // ── Check-in events ────────────────────────────────────────────────────────

    /**
     * Publica o actualiza el estado de check-in en Firestore.
     * Tanto el Adulto Mayor como el Familiar pueden leer este dato en tiempo real.
     */
    suspend fun publishCheckIn(
        circleId: String,
        date: String,
        status: String,
        scheduledAt: Long,
        respondedAt: Long? = null
    ) {
        try {
            val data = mutableMapOf<String, Any>(
                "date" to date,
                "status" to status,
                "scheduledAt" to scheduledAt
            )
            respondedAt?.let { data["respondedAt"] = it }

            circleDoc(circleId).collection(SUB_CHECKINS).document(date)
                .set(data, SetOptions.merge())
                .await()

            // También actualiza el campo de resumen en el documento raíz
            circleDoc(circleId).set(mapOf("lastCheckin" to data), SetOptions.merge()).await()

            Log.d(TAG, "Check-in publicado: $date → $status")
        } catch (e: Exception) {
            Log.e(TAG, "Error publicando check-in", e)
        }
    }

    // ── Notas del familiar para el adulto mayor ────────────────────────────────

    /** El Familiar publica una nota que Alicia leerá al adulto mayor. */
    suspend fun publishFamiliarNote(circleId: String, content: String, authorName: String = "Tu familiar") {
        try {
            val noteId = System.currentTimeMillis().toString()
            circleDoc(circleId).collection(SUB_FAMILIAR_NOTES).document(noteId)
                .set(mapOf(
                    "content" to content,
                    "authorName" to authorName,
                    "createdAt" to System.currentTimeMillis(),
                    "read" to false
                ))
                .await()
            Log.d(TAG, "Nota del familiar publicada: $content")
        } catch (e: Exception) {
            Log.e(TAG, "Error publicando nota del familiar", e)
        }
    }

    /**
     * El dispositivo del adulto mayor escucha en tiempo real las notas enviadas
     * por su familiar. Sólo devuelve las no leídas para evitar re-importarlas.
     */
    fun observeFamiliarNotes(circleId: String): Flow<List<FamiliarNoteData>> = callbackFlow {
        val listener = circleDoc(circleId).collection(SUB_FAMILIAR_NOTES)
            .whereEqualTo("read", false)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error escuchando notas del familiar", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        FamiliarNoteData(
                            id = doc.id,
                            content = doc.getString("content") ?: return@mapNotNull null,
                            authorName = doc.getString("authorName") ?: "Familiar",
                            createdAt = doc.getLong("createdAt") ?: 0L
                        )
                    } catch (e: Exception) { null }
                }?.sortedByDescending { it.createdAt } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /** Marca la nota del familiar como leída en Firestore (evita re-entrega). */
    suspend fun markFamiliarNoteRead(circleId: String, noteId: String) {
        try {
            circleDoc(circleId).collection(SUB_FAMILIAR_NOTES).document(noteId)
                .set(mapOf("read" to true), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error marcando nota como leída", e)
        }
    }

    // ── Alerta de pánico (SOS) ──────────────────────────────────────────────────

    /**
     * Publica una alerta de pánico activa. El dispositivo Familiar la escucha
     * en tiempo real para poner el dashboard en modo de alerta roja.
     */
    suspend fun publishPanicAlert(circleId: String, alertId: String) {
        try {
            circleDoc(circleId).collection(SUB_PANIC).document(DOC_PANIC_ACTIVE)
                .set(mapOf(
                    "active" to true,
                    "alertId" to alertId,
                    "timestamp" to System.currentTimeMillis()
                ))
                .await()
            Log.d(TAG, "Alerta de pánico publicada: $alertId")
        } catch (e: Exception) {
            Log.e(TAG, "Error publicando alerta de pánico", e)
        }
    }

    /** Limpia la alerta de pánico activa (al resolverla o marcarla falsa alarma). */
    suspend fun clearPanicAlert(circleId: String) {
        try {
            circleDoc(circleId).collection(SUB_PANIC).document(DOC_PANIC_ACTIVE)
                .set(mapOf("active" to false), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error limpiando alerta de pánico", e)
        }
    }

    /** Escucha en tiempo real si hay una alerta de pánico activa. */
    fun observePanicAlert(circleId: String): Flow<Boolean> = callbackFlow {
        val listener = circleDoc(circleId).collection(SUB_PANIC).document(DOC_PANIC_ACTIVE)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error escuchando alerta de pánico", error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.getBoolean("active") ?: false)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Escucha en tiempo real los check-ins del círculo.
     * Usado por FamiliarStatusScreen para actualizaciones sin refrescar.
     */
    fun observeCheckIns(circleId: String): Flow<List<CheckInHistoryEntity>> = callbackFlow {
        val listener = circleDoc(circleId).collection(SUB_CHECKINS)
            .orderBy("scheduledAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(60)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error escuchando check-ins", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        CheckInHistoryEntity(
                            date = doc.getString("date") ?: return@mapNotNull null,
                            scheduledAt = doc.getLong("scheduledAt") ?: 0L,
                            respondedAt = doc.getLong("respondedAt"),
                            status = doc.getString("status") ?: "PENDING"
                        )
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    // ── Línea de Tiempo / Diario de Recuerdos ─────────────────────────────────

    /** Genera un id nuevo para un recuerdo, sin escribirlo aún (se usa para subir la foto primero). */
    fun newMemoryId(circleId: String): String =
        circleDoc(circleId).collection(SUB_MEMORIES).document().id

    /** El Familiar publica (o edita) un recuerdo con foto, etiquetas y fecha del evento. */
    suspend fun publishMemory(circleId: String, memory: Memory) {
        try {
            circleDoc(circleId).collection(SUB_MEMORIES).document(memory.id)
                .set(memory.toFirestoreMap())
                .await()
            Log.d(TAG, "Recuerdo publicado: ${memory.title}")
        } catch (e: Exception) {
            Log.e(TAG, "Error publicando recuerdo", e)
        }
    }

    suspend fun deleteMemory(circleId: String, memoryId: String) {
        try {
            circleDoc(circleId).collection(SUB_MEMORIES).document(memoryId).delete().await()
            Log.d(TAG, "Recuerdo eliminado: $memoryId")
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando recuerdo", e)
        }
    }

    /** Escucha en tiempo real los recuerdos del círculo, del más reciente al más antiguo. */
    fun observeMemories(circleId: String): Flow<List<Memory>> = callbackFlow {
        val listener = circleDoc(circleId).collection(SUB_MEMORIES)
            .orderBy("eventDate", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error escuchando recuerdos", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toMemory() } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    private fun Memory.toFirestoreMap(): Map<String, Any> = mapOf(
        "photoUrl" to (photoUrl ?: ""),
        "title" to title,
        "description" to description,
        "eventDate" to eventDate,
        "tags" to tags.map { mapOf("name" to it.name, "relationship" to it.relationship) },
        "createdAt" to createdAt
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toMemory(): Memory? {
        return try {
            Memory(
                id = id,
                photoUrl = getString("photoUrl")?.takeIf { it.isNotBlank() },
                title = getString("title") ?: "",
                description = getString("description") ?: "",
                eventDate = getLong("eventDate") ?: 0L,
                tags = (get("tags") as? List<*>)?.mapNotNull { raw ->
                    (raw as? Map<*, *>)?.let { m ->
                        val tagName = m["name"] as? String
                        if (tagName.isNullOrBlank()) null
                        else MemoryTag(name = tagName, relationship = m["relationship"] as? String ?: "")
                    }
                } ?: emptyList(),
                createdAt = getLong("createdAt") ?: 0L
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando recuerdo", e)
            null
        }
    }

    // ── Citas médicas ──────────────────────────────────────────────────────────
    // A diferencia de Medicamentos y Notas (que solo viven en Room, local al
    // dispositivo del Adulto Mayor — ver hallazgo en FamiliarViewModel), las citas
    // SÍ se reflejan aquí para que el Familiar tenga visibilidad de solo lectura,
    // igual que ya hace el check-in con [observeCheckIns].

    /** El Adulto Mayor publica (o actualiza) una cita para que su Familiar la vea. */
    suspend fun publishAppointment(circleId: String, appointment: AppointmentEntity) {
        try {
            circleDoc(circleId).collection(SUB_APPOINTMENTS).document(appointment.id.toString())
                .set(appointment.toFirestoreMap())
                .await()
            Log.d(TAG, "Cita publicada: ${appointment.doctorName}")
        } catch (e: Exception) {
            Log.e(TAG, "Error publicando cita", e)
        }
    }

    /** Retira una cita eliminada por el Adulto Mayor de la vista del Familiar. */
    suspend fun deleteAppointmentRemote(circleId: String, appointmentId: Int) {
        try {
            circleDoc(circleId).collection(SUB_APPOINTMENTS).document(appointmentId.toString())
                .delete().await()
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando cita remota", e)
        }
    }

    /** El Familiar escucha en tiempo real las citas médicas del Adulto Mayor — solo lectura. */
    fun observeAppointments(circleId: String): Flow<List<AppointmentEntity>> = callbackFlow {
        val listener = circleDoc(circleId).collection(SUB_APPOINTMENTS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error escuchando citas", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toAppointment() } ?: emptyList()
                trySend(list.sortedBy { it.dateTimeMillis })
            }
        awaitClose { listener.remove() }
    }

    private fun AppointmentEntity.toFirestoreMap(): Map<String, Any> = mapOf(
        "doctorName" to doctorName,
        "specialty" to specialty,
        "location" to location,
        "note" to note,
        "dateTimeMillis" to dateTimeMillis
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toAppointment(): AppointmentEntity? {
        return try {
            AppointmentEntity(
                id = id.toIntOrNull() ?: return null,
                doctorName = getString("doctorName") ?: return null,
                specialty = getString("specialty") ?: "",
                location = getString("location") ?: "",
                note = getString("note") ?: "",
                dateTimeMillis = getLong("dateTimeMillis") ?: 0L
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando cita", e)
            null
        }
    }

    // ── Medicamentos ──────────────────────────────────────────────────────────
    // Mismo patrón que Citas médicas: el Adulto Mayor es el único que escribe
    // (crear/editar/activar/marcar tomado/borrar), el Familiar solo observa.

    /** Publica el estado completo de un medicamento (incluye si ya fue tomado hoy). */
    suspend fun publishMedication(circleId: String, medication: MedicationEntity) {
        try {
            circleDoc(circleId).collection(SUB_MEDICATIONS).document(medication.id.toString())
                .set(medication.toFirestoreMap())
                .await()
            Log.d(TAG, "Medicamento publicado: ${medication.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error publicando medicamento", e)
        }
    }

    suspend fun deleteMedicationRemote(circleId: String, medicationId: Int) {
        try {
            circleDoc(circleId).collection(SUB_MEDICATIONS).document(medicationId.toString())
                .delete().await()
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando medicamento remoto", e)
        }
    }

    /** El Familiar escucha en tiempo real los medicamentos del Adulto Mayor — solo lectura. */
    fun observeMedications(circleId: String): Flow<List<MedicationEntity>> = callbackFlow {
        val listener = circleDoc(circleId).collection(SUB_MEDICATIONS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error escuchando medicamentos", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toMedication() } ?: emptyList()
                trySend(list.sortedBy { it.hour * 60 + it.minute })
            }
        awaitClose { listener.remove() }
    }

    private fun MedicationEntity.toFirestoreMap(): Map<String, Any> = mapOf(
        "name" to name,
        "dose" to dose,
        "hour" to hour,
        "minute" to minute,
        "isActive" to isActive,
        "takenAt" to (takenAt ?: 0L),
        "photoUrl" to (photoUrl ?: ""),
        "withFood" to withFood,
        "note" to note
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toMedication(): MedicationEntity? {
        return try {
            MedicationEntity(
                id = id.toIntOrNull() ?: return null,
                name = getString("name") ?: return null,
                dose = getString("dose") ?: "",
                hour = getLong("hour")?.toInt() ?: 0,
                minute = getLong("minute")?.toInt() ?: 0,
                isActive = getBoolean("isActive") ?: true,
                takenAt = getLong("takenAt")?.takeIf { it > 0 },
                photoUrl = getString("photoUrl")?.takeIf { it.isNotBlank() },
                withFood = getBoolean("withFood") ?: false,
                note = getString("note") ?: ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando medicamento", e)
            null
        }
    }
}
