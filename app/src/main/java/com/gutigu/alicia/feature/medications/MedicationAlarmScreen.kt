package com.gutigu.alicia.feature.medications

import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import com.gutigu.alicia.ui.theme.AliciaAccent
import com.gutigu.alicia.ui.theme.AliciaAccentSoft
import com.gutigu.alicia.ui.theme.AliciaBackground
import com.gutigu.alicia.ui.theme.AliciaSurfaceAlt
import com.gutigu.alicia.ui.theme.AliciaText
import com.gutigu.alicia.ui.theme.AliciaTextSecondary
import java.util.Locale

// ── Paleta, centralizada en ui/theme/Color.kt donde coincide ──────────────────

private val BgNormal   = AliciaBackground   // claro — alarma normal
private val BgUrgent   = Color(0xFFFBDAD3)   // rojo suave — urgencia, últimos 30s
private val BgTaken    = AliciaAccentSoft    // verde suave — confirmado
private val SurfaceAlt  = AliciaSurfaceAlt
private val BorderGreen = AliciaAccent
private val TextPrimary = AliciaText
private val TextMuted   = AliciaTextSecondary
private val OrangeFood  = Color(0xFFB9770E)

// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MedicationAlarmScreen(
    medId: Int,
    medName: String,
    medDose: String,
    photoUrl: String?,
    withFood: Boolean,
    note: String,
    viewModel: MedicationAlarmViewModel = hiltViewModel(),
    onFinish: () -> Unit
) {
    val isTaken    by viewModel.isTaken.collectAsState()
    val isExpired  by viewModel.isExpired.collectAsState()
    val countdown  by viewModel.countdown.collectAsState()

    val context = LocalContext.current
    val ttsState = remember { mutableStateOf<TextToSpeech?>(null) }

    // ── TTS: saludo al aparecer la pantalla ──────────────────────────────────
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.Builder().setLanguage("es").setRegion("CO").build()
                val foodHint = if (withFood) ", recuerda tomarla con comida" else ""
                val noteHint = if (note.isNotBlank()) ". $note" else ""
                val msg = "Hola, es hora de tomar $medName" +
                    (if (medDose.isNotBlank()) ", $medDose" else "") +
                    "$foodHint$noteHint."
                engine?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "med_greeting")
            }
        }
        ttsState.value = engine
        onDispose { engine?.shutdown() }
    }

    // ── Iniciar countdown una sola vez ────────────────────────────────────────
    LaunchedEffect(Unit) {
        viewModel.startCountdown(medId, medName)
    }

    // ── Cierre automático tras tomar / expirar ─────────────────────────────────
    LaunchedEffect(isTaken) {
        if (isTaken) {
            ttsState.value?.speak(
                "¡Perfecto! Ya registré que tomaste $medName. ¡Que te haga bien!",
                TextToSpeech.QUEUE_FLUSH, null, "med_taken"
            )
            delay(2_500)
            onFinish()
        }
    }
    LaunchedEffect(isExpired) {
        if (isExpired) {
            ttsState.value?.speak(
                "No recibí tu confirmación. Le avisé a tu familiar.",
                TextToSpeech.QUEUE_FLUSH, null, "med_expired"
            )
            delay(3_000)
            onFinish()
        }
    }

    // ── Animaciones ────────────────────────────────────────────────────────────
    val isUrgent = countdown < 30 && !isTaken && !isExpired
    val bgColor by animateColorAsState(
        targetValue = when {
            isTaken   -> BgTaken
            isExpired -> BgUrgent
            isUrgent  -> BgUrgent
            else      -> BgNormal
        },
        animationSpec = tween(600),
        label = "bg"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pill_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (isTaken) {
            TakenScreen(medName)
        } else if (isExpired) {
            ExpiredScreen()
        } else {
            ActiveAlarmContent(
                medName   = medName,
                medDose   = medDose,
                photoUrl  = photoUrl,
                withFood  = withFood,
                note      = note,
                countdown = countdown,
                isUrgent  = isUrgent,
                pulseScale = pulseScale,
                onTaken   = { viewModel.confirmTaken(medId) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Contenido principal de la alarma
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActiveAlarmContent(
    medName: String,
    medDose: String,
    photoUrl: String?,
    withFood: Boolean,
    note: String,
    countdown: Int,
    isUrgent: Boolean,
    pulseScale: Float,
    onTaken: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // ── Cabecera ─────────────────────────────────────────────────────────
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "💊",
                fontSize = 40.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Es hora de tu medicina",
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        // ── Foto grande de la pastilla ────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(200.dp)
                .scale(pulseScale),
            contentAlignment = Alignment.Center
        ) {
            if (photoUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(photoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Foto de $medName",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                        .border(4.dp, BorderGreen, CircleShape)
                )
            } else {
                // Icono de respaldo si no hay foto
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                        .background(SurfaceAlt)
                        .border(4.dp, BorderGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("💊", fontSize = 90.sp)
                }
            }
        }

        // ── Nombre y dosis ────────────────────────────────────────────────────
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                medName,
                color = BorderGreen,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            if (medDose.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    medDose,
                    color = TextMuted,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )
            }
            if (withFood) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("🍽️", fontSize = 22.sp)
                    Text(
                        "Tómala con comida",
                        color = OrangeFood,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (note.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    note,
                    color = TextMuted,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // ── Botón gigante ─────────────────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onTaken,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isUrgent) 90.dp else 76.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BorderGreen)
            ) {
                Text(
                    "✅  YA LA TOMÉ",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0D1B2A)
                )
            }

            // Cuenta regresiva — se vuelve roja al final
            Text(
                if (isUrgent) "⏳ Quedan $countdown s" else "Tiempo: $countdown s",
                color = if (isUrgent) Color(0xFFD63A1F) else TextMuted,
                fontSize = 15.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Pantalla de confirmación (¡tomada!)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TakenScreen(medName: String) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + scaleIn(initialScale = 0.8f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFFE3F9EC), Color(0xFFFAF8FB))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Text("✅", fontSize = 96.sp)
                Text(
                    "¡Perfecto!",
                    color = Color(0xFF1B5E20),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "$medName registrado",
                    color = Color(0xFF1B5E20),
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Tu familiar puede ver que lo tomaste 💚",
                    color = TextMuted,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Pantalla de tiempo agotado
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExpiredScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFFFBDAD3), Color(0xFFFAF8FB))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text("⏰", fontSize = 80.sp)
            Text(
                "Tiempo agotado",
                color = Color(0xFFD63A1F),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Tu familiar ha sido notificado.\nEsta pantalla se cerrará pronto.",
                color = TextMuted,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                lineHeight = 26.sp
            )
        }
    }
}
