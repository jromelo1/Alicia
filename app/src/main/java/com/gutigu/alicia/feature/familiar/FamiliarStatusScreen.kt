package com.gutigu.alicia.feature.familiar

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gutigu.alicia.ui.theme.AliciaAccent
import com.gutigu.alicia.ui.theme.AliciaAccentBlue
import com.gutigu.alicia.ui.theme.AliciaAlert
import com.gutigu.alicia.ui.theme.AliciaBackground
import com.gutigu.alicia.ui.theme.AliciaSurface
import com.gutigu.alicia.ui.theme.AliciaSurfaceAlt
import com.gutigu.alicia.ui.theme.AliciaText
import com.gutigu.alicia.ui.theme.AliciaTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Paleta (spec v2 — claro), centralizada en ui/theme/Color.kt ────────────────
private val NavyDark      = AliciaBackground   // fondo de pantalla
private val NavyMedium    = AliciaSurface      // superficie de tarjeta
private val SurfaceAlt    = AliciaSurfaceAlt
private val TextPrimary   = AliciaText
private val TextSecondary = AliciaTextSecondary
private val AccentGreen   = AliciaAccent
// Única excepción de fondo saturado: modo alerta real (SOS o check-in perdido)
private val AlertBg              = AliciaAlert
private val TextOnAlert          = Color(0xFFFFFFFF)
private val TextOnAlertSecondary = Color(0xFFFCE0DE)

// Paleta adicional para las notas
private val PurpleFamiliar      = Color(0xFF9C27B0)
private val PurpleFamiliarLight = Color(0xFFCE93D8)

@Composable
fun FamiliarStatusScreen(viewModel: FamiliarViewModel = hiltViewModel()) {
    val state   by viewModel.dashboardState.collectAsState()
    val context = LocalContext.current

    // Determinar si estamos en modo alerta crítica
    val isAlert = state.wellbeing == WellbeingStatus.ALERT

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isAlert) AlertBg else NavyDark)
    ) {
        if (isAlert) {
            // ── MODO ALERTA: pantalla roja de emergencia ────────────────────
            AlertEmergencyScreen(state = state, context = context)
        } else {
            // ── MODO NORMAL: dashboard de paz mental ───────────────────────
            NormalDashboard(state = state, context = context, viewModel = viewModel)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Dashboard normal
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NormalDashboard(
    state: DashboardState,
    context: Context,
    viewModel: FamiliarViewModel
) {
    val name = state.userName.ifBlank { "tu familiar" }
    var showNoteDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = { BottomActionBar(state = state, context = context) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Encabezado ────────────────────────────────────────────────
            item {
                Text(
                    text = "Hola, estás monitoreando a",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                Text(
                    text = name,
                    color = TextPrimary,
                    fontFamily = MaterialTheme.typography.headlineLarge.fontFamily,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // ── 1. Hero: Indicador de estado ──────────────────────────────
            item {
                HeroStatusSection(state = state, name = name)
            }

            // ── Próxima cita médica (solo lectura) ─────────────────────────
            state.nextAppointment?.let { appt ->
                item {
                    NextAppointmentCard(appointment = appt)
                }
            }

            // ── 2. Línea de Vida ──────────────────────────────────────────
            item {
                Text(
                    text = "Actividad de hoy",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (state.todayActivities.isEmpty()) {
                item {
                    EmptyTimelineState()
                }
            } else {
                items(state.todayActivities, key = { it.timestamp }) { activity ->
                    TimelineItem(activity = activity)
                }
            }

            // ── Historial reciente (últimos 7 días) ───────────────────────
            val recentHistory = state.history
                .filter { it.date != todayStr() }
                .take(7)

            if (recentHistory.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = TextSecondary.copy(alpha = 0.2f)
                        )
                        Text(
                            "  Historial reciente  ",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = TextSecondary.copy(alpha = 0.2f)
                        )
                    }
                }
                items(recentHistory, key = { "hist_${it.id}" }) { record ->
                    HistoryRow(record)
                }
            }

            // ── 3. Mensaje a Alicia ───────────────────────────────────────
            item {
                SendNoteToAliciaCard(onClick = { showNoteDialog = true })
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }

    // Diálogo para escribir la nota
    if (showNoteDialog) {
        SendNoteDialog(
            userName  = name,
            onDismiss = { showNoteDialog = false },
            onSend    = { content, author ->
                viewModel.sendNoteToAdult(content, author)
                showNoteDialog = false
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  1. Hero Section — círculo de estado + mapa expandible
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HeroStatusSection(state: DashboardState, name: String) {
    // Pulso animado para estado ALERT/PENDING
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = if (state.wellbeing == WellbeingStatus.PENDING) 1.06f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val heroColor = state.wellbeing.heroColor()

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(24.dp),
        colors    = CardDefaults.cardColors(containerColor = heroColor.copy(alpha = 0.12f)),
        border    = androidx.compose.foundation.BorderStroke(
            width = 1.5.dp,
            color = heroColor.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Círculo grande de estado
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                heroColor.copy(alpha = 0.35f),
                                heroColor.copy(alpha = 0.10f)
                            )
                        )
                    )
                    .border(3.dp, heroColor.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text     = state.wellbeing.heroEmoji(),
                    fontSize = 48.sp
                )
            }

            // Mensaje principal
            Text(
                text = when (state.wellbeing) {
                    WellbeingStatus.OK      -> "Todo en orden"
                    WellbeingStatus.PENDING -> "Esperando respuesta"
                    WellbeingStatus.ALERT   -> "No respondió hoy"
                    WellbeingStatus.UNKNOWN -> "Sin datos aún"
                },
                color      = heroColor,
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center
            )

            // Subtítulo con hora
            state.lastCheckInAt?.let { ts ->
                Text(
                    text = when (state.wellbeing) {
                        WellbeingStatus.OK      -> "Último check-in: ${formatTime(ts)}"
                        WellbeingStatus.PENDING -> "Alarma sonó a las ${formatTime(ts)}"
                        WellbeingStatus.ALERT   -> "Alarma de las ${formatTime(ts)} sin respuesta"
                        else -> ""
                    },
                    color    = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Próxima cita médica — solo lectura, sin acciones (ver AppointmentRepository)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NextAppointmentCard(appointment: com.gutigu.alicia.data.AppointmentEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = AliciaAccentBlue.copy(alpha = 0.10f)),
        border   = androidx.compose.foundation.BorderStroke(1.dp, AliciaAccentBlue.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AliciaAccentBlue.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text("🩺", fontSize = 24.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Próxima cita médica", color = AliciaAccentBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                val who = if (appointment.specialty.isNotBlank())
                    "${appointment.doctorName} · ${appointment.specialty}" else appointment.doctorName
                Text(who, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    formatAppointmentDateTime(appointment.dateTimeMillis),
                    color = TextSecondary, fontSize = 13.sp
                )
                if (appointment.location.isNotBlank()) {
                    Text("📍 ${appointment.location}", color = TextSecondary, fontSize = 13.sp, maxLines = 1)
                }
            }
        }
    }
}

private fun formatAppointmentDateTime(millis: Long): String {
    val fmt = SimpleDateFormat("EEEE d 'de' MMMM, HH:mm", Locale("es", "CO"))
    return fmt.format(Date(millis)).replaceFirstChar { it.uppercase() }
}

// ─────────────────────────────────────────────────────────────────────────────
//  2. Línea de Vida — ítems cronológicos
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TimelineItem(activity: ActivityItem) {
    val (icon, color) = when (activity.type) {
        ActivityType.CHECK_IN    -> Icons.Default.Favorite        to Color(0xFF2ECC71)
        ActivityType.MEDICATION  -> Icons.Default.MedicalServices to Color(0xFF3498DB)
        ActivityType.APPOINTMENT -> Icons.Default.DateRange       to AliciaAccentBlue
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Línea vertical + icono
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(20.dp)
                    .background(TextSecondary.copy(alpha = 0.2f))
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f).padding(top = 2.dp)) {
            Text(activity.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(activity.description, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }

        Text(
            formatTime(activity.timestamp),
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun EmptyTimelineState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceAlt)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Sin actividades registradas hoy.\nAquí verás check-ins, medicamentos y citas médicas.",
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Fila de historial (sección inferior)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HistoryRow(record: com.gutigu.alicia.data.CheckInHistoryEntity) {
    val (tint, label) = when (record.status) {
        "RESPONDED" -> Color(0xFF2ECC71) to "Bien"
        "MISSED"    -> Color(0xFFE74C3C) to "Alerta"
        else        -> Color(0xFFF39C12) to "Pendiente"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NavyMedium)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(tint)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            formatDisplayDate(record.date),
            color = TextPrimary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(tint.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  3. Barra inferior persistente — Llamar
//
//  Antes también tenía un botón "Mensaje de voz" que solo abría un aviso de
//  "disponible próximamente" — se retiró porque prometía una función que no
//  existe (grabar y enviar un audio). Para dejar una nota de texto que Alicia
//  le lea al adulto mayor, ya existe la tarjeta "Mensaje para Alicia" más
//  arriba en este mismo dashboard, que sí funciona.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BottomActionBar(state: DashboardState, context: Context) {
    Surface(
        modifier       = Modifier.fillMaxWidth(),
        color          = NavyMedium,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Botón de llamada directa al adulto mayor
            Button(
                onClick = {
                    val phone = state.userPhone.takeIf { it.isNotBlank() } ?: return@Button
                    context.startActivity(
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape   = RoundedCornerShape(14.dp),
                colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60)),
                enabled = state.userPhone.isNotBlank()
            ) {
                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (state.userPhone.isNotBlank())
                        "Llamar a ${state.userName.ifBlank { "tu familiar" }.split(" ").first()}"
                    else "Sin teléfono",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Modo ALERTA: pantalla de emergencia roja
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AlertEmergencyScreen(state: DashboardState, context: Context) {
    val name = state.userName.ifBlank { "tu familiar" }

    // Pulso vibrante
    val infiniteTransition = rememberInfiniteTransition(label = "alert")
    val alertAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alertAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // ── Icono + mensaje ────────────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(if (state.alertSource == AlertSource.PANIC) "🆘" else "⚠️", fontSize = 80.sp)
            Text(
                text       = "ALERTA",
                color      = TextOnAlert.copy(alpha = alertAlpha),
                fontSize   = 42.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = if (state.alertSource == AlertSource.PANIC)
                    "$name activó el botón de pánico."
                else "$name no respondió el check-in de hoy.",
                color      = TextOnAlert,
                fontSize   = 18.sp,
                textAlign  = TextAlign.Center,
                lineHeight = 26.sp
            )
            state.lastCheckInAt?.let {
                if (state.alertSource != AlertSource.PANIC) {
                    Text(
                        "Alarma programada a las ${formatTime(it)}",
                        color = TextOnAlertSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // ── Botones de emergencia ──────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Llamar directamente al adulto mayor
            if (state.userPhone.isNotBlank()) {
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:${state.userPhone}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape   = RoundedCornerShape(14.dp),
                    colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60))
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Llamar a $name", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Contactos del círculo para coordinarse
            if (state.guardians.isNotEmpty()) {
                Text(
                    "Coordinar con el círculo:",
                    color = TextOnAlertSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                state.guardians.forEach { member ->
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:${member.phone}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape   = RoundedCornerShape(12.dp),
                        border  = androidx.compose.foundation.BorderStroke(
                            1.dp, TextOnAlert.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null,
                            tint = TextOnAlert, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(member.name, fontSize = 14.sp, color = TextOnAlert)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Utilidades
// ─────────────────────────────────────────────────────────────────────────────

private fun formatTime(ts: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))

private fun todayStr(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

private fun formatDisplayDate(dateStr: String): String = try {
    val parsed    = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr) ?: return dateStr
    val today     = todayStr()
    val yesterday = run {
        val c = java.util.Calendar.getInstance()
        c.add(java.util.Calendar.DATE, -1)
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.time)
    }
    when (dateStr) {
        today     -> "Hoy"
        yesterday -> "Ayer"
        else      -> SimpleDateFormat("d 'de' MMMM", Locale.Builder().setLanguage("es").build()).format(parsed)
    }
} catch (e: Exception) { dateStr }

// ─────────────────────────────────────────────────────────────────────────────
//  4. Mensaje a Alicia — el familiar le deja una nota al adulto mayor
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SendNoteToAliciaCard(onClick: () -> Unit) {
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = PurpleFamiliar.copy(alpha = 0.10f)),
        border    = androidx.compose.foundation.BorderStroke(1.dp, PurpleFamiliarLight.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(PurpleFamiliar.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                Text("💌", fontSize = 26.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Mensaje para Alicia",
                    color = PurpleFamiliarLight,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Deja un aviso que Alicia le dirá a tu familiar",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
            Icon(
                Icons.Default.Edit,
                contentDescription = null,
                tint = PurpleFamiliarLight.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SendNoteDialog(
    userName: String,
    onDismiss: () -> Unit,
    onSend: (content: String, authorName: String) -> Unit
) {
    var content    by remember { mutableStateOf("") }
    var authorName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = NavyMedium,
        titleContentColor = TextPrimary,
        textContentColor  = TextSecondary,
        icon = { Text("💌", fontSize = 36.sp) },
        title = {
            Text(
                "Mensaje para $userName",
                fontWeight = FontWeight.Bold,
                fontSize   = 20.sp,
                textAlign  = TextAlign.Center,
                modifier   = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Alicia leerá este mensaje en voz alta a tu familiar.",
                    fontSize   = 14.sp,
                    lineHeight = 20.sp,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = content, onValueChange = { content = it },
                    label = { Text("Mensaje", color = TextSecondary) },
                    placeholder = { Text("Ej: Hoy pasan a recoger la ropa a las 3pm", color = TextSecondary.copy(alpha = 0.5f)) },
                    minLines = 3, maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PurpleFamiliar,
                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.4f),
                        focusedLabelColor = PurpleFamiliarLight,
                        cursorColor = PurpleFamiliar,
                        focusedContainerColor = SurfaceAlt,
                        unfocusedContainerColor = SurfaceAlt
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 16.sp)
                )

                OutlinedTextField(
                    value = authorName, onValueChange = { authorName = it },
                    label = { Text("Tu nombre (opcional)", color = TextSecondary) },
                    placeholder = { Text("Ej: María (tu hija)", color = TextSecondary.copy(alpha = 0.5f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PurpleFamiliar,
                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.4f),
                        focusedLabelColor = PurpleFamiliarLight,
                        cursorColor = PurpleFamiliar,
                        focusedContainerColor = SurfaceAlt,
                        unfocusedContainerColor = SurfaceAlt
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 16.sp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick  = { if (content.isNotBlank()) onSend(content, authorName.ifBlank { "Tu familiar" }) },
                enabled  = content.isNotBlank(),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = PurpleFamiliar)
            ) {
                Text("📤 Enviar a Alicia", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextSecondary)
            }
        }
    )
}
