package com.gutigu.alicia.feature.medications

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gutigu.alicia.data.AppointmentEntity
import com.gutigu.alicia.data.MedicationEntity
import com.gutigu.alicia.ui.theme.AliciaAccent
import com.gutigu.alicia.ui.theme.AliciaAccentBlue
import com.gutigu.alicia.ui.theme.AliciaBackground
import com.gutigu.alicia.ui.theme.AliciaFormAlert
import com.gutigu.alicia.ui.theme.AliciaSurfaceAlt
import com.gutigu.alicia.ui.theme.AliciaText
import com.gutigu.alicia.ui.theme.AliciaTextSecondary
import com.gutigu.alicia.ui.theme.AliciaWarning
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

// Paleta centralizada en ui/theme/Color.kt
private val NavyDark      = AliciaBackground   // fondo de pantalla
private val NavyMedium    = AliciaSurfaceAlt   // superficie de tarjeta/diálogo
private val AccentGreen   = AliciaAccent
private val AccentRed     = AliciaFormAlert
private val TextPrimary   = AliciaText
private val TextSecondary = AliciaTextSecondary
private val OrangeFood    = Color(0xFFB9770E)
// Las citas médicas usan azul en vez de verde para distinguirse de los
// medicamentos de un vistazo, sin depender solo del ícono.
private val AccentBlue    = AliciaAccentBlue
private val AccentAmber   = AliciaWarning

// ─────────────────────────────────────────────────────────────────────────────
//  Pantalla principal — Lista de medicamentos
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MedicationScreen(
    viewModel: MedicationViewModel = hiltViewModel(),
    appointmentViewModel: AppointmentViewModel = hiltViewModel()
) {
    val meds          by viewModel.medications.collectAsState()
    val uploadState   by viewModel.photoUploadState.collectAsState()
    val hasNoGuardians by viewModel.hasNoGuardians.collectAsState()
    val appointments  by appointmentViewModel.appointments.collectAsState()
    var showMedDialog  by remember { mutableStateOf(false) }
    var showApptDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = NavyDark,
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExtendedFloatingActionButton(
                    onClick = { showApptDialog = true },
                    containerColor = AccentBlue,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(24.dp)) },
                    text = { Text("Agregar cita médica", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                )
                ExtendedFloatingActionButton(
                    onClick = { showMedDialog = true },
                    containerColor = AccentGreen,
                    contentColor = TextPrimary,
                    icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(26.dp)) },
                    text = { Text("Agregar medicamento", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            item { Spacer(modifier = Modifier.height(24.dp)) }

            // ── Sección: Medicamentos ──────────────────────────────────────
            item {
                Text("Medicamentos", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Recordatorios para tomar tus medicamentos",
                    color = TextSecondary,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )

                // Banner: sin guardianes en el círculo — un medicamento sin confirmar no le avisaría a nadie
                if (hasNoGuardians) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = AccentAmber.copy(alpha = 0.12f))
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Todavía no hay ningún guardián en tu círculo. Si no confirmas que tomaste tu medicamento, no habrá nadie a quien avisar.",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Banner de subida de foto
                if (uploadState is PhotoUploadState.Uploading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NavyMedium, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = AccentGreen,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Subiendo foto de la pastilla…", color = TextSecondary, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (meds.isEmpty()) EmptyMedicationsState()
            }

            if (meds.isNotEmpty()) {
                items(meds, key = { "med_${it.id}" }) { med ->
                    Box(modifier = Modifier.padding(bottom = 12.dp)) {
                        MedicationCard(
                            med      = med,
                            onToggle = { viewModel.toggleActive(med) },
                            onDelete = { viewModel.deleteMedication(med) }
                        )
                    }
                }
            }

            // ── Sección: Citas médicas ─────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(28.dp))
                Text("Citas médicas", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Te avisamos el día y la hora de cada cita",
                    color = TextSecondary,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )
                if (appointments.isEmpty()) EmptyAppointmentsState()
            }

            if (appointments.isNotEmpty()) {
                items(appointments, key = { "appt_${it.id}" }) { appt ->
                    Box(modifier = Modifier.padding(bottom = 12.dp)) {
                        AppointmentCard(
                            appointment = appt,
                            onDelete = { appointmentViewModel.deleteAppointment(appt) }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(140.dp)) }
        }
    }

    if (showMedDialog) {
        AddMedicationDialog(
            onDismiss = { showMedDialog = false },
            onConfirm = { name, dose, hour, minute, withFood, note, photoUri ->
                viewModel.addMedication(name, dose, hour, minute, withFood, note, photoUri)
                showMedDialog = false
            }
        )
    }

    if (showApptDialog) {
        AddAppointmentDialog(
            onDismiss = { showApptDialog = false },
            onConfirm = { doctorName, specialty, location, note, dateTimeMillis ->
                appointmentViewModel.addAppointment(doctorName, specialty, location, note, dateTimeMillis)
                showApptDialog = false
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tarjeta de medicamento (con foto thumbnail si existe)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MedicationCard(
    med: MedicationEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (med.isActive) NavyMedium else NavyMedium.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Foto de la pastilla o icono emoji
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                if (med.photoUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(med.photoUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = med.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .border(
                                2.dp,
                                if (med.isActive) AccentGreen else TextSecondary,
                                CircleShape
                            )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(NavyDark.copy(alpha = 0.6f))
                            .border(
                                2.dp,
                                if (med.isActive) AccentGreen.copy(alpha = 0.5f) else TextSecondary.copy(alpha = 0.3f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("💊", fontSize = 24.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    med.name,
                    color = if (med.isActive) TextPrimary else TextSecondary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "%02d:%02d".format(med.hour, med.minute),
                        color = if (med.isActive) AccentGreen else TextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (med.dose.isNotBlank()) {
                        Text("·", color = TextSecondary, fontSize = 15.sp)
                        Text(med.dose, color = TextSecondary, fontSize = 14.sp)
                    }
                    if (med.withFood) {
                        Text("🍽️", fontSize = 13.sp)
                    }
                }
                if (med.note.isNotBlank()) {
                    Text(
                        med.note,
                        color = TextSecondary.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }

            Switch(
                checked = med.isActive,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor    = AccentGreen,
                    checkedTrackColor    = AccentGreen.copy(alpha = 0.4f),
                    uncheckedThumbColor  = TextSecondary,
                    uncheckedTrackColor  = TextSecondary.copy(alpha = 0.2f)
                )
            )

            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Eliminar",
                    tint = AccentRed.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Diálogo "Nuevo medicamento" — versión completa con foto + detalles
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AddMedicationDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, dose: String, hour: Int, minute: Int, withFood: Boolean, note: String, photoUri: Uri?) -> Unit
) {
    val context = LocalContext.current
    var name     by remember { mutableStateOf("") }
    var dose     by remember { mutableStateOf("") }
    var hour     by remember { mutableIntStateOf(8) }
    var minute   by remember { mutableIntStateOf(0) }
    var withFood by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }

    // URI local de la foto elegida / capturada
    var localPhotoUri  by remember { mutableStateOf<Uri?>(null) }
    // URI temporal para TakePicture (necesita FileProvider)
    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }

    // ── Launchers ──────────────────────────────────────────────────────────
    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) localPhotoUri = cameraOutputUri
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) localPhotoUri = uri
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .background(NavyMedium, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(24.dp)
            ) {
                Text(
                    "Nuevo medicamento",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                // ── SECCIÓN FOTO ────────────────────────────────────────────
                Text(
                    "Foto de la pastilla",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Preview circular
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(NavyDark)
                            .border(
                                2.dp,
                                if (localPhotoUri != null) AccentGreen else TextSecondary.copy(alpha = 0.4f),
                                CircleShape
                            )
                            .clickable { galleryLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (localPhotoUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(localPhotoUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Text("💊", fontSize = 30.sp)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Botón cámara
                        FilledTonalButton(
                            onClick = {
                                val uri = createPillCameraUri(context)
                                cameraOutputUri = uri
                                takePictureLauncher.launch(uri)
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                containerColor = NavyDark, contentColor = AccentGreen
                            )
                        ) {
                            Text("📸 Cámara", fontSize = 14.sp)
                        }
                        // Botón galería
                        FilledTonalButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            shape = RoundedCornerShape(10.dp),
                            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                containerColor = NavyDark, contentColor = TextSecondary
                            )
                        ) {
                            Text("🖼️ Galería", fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── NOMBRE ─────────────────────────────────────────────────
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del medicamento", color = TextSecondary) },
                    placeholder = { Text("Ej. Metformina", color = TextSecondary.copy(alpha = 0.5f)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 18.sp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // ── DOSIS ──────────────────────────────────────────────────
                OutlinedTextField(
                    value = dose,
                    onValueChange = { dose = it },
                    label = { Text("Dosis (opcional)", color = TextSecondary) },
                    placeholder = { Text("Ej. 500mg, 1 pastilla", color = TextSecondary.copy(alpha = 0.5f)) },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 18.sp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // ── NOTA ───────────────────────────────────────────────────
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Instrucción adicional (opcional)", color = TextSecondary) },
                    placeholder = { Text("Ej. Con un vaso de agua", color = TextSecondary.copy(alpha = 0.5f)) },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 16.sp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // ── CON COMIDA / EN AYUNAS ─────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NavyDark, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(if (withFood) "🍽️" else "🌅", fontSize = 22.sp)
                        Column {
                            Text(
                                if (withFood) "Con comida" else "En ayunas",
                                color = if (withFood) OrangeFood else TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (withFood) "Tomar después de comer" else "Tomar sin haber comido",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Switch(
                        checked = withFood,
                        onCheckedChange = { withFood = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor   = OrangeFood,
                            checkedTrackColor   = OrangeFood.copy(alpha = 0.4f),
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = TextSecondary.copy(alpha = 0.2f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── HORA ───────────────────────────────────────────────────
                Text("Hora del recordatorio", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            val total = hour * 60 + minute - 30
                            val norm  = ((total % 1440) + 1440) % 1440
                            hour = norm / 60; minute = norm % 60
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(width = 72.dp, height = 46.dp),
                        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                            containerColor = NavyDark, contentColor = AccentGreen
                        )
                    ) { Text("−30m", fontSize = 12.sp) }

                    Text(
                        "%02d:%02d".format(hour, minute),
                        color = TextPrimary,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    FilledTonalButton(
                        onClick = {
                            val total = hour * 60 + minute + 30
                            val norm  = ((total % 1440) + 1440) % 1440
                            hour = norm / 60; minute = norm % 60
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(width = 72.dp, height = 46.dp),
                        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                            containerColor = NavyDark, contentColor = AccentGreen
                        )
                    ) { Text("+30m", fontSize = 12.sp) }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── BOTONES ────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Text("Cancelar", color = TextSecondary, fontSize = 16.sp)
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onConfirm(
                                    name.trim(), dose.trim(),
                                    hour, minute,
                                    withFood, noteText.trim(),
                                    localPhotoUri
                                )
                            }
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                    ) {
                        Text("Guardar", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Crea un archivo temporal en caché y devuelve su URI via FileProvider.
 * Usado para alimentar [ActivityResultContracts.TakePicture].
 */
private fun createPillCameraUri(context: Context): Uri {
    val dir  = File(context.cacheDir, "pill_photos").also { it.mkdirs() }
    val file = File(dir, "pill_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
private fun EmptyMedicationsState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("💊", fontSize = 72.sp, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "Sin medicamentos",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "Agrega tus medicamentos y\nrecibirás un recordatorio a la hora indicada.",
            color = TextSecondary,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tarjeta de cita médica
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppointmentCard(
    appointment: AppointmentEntity,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyMedium),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(NavyDark.copy(alpha = 0.6f))
                    .border(2.dp, AccentBlue.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("🩺", fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                val who = if (appointment.specialty.isNotBlank())
                    "${appointment.doctorName} · ${appointment.specialty}"
                else appointment.doctorName
                Text(who, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    formatAppointmentDateTime(appointment.dateTimeMillis),
                    color = AccentBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                if (appointment.location.isNotBlank()) {
                    Text("📍 ${appointment.location}", color = TextSecondary, fontSize = 13.sp, maxLines = 1)
                }
                if (appointment.note.isNotBlank()) {
                    Text(
                        appointment.note,
                        color = TextSecondary.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Eliminar cita",
                    tint = AccentRed.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyAppointmentsState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🩺", fontSize = 64.sp, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Sin citas médicas",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Agrega tu próxima cita y te avisamos\nel día y la hora que elijas.",
            color = TextSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Diálogo "Nueva cita médica"
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAppointmentDialog(
    onDismiss: () -> Unit,
    onConfirm: (doctorName: String, specialty: String, location: String, note: String, dateTimeMillis: Long) -> Unit
) {
    var doctorName by remember { mutableStateOf("") }
    var specialty  by remember { mutableStateOf("") }
    var location   by remember { mutableStateOf("") }
    var noteText   by remember { mutableStateOf("") }

    // Por defecto, mañana a las 9:00 — una cita "hoy mismo" rara vez es realista de agendar así
    val tomorrow = remember { Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) } }
    var selectedYear  by remember { mutableIntStateOf(tomorrow.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(tomorrow.get(Calendar.MONTH)) }
    var selectedDay   by remember { mutableIntStateOf(tomorrow.get(Calendar.DAY_OF_MONTH)) }
    var hour   by remember { mutableIntStateOf(9) }
    var minute by remember { mutableIntStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }

    val selectedMillis = remember(selectedYear, selectedMonth, selectedDay, hour, minute) {
        Calendar.getInstance().apply {
            set(selectedYear, selectedMonth, selectedDay, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .background(NavyMedium, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(24.dp)
            ) {
                Text(
                    "Nueva cita médica",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                // ── CON QUIÉN ─────────────────────────────────────────────
                OutlinedTextField(
                    value = doctorName,
                    onValueChange = { doctorName = it },
                    label = { Text("Con quién es la cita", color = TextSecondary) },
                    placeholder = { Text("Ej. Dra. Pérez", color = TextSecondary.copy(alpha = 0.5f)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 18.sp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // ── ESPECIALIDAD ──────────────────────────────────────────
                OutlinedTextField(
                    value = specialty,
                    onValueChange = { specialty = it },
                    label = { Text("Especialidad (opcional)", color = TextSecondary) },
                    placeholder = { Text("Ej. Cardiología", color = TextSecondary.copy(alpha = 0.5f)) },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 16.sp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // ── LUGAR ─────────────────────────────────────────────────
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Lugar (opcional)", color = TextSecondary) },
                    placeholder = { Text("Ej. Clínica del Country, consultorio 302", color = TextSecondary.copy(alpha = 0.5f)) },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 16.sp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // ── NOTA ──────────────────────────────────────────────────
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Nota adicional (opcional)", color = TextSecondary) },
                    placeholder = { Text("Ej. Llevar exámenes de sangre", color = TextSecondary.copy(alpha = 0.5f)) },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 16.sp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ── FECHA ─────────────────────────────────────────────────
                Text("Fecha de la cita", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = NavyDark, contentColor = AccentBlue
                    )
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(formatAppointmentDate(selectedMillis), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── HORA ──────────────────────────────────────────────────
                Text("Hora de la cita", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            val total = hour * 60 + minute - 30
                            val norm  = ((total % 1440) + 1440) % 1440
                            hour = norm / 60; minute = norm % 60
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(width = 72.dp, height = 46.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = NavyDark, contentColor = AccentBlue
                        )
                    ) { Text("−30m", fontSize = 12.sp) }

                    Text(
                        "%02d:%02d".format(hour, minute),
                        color = TextPrimary,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    FilledTonalButton(
                        onClick = {
                            val total = hour * 60 + minute + 30
                            val norm  = ((total % 1440) + 1440) % 1440
                            hour = norm / 60; minute = norm % 60
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(width = 72.dp, height = 46.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = NavyDark, contentColor = AccentBlue
                        )
                    ) { Text("+30m", fontSize = 12.sp) }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── BOTONES ───────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Text("Cancelar", color = TextSecondary, fontSize = 16.sp)
                    }
                    Button(
                        onClick = {
                            if (doctorName.isNotBlank()) {
                                onConfirm(
                                    doctorName.trim(), specialty.trim(),
                                    location.trim(), noteText.trim(),
                                    selectedMillis
                                )
                            }
                        },
                        enabled = doctorName.isNotBlank(),
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) {
                        Text("Guardar", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        // El DatePicker de Compose trabaja en milisegundos UTC de medianoche — se
        // convierte a/desde año-mes-día locales para no desfasar el día elegido.
        val initialUtcMillis = remember(selectedYear, selectedMonth, selectedDay) {
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(selectedYear, selectedMonth, selectedDay, 0, 0, 0)
            }.timeInMillis
        }
        val todayUtcMidnight = remember {
            val local = Calendar.getInstance()
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
            }.timeInMillis
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialUtcMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis >= todayUtcMidnight
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
                        selectedYear  = utcCal.get(Calendar.YEAR)
                        selectedMonth = utcCal.get(Calendar.MONTH)
                        selectedDay   = utcCal.get(Calendar.DAY_OF_MONTH)
                    }
                    showDatePicker = false
                }) { Text("Aceptar", color = AccentBlue, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar", color = TextSecondary) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun formatAppointmentDate(millis: Long): String {
    val fmt = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "CO"))
    return fmt.format(java.util.Date(millis)).replaceFirstChar { it.uppercase() }
}

private fun formatAppointmentDateTime(millis: Long): String {
    val fmt = SimpleDateFormat("EEEE d 'de' MMMM, HH:mm", Locale("es", "CO"))
    return fmt.format(java.util.Date(millis)).replaceFirstChar { it.uppercase() }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor     = AccentGreen,
    unfocusedBorderColor   = TextSecondary.copy(alpha = 0.4f),
    focusedLabelColor      = AccentGreen,
    cursorColor            = AccentGreen,
    focusedContainerColor  = NavyDark,
    unfocusedContainerColor = NavyDark
)
