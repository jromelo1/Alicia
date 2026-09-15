package com.gutigu.alicia.feature.checkin

import android.Manifest
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.gutigu.alicia.ui.theme.AliciaAccent
import com.gutigu.alicia.ui.theme.AliciaAccentSoft
import com.gutigu.alicia.ui.theme.AliciaAlert
import com.gutigu.alicia.ui.theme.AliciaBackground
import com.gutigu.alicia.ui.theme.AliciaLine
import com.gutigu.alicia.ui.theme.AliciaSurface
import com.gutigu.alicia.ui.theme.AliciaSurfaceAlt
import com.gutigu.alicia.ui.theme.AliciaText
import com.gutigu.alicia.ui.theme.AliciaTextSecondary
import com.gutigu.alicia.ui.theme.AliciaWarning

// ─── Paleta, centralizada en ui/theme/Color.kt donde coincide ────────────────
private val NavyDark         = AliciaBackground   // fondo de pantalla (nombre heredado, valor claro)
private val NavyMedium       = AliciaSurface      // superficie de tarjeta blanca
private val SurfaceAlt       = AliciaSurfaceAlt   // superficie secundaria / chip inactivo
private val LineColor        = AliciaLine
private val AccentGreen      = AliciaAccent
private val AccentAmber      = AliciaWarning
private val AccentRed        = AliciaAlert
private val TextPrimary      = AliciaText
private val TextSecondary    = AliciaTextSecondary
// Fondos por fase de la Confirmación Calmada — tintes claros, nunca oscuros
private val BgAwakening     = Color(0xFFFDF3E3)   // ámbar muy suave
private val BgMain          = Color(0xFFFBEAC8)   // ámbar más presente
private val BgFinalWarning  = Color(0xFFFBDAD3)   // rojo suave — urgencia, todavía no es la alerta roja del SOS
private val BgResponded     = AliciaAccentSoft    // verde suave — éxito

@Composable
fun CheckInScreen(viewModel: CheckInViewModel = hiltViewModel()) {
    val uiState          by viewModel.uiState.collectAsState()
    val countdownSeconds by viewModel.countdownSeconds.collectAsState()
    val alarmPhase       by viewModel.alarmPhase.collectAsState()
    val respondedHappily by viewModel.respondedHappily.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDark)
    ) {
        when (val state = uiState) {
            is CheckInUiState.Loading -> CircularProgressIndicator(
                color = AccentGreen,
                modifier = Modifier.align(Alignment.Center)
            )

            is CheckInUiState.Ready -> {
                val isPending = state.todayRecord?.status == CheckInStatus.PENDING

                // Con la alarma activa, el botón/gesto Atrás no debe poder sacar al
                // usuario de la pantalla en silencio: la cuenta regresiva sigue corriendo
                // en el ViewModel aunque la vista desaparezca, y su familia terminaría
                // recibiendo un aviso de "no respondió" mientras él sigue con el teléfono
                // en la mano. Sin acción de "cancelar" — la única salida es responder.
                BackHandler(enabled = isPending) { }

                AnimatedContent(targetState = isPending, label = "checkin_mode") { pending ->
                    if (pending) {
                        AlarmActiveScreen(
                            config           = state.config,
                            countdownSeconds = countdownSeconds,
                            alarmPhase       = alarmPhase,
                            respondedHappily = respondedHappily,
                            onRespond        = { viewModel.respondNow() },
                            onCountdownStart = { viewModel.startInAppCountdown() }
                        )
                    } else {
                        CheckInSettingsScreen(state = state, viewModel = viewModel)
                    }
                }
            }
        }
    }
}

// ─── Pantalla de alarma activa — Confirmación Calmada ────────────────────

@Composable
private fun AlarmActiveScreen(
    config: CheckInConfig,
    countdownSeconds: Int,
    alarmPhase: AlarmPhase,
    respondedHappily: Boolean,
    onRespond: () -> Unit,
    onCountdownStart: () -> Unit
) {
    val context = LocalContext.current

    // ── TTS ────────────────────────────────────────────────────────────────
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.Builder().setLanguage("es").setRegion("CO").build()
        }
        onDispose { tts?.shutdown() }
    }

    // ── Vibración suave de activación ─────────────────────────────────────
    DisposableEffect(Unit) {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            (context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE)
                    as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
        }
        // Patrón suave: 3 pulsos cortos
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 120, 100, 120, 100, 120), -1))
        onDispose { vibrator.cancel() }
    }

    // ── Sonido latido (ToneGenerator con volumen creciente) ───────────────
    var toneVolume by remember { mutableStateOf(30) }
    DisposableEffect(alarmPhase) {
        if (alarmPhase == AlarmPhase.MAIN || alarmPhase == AlarmPhase.FINAL_WARNING) {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, toneVolume.coerceAtMost(100))
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 180)
            return@DisposableEffect onDispose { tone.release() }
        }
        onDispose { }
    }

    // ── Arrancar countdown al aparecer ────────────────────────────────────
    LaunchedEffect(Unit) {
        onCountdownStart()
        kotlinx.coroutines.delay(600)
        tts?.speak(
            "Hola, hace un rato que no nos saludamos. ¿Estás por ahí?",
            TextToSpeech.QUEUE_FLUSH, null, "awakening"
        )
    }

    // Hablar advertencia cuando entra en la fase final
    LaunchedEffect(alarmPhase) {
        if (alarmPhase == AlarmPhase.FINAL_WARNING) {
            tts?.speak(
                "Voy a avisar a tu familia en 10 segundos para que sepa que no respondes. " +
                        "Presiona el botón si todo está en orden.",
                TextToSpeech.QUEUE_FLUSH, null, "warning"
            )
        }
    }

    // Hablar mensaje de cierre al responder
    LaunchedEffect(respondedHappily) {
        if (respondedHappily) {
            kotlinx.coroutines.delay(300)
            tts?.speak(
                "¡Perfecto! Seguimos conectados. Que tengas un lindo día.",
                TextToSpeech.QUEUE_FLUSH, null, "ok"
            )
        }
    }

    // ── Color de fondo animado por fase ───────────────────────────────────
    val bgColor by animateColorAsState(
        targetValue = when {
            respondedHappily                  -> BgResponded
            alarmPhase == AlarmPhase.FINAL_WARNING -> BgFinalWarning
            alarmPhase == AlarmPhase.MAIN     -> BgMain
            else                              -> BgAwakening
        },
        animationSpec = tween(1200),
        label = "bg_phase"
    )

    // Aumentar volumen del latido gradualmente
    LaunchedEffect(countdownSeconds) {
        toneVolume = 30 + ((ALARM_COUNTDOWN_SECONDS - countdownSeconds) * 2).coerceAtMost(70)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        if (respondedHappily) {
            RespondedHappilyScreen(name = config.userName)
        } else {
            CalmConfirmationContent(
                config           = config,
                countdownSeconds = countdownSeconds,
                alarmPhase       = alarmPhase,
                onRespond        = onRespond
            )
        }
    }
}

// ─── Contenido de las 3 fases ─────────────────────────────────────────────

@Composable
private fun CalmConfirmationContent(
    config: CheckInConfig,
    countdownSeconds: Int,
    alarmPhase: AlarmPhase,
    onRespond: () -> Unit
) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val timeEmoji = when { hour < 12 -> "🌅"; hour < 19 -> "🌤️"; else -> "🌙" }
    val amberGlow = Color(0xFFF39C12)

    // Animación de latido sobre el botón (fase MAIN y FINAL_WARNING)
    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")
    val heartbeatScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = if (alarmPhase == AlarmPhase.MAIN || alarmPhase == AlarmPhase.FINAL_WARNING) 1.04f else 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(520, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartbeat_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // ── Parte superior ─────────────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 56.dp)
        ) {
            // Fase AWAKENING: saludo grande
            AnimatedVisibility(
                visible = alarmPhase == AlarmPhase.AWAKENING,
                enter   = fadeIn(tween(600)) + expandVertically()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(timeEmoji, fontSize = 72.sp)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Hola, ${config.userName}",
                        color      = amberGlow,
                        fontFamily = MaterialTheme.typography.headlineLarge.fontFamily,
                        fontSize   = 32.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign  = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "¿Estás por ahí?",
                        color      = TextPrimary,
                        fontSize   = 24.sp,
                        textAlign  = TextAlign.Center,
                        lineHeight = 32.sp
                    )
                }
            }

            // Fase FINAL_WARNING: cuenta regresiva gigante
            AnimatedVisibility(
                visible = alarmPhase == AlarmPhase.FINAL_WARNING,
                enter   = fadeIn(tween(400))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text       = "$countdownSeconds",
                        color      = Color(0xFFD63A1F),
                        fontSize   = 110.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "segundos para avisar\na tu familia",
                        color      = Color(0xFFA84B33),
                        fontSize   = 18.sp,
                        textAlign  = TextAlign.Center,
                        lineHeight = 26.sp
                    )
                }
            }

            // Fase MAIN: icono de corazón animado
            AnimatedVisibility(
                visible = alarmPhase == AlarmPhase.MAIN,
                enter   = fadeIn(tween(400))
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(amberGlow.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = null,
                        tint     = amberGlow,
                        modifier = Modifier.size(44.dp).scale(heartbeatScale)
                    )
                }
            }
        }

        // ── Botón gigante (60 % de la pantalla en fase MAIN/WARNING) ───────
        val buttonHeight by animateDpAsState(
            targetValue = when (alarmPhase) {
                AlarmPhase.MAIN, AlarmPhase.FINAL_WARNING -> 200.dp
                else -> 100.dp
            },
            animationSpec = tween(700),
            label = "btn_height"
        )
        val buttonFontSize by animateFloatAsState(
            targetValue = when (alarmPhase) {
                AlarmPhase.MAIN, AlarmPhase.FINAL_WARNING -> 36f
                else -> 28f
            },
            animationSpec = tween(700),
            label = "btn_font"
        )

        Button(
            onClick = onRespond,
            modifier = Modifier
                .fillMaxWidth()
                .height(buttonHeight)
                .scale(if (alarmPhase == AlarmPhase.MAIN) heartbeatScale else 1f),
            shape  = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = if (alarmPhase == AlarmPhase.MAIN) 12.dp else 4.dp
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint     = TextPrimary,
                    modifier = Modifier.size(if (alarmPhase == AlarmPhase.MAIN) 48.dp else 32.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "SÍ, ESTOY BIEN",
                    color      = TextPrimary,
                    fontSize   = buttonFontSize.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign  = TextAlign.Center
                )
            }
        }

        // ── Barra de progreso inferior ──────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (alarmPhase == AlarmPhase.AWAKENING) {
                Text(
                    "Presiona si estás bien",
                    color    = TextSecondary,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            // Barra ámbar que se agota
            val progress by animateFloatAsState(
                targetValue    = countdownSeconds / ALARM_COUNTDOWN_SECONDS.toFloat(),
                animationSpec  = tween(800),
                label          = "progress"
            )
            val barColor = when {
                countdownSeconds > 15 -> amberGlow
                countdownSeconds > 7  -> Color(0xFFE67E22)
                else                  -> Color(0xFFE74C3C)
            }
            LinearProgressIndicator(
                progress      = { progress },
                modifier      = Modifier.fillMaxWidth().height(8.dp),
                color         = barColor,
                trackColor    = SurfaceAlt
            )
        }
    }
}

// ─── Pantalla de respuesta exitosa ────────────────────────────────────────

@Composable
private fun RespondedHappilyScreen(name: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "sparkle")
    val sparkleAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.6f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label         = "sparkle_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFFE3F9EC), Color(0xFFFAF8FB))
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
                text       = "¡Perfecto, $name!",
                color      = AccentGreen.copy(alpha = sparkleAlpha),
                fontSize   = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign  = TextAlign.Center
            )

            Text(
                text      = "Seguimos conectados.\nQue tengas un lindo día.",
                color     = TextPrimary,
                fontSize  = 20.sp,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp
            )

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AccentGreen.copy(alpha = 0.12f))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    "Tu familia ya sabe que estás bien 💚",
                    color    = AccentGreen,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ─── Pantalla de configuración ────────────────────────────────────────────

@Composable
private fun CheckInSettingsScreen(
    state: CheckInUiState.Ready,
    viewModel: CheckInViewModel
) {
    val hasNoContacts by viewModel.hasNoContacts.collectAsState()
    val context = LocalContext.current

    var hasNotificationPermission by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }

    // El permiso se puede activar/desactivar desde Ajustes del sistema sin pasar por
    // esta pantalla — se vuelve a revisar cada vez que la app regresa a primer plano,
    // no solo justo después de pedirlo.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasNotificationPermission = granted }

    // Se pide una sola vez, apenas se detecta que falta — sin este permiso (obligatorio
    // desde Android 13) la alarma diaria no suena ni aparece, y la app nunca lo pedía.
    LaunchedEffect(Unit) {
        if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Check-in de Bienestar",
            color = TextPrimary,
            fontFamily = MaterialTheme.typography.headlineLarge.fontFamily,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Una vez al día recibirás una alarma con saludo.",
            color = TextSecondary,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (!hasNotificationPermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AccentAmber.copy(alpha = 0.12f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Sin notificaciones activadas no vas a escuchar tu alarma de bienestar ni la de tus medicamentos.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            )
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Activar en Ajustes", color = AccentAmber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        TodayStatusCard(state.todayRecord)

        Spacer(modifier = Modifier.height(20.dp))

        if (hasNoContacts) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AccentAmber.copy(alpha = 0.12f))
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Todavía no tienes a nadie en tu círculo. Agrega al menos un familiar en la pestaña Círculo antes de activar la alarma, o no le avisará a nadie.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // El horario (activar/desactivar, hora, umbral de aviso) lo define el
        // Familiar desde su propia pestaña de Bienestar — aquí solo se muestra,
        // ya sincronizado por Firestore (ver CheckInViewModel.observeRemoteSchedule).
        SettingsCard {
            Column {
                Text("Chequeo diario", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Tu familia configura este horario desde su app",
                    color = TextSecondary, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
                )
                if (state.config.isEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Activo — recibirás el saludo a las %02d:%02d".format(state.config.scheduledHour, state.config.scheduledMinute),
                            color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Si no abres la app en ${state.config.timeoutMinutes / 60} horas, tu familia recibirá un aviso.",
                        color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Tu familia aún no ha activado el chequeo diario",
                            color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Puede activarlo desde su propia pestaña Bienestar.",
                        color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsCard {
            Column {
                Text("Tu número de teléfono", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Para que tu familia pueda llamarte directamente", color = TextSecondary, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                OutlinedTextField(
                    value = state.config.userPhone,
                    onValueChange = { viewModel.updateConfig(state.config.copy(userPhone = it)) },
                    placeholder = { Text("Ej. 5512345678", color = TextSecondary.copy(alpha = 0.5f)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                    ),
                    // Se guarda solo (sin botón) en cada cambio — este check confirma
                    // visualmente que el número ya quedó registrado, para que no
                    // parezca que el campo "no hace nada".
                    trailingIcon = {
                        if (state.config.userPhone.length >= 7) {
                            Icon(Icons.Default.Check, contentDescription = "Guardado", tint = AccentGreen)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGreen,
                        focusedLabelColor = AccentGreen,
                        cursorColor = AccentGreen,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        unfocusedBorderColor = LineColor
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 18.sp)
                )
                if (state.config.userPhone.length >= 7) {
                    Text(
                        "✓ Guardado — tu familia ya puede ver este número",
                        color = AccentGreen,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Nota informativa sobre el countdown de 30s
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = AccentAmber.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Al abrir la app cuando suene la alarma, tendrás 30 segundos para tocar \"¡Estoy bien!\". Si no respondes, se enviará un SMS a tu grupo familiar.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { viewModel.triggerNow() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NavyMedium)
        ) {
            Text("Probar alarma ahora", color = AccentGreen, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ─── Estado de hoy ────────────────────────────────────────────────────────

@Composable
private fun TodayStatusCard(record: CheckInRecord?) {
    val (icon, tint, title, subtitle) = when (record?.status) {
        CheckInStatus.RESPONDED -> CheckInStatusDisplay(
            Icons.Default.Check, AccentGreen,
            "Respondiste hoy",
            record.respondedAt?.let { "A las ${formatTime(it)}" } ?: ""
        )
        CheckInStatus.MISSED -> CheckInStatusDisplay(
            Icons.Default.Warning, Color(0xFFFF8C00),
            "Sin respuesta hoy",
            "Tu familia recibió un aviso. Espero que estés bien."
        )
        CheckInStatus.PENDING -> CheckInStatusDisplay(
            Icons.Default.Warning, AccentAmber,
            "Alarma pendiente",
            "Aún no has respondido el check-in de hoy"
        )
        null -> CheckInStatusDisplay(
            Icons.Default.Check, TextSecondary,
            "Sin alarma hoy aún",
            "Recibirás la alarma a la hora configurada"
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyMedium)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (subtitle.isNotEmpty()) Text(subtitle, color = TextSecondary, fontSize = 14.sp)
            }
        }
    }
}

private data class CheckInStatusDisplay(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color,
    val title: String,
    val subtitle: String
)

// ─── Ajustador de hora ────────────────────────────────────────────────────

@Composable
internal fun TimeAdjuster(hour: Int, minute: Int, enabled: Boolean, onAdjust: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalButton(
            onClick = { onAdjust(-30) }, enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(width = 80.dp, height = 52.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = SurfaceAlt, contentColor = AccentGreen)
        ) { Text("- 30 min", fontSize = 13.sp) }

        Text(
            text = "%02d:%02d".format(hour, minute),
            color = if (enabled) TextPrimary else TextSecondary,
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        FilledTonalButton(
            onClick = { onAdjust(+30) }, enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(width = 80.dp, height = 52.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = SurfaceAlt, contentColor = AccentGreen)
        ) { Text("+ 30 min", fontSize = 13.sp) }
    }
}

// ─── Chips de timeout ─────────────────────────────────────────────────────

@Composable
internal fun TimeoutChips(selectedMinutes: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    val options = listOf(60 to "1 hora", 120 to "2 horas", 240 to "4 horas")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { (minutes, label) ->
            val sel = selectedMinutes == minutes
            Button(
                onClick = { onSelect(minutes) },
                enabled = enabled,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f).height(46.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (sel) AccentGreen else SurfaceAlt,
                    contentColor   = if (sel) TextPrimary else TextSecondary
                )
            ) {
                Text(label, fontSize = 14.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

// ─── Card contenedor ──────────────────────────────────────────────────────

@Composable
internal fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyMedium),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.padding(16.dp)) { content() }
    }
}

// ─── Utilidades ───────────────────────────────────────────────────────────

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
