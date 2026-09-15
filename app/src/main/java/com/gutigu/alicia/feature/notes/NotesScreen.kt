package com.gutigu.alicia.feature.notes

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.gutigu.alicia.data.NoteEntity
import com.gutigu.alicia.ui.theme.AliciaAccent
import com.gutigu.alicia.ui.theme.AliciaBackground
import com.gutigu.alicia.ui.theme.AliciaFormAlert
import com.gutigu.alicia.ui.theme.AliciaSurfaceAlt
import com.gutigu.alicia.ui.theme.AliciaText
import com.gutigu.alicia.ui.theme.AliciaTextSecondary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ── Paleta, centralizada en ui/theme/Color.kt donde coincide ────────────────────
private val NavyDark       = AliciaBackground   // fondo de pantalla
private val NavyMedium     = AliciaSurfaceAlt   // superficie de tarjeta/diálogo
private val TextPrimary    = AliciaText
private val TextSecondary  = AliciaTextSecondary
private val AccentGreen    = AliciaAccent
private val AccentRed      = AliciaFormAlert
private val PurpleMic      = Color(0xFF9C27B0)
private val PurpleMicLight = Color(0xFF7A1F8A)

// ─────────────────────────────────────────────────────────────────────────────
//  Pantalla principal
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NotesScreen(viewModel: NotesViewModel = hiltViewModel()) {
    val notes        by viewModel.notes.collectAsState()
    val voiceState   by viewModel.voiceState.collectAsState()
    val pendingNote  by viewModel.pendingNote.collectAsState()
    val unreadCount  by viewModel.unreadFamiliarCount.collectAsState()

    val context = LocalContext.current
    var showTextDialog     by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }

    // ── Permiso de micrófono ───────────────────────────────────────────────────
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startVoiceInput() }

    fun onMicTap() {
        val ok = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (ok) viewModel.startVoiceInput() else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Abrir selector de hora cuando la nota pendiente lo solicite
    LaunchedEffect(pendingNote?.askForReminder) {
        if (pendingNote?.askForReminder == true) showReminderDialog = true
    }

    Scaffold(containerColor = NavyDark) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Contenido scrollable ─────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // Cabecera
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("🎤 Mis Notas", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("Tu diario privado — no se comparte con tu familia", color = TextSecondary, fontSize = 15.sp)
                    }
                    IconButton(
                        onClick = { showTextDialog = true },
                        modifier = Modifier.size(44.dp).background(NavyMedium, CircleShape)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Nota manual", tint = AccentGreen, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Banner notas del familiar sin leer
                AnimatedVisibility(visible = unreadCount > 0) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PurpleMic.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                .border(1.dp, PurpleMicLight.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("💌", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (unreadCount == 1) "Tu familiar te dejó 1 nota nueva"
                                else "Tu familiar te dejó $unreadCount notas nuevas",
                                color = PurpleMicLight,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                // Lista
                if (notes.isEmpty()) {
                    EmptyNotesState(onTapMic = ::onMicTap)
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 180.dp)
                    ) {
                        items(notes, key = { it.id }) { note ->
                            NoteCardItem(
                                note     = note,
                                onDelete = { viewModel.deleteNote(note) },
                                onRead   = { if (!note.isRead) viewModel.markNoteRead(note) }
                            )
                        }
                    }
                }
            }

            // ── Overlay de transcripción en vivo ───────────────────────────────
            AnimatedVisibility(
                visible  = voiceState is VoiceInputState.Listening,
                enter    = fadeIn() + slideInVertically { it },
                exit     = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val partial = (voiceState as? VoiceInputState.Listening)?.partial ?: ""
                LiveTranscriptionOverlay(partial = partial, onStopTap = { viewModel.stopVoiceInput() })
            }

            // ── Nota pendiente de confirmación ─────────────────────────────────
            AnimatedVisibility(
                visible  = pendingNote != null && voiceState is VoiceInputState.Idle,
                enter    = fadeIn() + slideInVertically { it },
                exit     = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                pendingNote?.let { pn ->
                    PendingNoteConfirmPanel(
                        pending   = pn,
                        onConfirm = { viewModel.confirmPendingNote() },
                        onDiscard = { viewModel.discardPendingNote() }
                    )
                }
            }

            // ── Botón central de micrófono ─────────────────────────────────────
            AnimatedVisibility(
                visible  = voiceState is VoiceInputState.Idle && pendingNote == null,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                MicFab(onClick = ::onMicTap, modifier = Modifier.padding(bottom = 32.dp))
            }

            // ── Toast de error ─────────────────────────────────────────────────
            val errorMsg = (voiceState as? VoiceInputState.Error)?.message
            AnimatedVisibility(
                visible  = errorMsg != null,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 32.dp)
                        .background(AccentRed.copy(alpha = 0.9f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        errorMsg ?: "",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // Diálogo nota manual
    if (showTextDialog) {
        AddNoteTextDialog(
            onDismiss = { showTextDialog = false },
            onConfirm = { text -> viewModel.addNoteManual(text); showTextDialog = false }
        )
    }

    // Diálogo recordatorio
    if (showReminderDialog) {
        ReminderTimeDialog(
            onDismiss = { viewModel.setPendingReminder(null); showReminderDialog = false },
            onConfirm = { hour, minute ->
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (!after(Calendar.getInstance())) add(Calendar.DATE, 1)
                }
                viewModel.setPendingReminder(cal.timeInMillis)
                showReminderDialog = false
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Botón FAB de micrófono con pulso
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MicFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_idle")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            tween(1_200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ), label = "pulse"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(88.dp).scale(pulse)
        ) {
            // Halo de fondo
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(PurpleMic.copy(alpha = 0.22f))
            )
            // Botón
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(PurpleMic)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Hablar",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text("Toca para hablar", color = PurpleMicLight, fontSize = 13.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Overlay de transcripción en vivo
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LiveTranscriptionOverlay(partial: String, onStopTap: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val wave by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "wave"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, NavyDark.copy(alpha = 0.96f))))
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Ondas del micrófono
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(1.0f, 1.4f, 1.8f, 1.4f, 1.0f).forEachIndexed { i, baseH ->
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height((14 * baseH * (if (i == 2) wave else 1f)).dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(PurpleMicLight)
                    )
                }
            }

            // Texto parcial en vivo
            AnimatedContent(
                targetState = partial.ifBlank { "Te escucho…" },
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) },
                label = "partial"
            ) { text ->
                Text(
                    text       = text,
                    color      = if (partial.isBlank()) TextSecondary else TextPrimary,
                    fontSize   = if (partial.isBlank()) 18.sp else 24.sp,
                    fontWeight = if (partial.isBlank()) FontWeight.Normal else FontWeight.SemiBold,
                    textAlign  = TextAlign.Center,
                    lineHeight = 32.sp,
                    modifier   = Modifier.fillMaxWidth()
                )
            }

            // Botón detener
            TextButton(onClick = onStopTap) {
                Icon(Icons.Default.MicOff, contentDescription = null, tint = PurpleMicLight, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Detener", color = PurpleMicLight, fontSize = 14.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Panel de confirmación
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PendingNoteConfirmPanel(
    pending: PendingNote,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, NavyDark)))
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Card(
            shape  = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = NavyMedium),
            elevation = CardDefaults.cardElevation(8.dp),
            modifier  = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pending.category.emoji, fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            pending.category.label,
                            color = pending.category.color,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text("¿Guardo esto?", color = TextSecondary, fontSize = 12.sp)
                    }
                }

                Text(
                    pending.content,
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 28.sp
                )

                pending.remindAt?.let { ts ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⏰", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Recordatorio a las ${formatTime(ts)}", color = AccentGreen, fontSize = 13.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onDiscard, modifier = Modifier.weight(1f).height(48.dp)) {
                        Text("Descartar", color = TextSecondary, fontSize = 15.sp)
                    }
                    Button(
                        onClick  = onConfirm,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                    ) {
                        Text("✓  Guardar", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tarjeta de nota (renombrada NoteCardItem para evitar conflicto)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NoteCardItem(
    note: NoteEntity,
    onDelete: () -> Unit,
    onRead: () -> Unit
) {
    val category = runCatching { NoteCategory.valueOf(note.category) }.getOrDefault(NoteCategory.GENERAL)
    val isUnread = !note.isRead
    val sourceName = NoteSource.entries.firstOrNull { it.name == note.source } ?: NoteSource.TEXT

    LaunchedEffect(isUnread) { if (isUnread) onRead() }

    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnread) category.color.copy(alpha = 0.10f) else NavyMedium
        ),
        border = if (isUnread) BorderStroke(1.dp, category.color.copy(alpha = 0.45f)) else null,
        elevation = CardDefaults.cardElevation(if (isUnread) 4.dp else 2.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(category.color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(category.emoji, fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (isUnread) {
                    Text("NUEVA", color = category.color, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Text(
                    note.content,
                    color     = TextPrimary,
                    fontSize  = 17.sp,
                    lineHeight = 24.sp,
                    maxLines  = 3,
                    overflow  = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    val srcEmoji = when (sourceName) {
                        NoteSource.VOICE    -> "🎤"
                        NoteSource.FAMILIAR -> "💌"
                        NoteSource.TEXT     -> "⌨️"
                    }
                    Text(srcEmoji, fontSize = 12.sp)
                    Text(formatDate(note.createdAt), color = TextSecondary, fontSize = 12.sp)
                    note.remindAt?.let { ts ->
                        Text("·", color = TextSecondary, fontSize = 12.sp)
                        Text("⏰ ${formatTime(ts)}", color = AccentGreen, fontSize = 12.sp)
                    }
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar",
                    tint = AccentRed.copy(alpha = 0.55f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Estado vacío
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyNotesState(onTapMic: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("🎤", fontSize = 72.sp, textAlign = TextAlign.Center)
        Text(
            "Habla y yo recuerdo",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            "Toca el micrófono y dile a Alicia\nqué quieres que recuerde por ti.\nEs privado: tu familia no lo verá.",
            color = TextSecondary,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick  = onTapMic,
            shape    = RoundedCornerShape(50.dp),
            modifier = Modifier.height(60.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = PurpleMic)
        ) {
            Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Hablar con Alicia", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Diálogos
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AddNoteTextDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(NavyMedium, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Nueva nota", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text("¿Qué quieres anotar?", color = TextSecondary) },
                    minLines = 3, maxLines = 6,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGreen, unfocusedBorderColor = TextSecondary.copy(alpha = 0.4f),
                        focusedLabelColor = AccentGreen, cursorColor = AccentGreen,
                        focusedContainerColor = NavyDark, unfocusedContainerColor = NavyDark
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = TextPrimary, fontSize = 18.sp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(50.dp)) {
                        Text("Cancelar", color = TextSecondary)
                    }
                    Button(
                        onClick  = { if (text.isNotBlank()) onConfirm(text) },
                        enabled  = text.isNotBlank(),
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                    ) { Text("Guardar", color = TextPrimary, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun ReminderTimeDialog(onDismiss: () -> Unit, onConfirm: (hour: Int, minute: Int) -> Unit) {
    var hour   by remember { mutableIntStateOf(9) }
    var minute by remember { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(NavyMedium, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("⏰", fontSize = 40.sp)
                Text(
                    "¿A qué hora te lo recuerdo?",
                    color = TextPrimary, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { val t = hour * 60 + minute - 30; val n = ((t % 1440) + 1440) % 1440; hour = n / 60; minute = n % 60 },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(width = 68.dp, height = 44.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = NavyDark, contentColor = AccentGreen)
                    ) { Text("−30m", fontSize = 12.sp) }

                    Text(
                        "%02d:%02d".format(hour, minute),
                        color = TextPrimary, fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    FilledTonalButton(
                        onClick = { val t = hour * 60 + minute + 30; val n = ((t % 1440) + 1440) % 1440; hour = n / 60; minute = n % 60 },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(width = 68.dp, height = 44.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = NavyDark, contentColor = AccentGreen)
                    ) { Text("+30m", fontSize = 12.sp) }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(50.dp)) {
                        Text("Sin recordatorio", color = TextSecondary, fontSize = 13.sp)
                    }
                    Button(
                        onClick  = { onConfirm(hour, minute) },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                    ) { Text("Aceptar", color = TextPrimary, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun formatDate(ts: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.Builder().setLanguage("es").build()).format(Date(ts))

private fun formatTime(ts: Long): String =
    SimpleDateFormat("HH:mm", Locale.Builder().setLanguage("es").build()).format(Date(ts))
