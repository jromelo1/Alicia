package com.gutigu.alicia.feature.checkin

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.gutigu.alicia.ui.theme.AliciaAccent
import com.gutigu.alicia.ui.theme.AliciaBackground
import com.gutigu.alicia.ui.theme.AliciaText
import com.gutigu.alicia.ui.theme.AliciaTextSecondary
import com.gutigu.alicia.ui.theme.AliciaWarning

private val NavyDark      = AliciaBackground
private val AccentGreen   = AliciaAccent
private val AccentAmber   = AliciaWarning
private val TextPrimary   = AliciaText
private val TextSecondary = AliciaTextSecondary

/** El Familiar configura aquí el horario del check-in diario del Adulto Mayor. */
@Composable
fun FamiliarCheckInScreen(viewModel: FamiliarCheckInViewModel = hiltViewModel()) {
    val schedule by viewModel.schedule.collectAsState()
    val hasNoContacts by viewModel.hasNoContacts.collectAsState()
    val context = LocalContext.current

    var hasNotificationPermission by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }

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

    // Sin este permiso (obligatorio desde Android 13) el Familiar no ve la alerta
    // de "no respondió" ni la de pánico — se pide apenas se detecta que falta.
    LaunchedEffect(Unit) {
        if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Bienestar",
            color = TextPrimary,
            fontFamily = MaterialTheme.typography.headlineLarge.fontFamily,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Configura el chequeo diario de tu familiar",
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
                            "Sin notificaciones activadas no vas a ver las alertas de tu familiar (pánico o check-in perdido).",
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
                        "Todavía no hay nadie en el círculo de tu familiar. Únete o agrega un guardián antes de activar la alarma, o no le avisará a nadie.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Activar chequeo diario", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Tu familiar recibirá una alarma con saludo cada día", color = TextSecondary, fontSize = 14.sp)
                }
                Switch(
                    checked = schedule.enabled && !hasNoContacts,
                    enabled = !hasNoContacts,
                    onCheckedChange = { viewModel.saveSchedule(schedule.copy(enabled = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AccentGreen,
                        checkedTrackColor = AccentGreen.copy(alpha = 0.4f),
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = TextSecondary.copy(alpha = 0.2f)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsCard {
            Column {
                Text("Hora de la alarma", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Tu familiar recibirá el saludo a esta hora", color = TextSecondary, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 16.dp))
                TimeAdjuster(
                    hour = schedule.hour,
                    minute = schedule.minute,
                    enabled = schedule.enabled,
                    onAdjust = { delta -> viewModel.saveSchedule(adjustSchedule(schedule, delta)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsCard {
            Column {
                Text("Si no abre la app en...", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Recibirás un aviso a este círculo", color = TextSecondary, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 16.dp))
                TimeoutChips(
                    selectedMinutes = schedule.timeoutMinutes,
                    enabled = schedule.enabled,
                    onSelect = { viewModel.saveSchedule(schedule.copy(timeoutMinutes = it)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Se guarda solo en cada cambio — este aviso confirma que ya está
        // sincronizado con el dispositivo del Adulto Mayor.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (schedule.enabled) Icons.Default.Check else Icons.Default.Warning,
                contentDescription = null,
                tint = if (schedule.enabled) AccentGreen else TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (schedule.enabled)
                    "Sincronizado — tu familiar ya ve este horario"
                else
                    "El chequeo está desactivado",
                color = if (schedule.enabled) AccentGreen else TextSecondary,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

private fun adjustSchedule(schedule: CheckInScheduleData, deltaMinutes: Int): CheckInScheduleData {
    val totalMinutes = schedule.hour * 60 + schedule.minute + deltaMinutes
    val normalized = ((totalMinutes % (24 * 60)) + 24 * 60) % (24 * 60)
    return schedule.copy(hour = normalized / 60, minute = normalized % 60)
}
