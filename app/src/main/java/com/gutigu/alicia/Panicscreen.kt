package com.gutigu.alicia

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.gutigu.alicia.ui.theme.AliciaAccentSoft
import com.gutigu.alicia.ui.theme.AliciaAlert
import com.gutigu.alicia.ui.theme.AliciaBackground
import com.gutigu.alicia.ui.theme.AliciaText
import com.gutigu.alicia.ui.theme.AliciaTextSecondary

// ─────────────────────────────────────────────
//  PALETA DE COLORES — centralizada en ui/theme/Color.kt donde coincide;
//  el rojo del botón SOS y sus derivados son deliberadamente propios de esta
//  pantalla (ver nota "Botón SOS" abajo).
// ─────────────────────────────────────────────

private object PanicColors {
    // Fondos por fase — spec v2: claro en reposo, la única excepción de color fuerte
    // es la emergencia activa real (backgroundDanger), nunca decorativo.
    val backgroundSafe = AliciaBackground
    val backgroundDanger = AliciaAlert
    val backgroundSendingTint = AliciaAccentSoft

    // Texto sobre fondo claro (idle, countdown, enviando, resuelto, error)
    val textOnLight = AliciaText
    val textOnLightSecondary = AliciaTextSecondary

    // Texto sobre fondo rojo (única pantalla con fondo oscuro/saturado: alerta activa)
    val textOnDanger = Color(0xFFFFFFFF)

    // Botón SOS — siempre rojo, sin importar el fondo detrás; distinto a propósito
    // de AliciaAlert (E8342A), que queda reservado al fondo de la alerta ya activa.
    val buttonRed = Color(0xFFE53935)
    val buttonRedDark = Color(0xFFB71C1C)
    val cancelButton = AliciaTextSecondary
    val resolveGreen = Color(0xFF2E7D32)
    val cardOnDanger = Color(0x26FFFFFF)
    val pulseRing = AliciaAlert.copy(alpha = 0.12f)
    val warningStrong = Color(0xFFB9770E)
}

// ─────────────────────────────────────────────
//  PANTALLA PRINCIPAL
// ─────────────────────────────────────────────

@Composable
fun PanicScreen(
    viewModel: PanicViewModel = hiltViewModel()
) {
    val panicState by viewModel.panicState.collectAsState()
    val hasNoContacts by viewModel.hasNoContacts.collectAsState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    // Sin estos permisos en tiempo de ejecución, el SMS y la ubicación fallan
    // en silencio (ver Panicrepository) — se piden justo al presionar SOS en
    // vez de durante el onboarding, para no interrumpir con diálogos antes
    // de que el usuario realmente necesite la función.
    val panicPermissions = arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.ACCESS_FINE_LOCATION)
    fun hasPanicPermissions() = panicPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    val panicPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Se continúa aunque el usuario niegue alguno: el envío ya maneja con
        // gracia la falta de SMS o de ubicación (ver ActiveContent/StatusRow).
        viewModel.startPanicSequence()
    }
    val onPanicPress: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (hasPanicPermissions()) {
            viewModel.startPanicSequence()
        } else {
            panicPermissionLauncher.launch(panicPermissions)
        }
    }

    // El botón/gesto Atrás no debe poder sacar al usuario de la secuencia de SOS en
    // silencio. En Countdown ya existe un "cancelar" explícito (el botón CANCELAR) —
    // Atrás hace lo mismo, en vez de solo desaparecer de la vista. En Sending/Active
    // no hay una acción de "cancelar" (la alerta ya se está enviando o ya se envió):
    // Atrás se absorbe sin hacer nada, la única salida es resolver con los botones
    // explícitos "Estoy bien" / "Falsa alarma".
    BackHandler(enabled = panicState is PanicState.Countdown) {
        viewModel.cancelPanic()
    }
    BackHandler(enabled = panicState is PanicState.Sending || panicState is PanicState.Active) { }

    val backgroundColor by animateColorAsState(
        targetValue = when (panicState) {
            is PanicState.Active -> PanicColors.backgroundDanger
            is PanicState.Sending -> PanicColors.backgroundSendingTint
            else -> PanicColors.backgroundSafe
        },
        animationSpec = tween(600),
        label = "bgColor"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = panicState,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "panicContent"
        ) { state ->
            when (state) {
                is PanicState.Idle -> IdleContent(
                    hasNoContacts = hasNoContacts,
                    onPanicPress = onPanicPress
                )
                is PanicState.Countdown -> CountdownContent(
                    seconds = state.secondsRemaining,
                    onCancel = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.cancelPanic()
                    }
                )
                is PanicState.Sending -> SendingContent()
                is PanicState.Active -> ActiveContent(
                    alertId = state.alertId,
                    smsSentCount = state.smsSentCount,
                    smsTotalCount = state.smsTotalCount,
                    locationShared = state.locationShared,
                    onImOkay = { viewModel.resolveAlert(isFalseAlarm = false) },
                    onFalseAlarm = { viewModel.resolveAlert(isFalseAlarm = true) }
                )
                is PanicState.Resolved -> ResolvedContent()
                is PanicState.Error -> ErrorContent(message = state.message)
            }
        }
    }
}

// ─────────────────────────────────────────────
//  ESTADO: IDLE — Botón de Pánico Principal
// ─────────────────────────────────────────────

@Composable
private fun IdleContent(hasNoContacts: Boolean, onPanicPress: () -> Unit) {
    // Animación de pulso en el anillo exterior
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    var isPressed by remember { mutableStateOf(false) }
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "buttonScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(40.dp)
    ) {
        // Título
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "CÍRCULO DE",
                color = PanicColors.textOnLightSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.W400,
                letterSpacing = 4.sp
            )
            Text(
                text = "CONFIANZA",
                color = PanicColors.textOnLight,
                fontFamily = MaterialTheme.typography.headlineLarge.fontFamily,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }

        // Botón de Pánico con anillos de pulso
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(240.dp)
        ) {
            // Anillo de pulso exterior
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(PanicColors.pulseRing)
            )
            // Anillo medio
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(Color(0x14E8342A))
            )
            // Botón principal — MUY GRANDE para adultos mayores
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(180.dp)
                    .scale(buttonScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(PanicColors.buttonRed, PanicColors.buttonRedDark)
                        )
                    )
                    .border(3.dp, Color(0x88FFFFFF), CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                tryAwaitRelease()
                                isPressed = false
                            },
                            onTap = { onPanicPress() }
                        )
                    }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "🆘", fontSize = 48.sp)
                    Text(
                        text = "AYUDA",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 3.sp
                    )
                }
            }
        }

        // Instrucción
        Text(
            text = "Toca para alertar a tu círculo",
            color = PanicColors.textOnLightSecondary,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )

        if (hasNoContacts) {
            Text(
                text = "⚠️ Todavía no tienes a nadie en tu círculo — agrega a un familiar para que esta alerta le llegue a alguien",
                color = PanicColors.warningStrong,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────
//  ESTADO: COUNTDOWN — Cancelar antes de disparar
// ─────────────────────────────────────────────

@Composable
private fun CountdownContent(seconds: Int, onCancel: () -> Unit) {
    val progress by animateFloatAsState(
        targetValue = seconds / PanicViewModel.COUNTDOWN_SECONDS.toFloat(),
        animationSpec = tween(800),
        label = "countdownProgress"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp),
        modifier = Modifier.padding(32.dp)
    ) {
        Text(
            text = "Enviando alerta en...",
            color = PanicColors.textOnLightSecondary,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )

        // Contador circular grande
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = PanicColors.buttonRed,
                trackColor = Color(0x1F000000),
                strokeWidth = 8.dp,
            )
            Text(
                text = "$seconds",
                color = PanicColors.textOnLight,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Botón de cancelar — también muy grande
        Button(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PanicColors.cancelButton
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = "CANCELAR",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }

        Text(
            text = "Toca CANCELAR si estás bien",
            color = PanicColors.textOnLightSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────
//  ESTADO: SENDING — Enviando alertas
// ─────────────────────────────────────────────

@Composable
private fun SendingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(80.dp),
            color = Color(0xFF2E7D32),
            strokeWidth = 6.dp
        )
        Text(
            text = "Alertando a tu círculo...",
            color = PanicColors.textOnLight,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Obteniendo tu ubicación\ny notificando a familia",
            color = PanicColors.textOnLightSecondary,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
    }
}

// ─────────────────────────────────────────────
//  ESTADO: ACTIVE — Alerta enviada
// ─────────────────────────────────────────────

@Composable
private fun ActiveContent(
    alertId: String,
    smsSentCount: Int,
    smsTotalCount: Int,
    locationShared: Boolean,
    onImOkay: () -> Unit,
    onFalseAlarm: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "activePulse")
    val blink by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink"
    )

    // No prometemos más de lo que realmente pasó: si nadie recibió el SMS,
    // esta pantalla lo dice claro en vez de mostrar un "enviado" genérico.
    val noneReached = smsSentCount == 0
    val allReached = smsTotalCount > 0 && smsSentCount == smsTotalCount

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Indicador de alerta activa
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .scale(blink)
                .size(100.dp)
                .clip(CircleShape)
                .background(Color(0x44FFFFFF))
        ) {
            Text(text = "🆘", fontSize = 56.sp)
        }

        Text(
            text = if (noneReached) "ALERTA REGISTRADA" else "¡ALERTA ENVIADA!",
            color = PanicColors.textOnDanger,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center
        )

        // Tarjeta de estado — refleja lo que de verdad pasó con el SMS y la ubicación
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = PanicColors.cardOnDanger),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusRow(
                    emoji = if (allReached) "📱" else "⚠️",
                    text = when {
                        smsTotalCount == 0 -> "Tu círculo está vacío: nadie fue avisado"
                        noneReached -> "No se pudo avisar a tu círculo por SMS"
                        !allReached -> "Avisamos a $smsSentCount de $smsTotalCount personas"
                        else -> "Tu círculo fue notificado"
                    }
                )
                StatusRow(
                    emoji = if (locationShared) "📍" else "❔",
                    text = if (locationShared) "Ubicación compartida" else "Ubicación no disponible"
                )
            }
        }

        if (noneReached) {
            Text(
                text = "⚠️ Llama directamente al\n123 o a un familiar",
                color = PanicColors.textOnDanger,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 26.sp
            )
        }

        Spacer(Modifier.height(8.dp))

        // Botones de resolución
        Text(
            text = "¿Ya estás bien?",
            color = PanicColors.textOnDanger,
            fontSize = 16.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Falsa alarma
            OutlinedButton(
                onClick = onFalseAlarm,
                modifier = Modifier.weight(1f).height(60.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Falsa alarma", fontSize = 14.sp)
            }

            // Estoy bien
            Button(
                onClick = onImOkay,
                modifier = Modifier.weight(1f).height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PanicColors.resolveGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Estoy bien", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─────────────────────────────────────────────
//  ESTADO: RESOLVED — Confirmación
// ─────────────────────────────────────────────

@Composable
private fun ResolvedContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(text = "✅", fontSize = 72.sp)
        Text(
            text = "Tu círculo fue\navisado que estás bien",
            color = PanicColors.textOnLight,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 32.sp
        )
        Text(
            text = "Gracias por confirmar",
            color = PanicColors.textOnLightSecondary,
            fontSize = 16.sp
        )
    }
}

// ─────────────────────────────────────────────
//  ESTADO: ERROR
// ─────────────────────────────────────────────

@Composable
private fun ErrorContent(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(32.dp)
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = PanicColors.warningStrong
        )
        Text(
            text = "No se pudo enviar la alerta",
            color = PanicColors.textOnLight,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = message,
            color = PanicColors.textOnLightSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = "⚠️ Llama directamente al\n123 o a un familiar",
            color = PanicColors.warningStrong,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 26.sp
        )
    }
}

// ─────────────────────────────────────────────
//  COMPONENTES REUTILIZABLES
// ─────────────────────────────────────────────

@Composable
private fun StatusRow(emoji: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = emoji, fontSize = 20.sp)
        Text(
            text = text,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
