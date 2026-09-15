package com.gutigu.alicia.feature.memories

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gutigu.alicia.ui.theme.AliciaAccent
import com.gutigu.alicia.ui.theme.AliciaBackground
import com.gutigu.alicia.ui.theme.AliciaCircle
import com.gutigu.alicia.ui.theme.AliciaCircleSoft
import com.gutigu.alicia.ui.theme.AliciaFormAlert
import com.gutigu.alicia.ui.theme.AliciaSurfaceAlt
import com.gutigu.alicia.ui.theme.AliciaText
import com.gutigu.alicia.ui.theme.AliciaTextSecondary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

// Paleta centralizada en ui/theme/Color.kt
private val NavyDark      = AliciaBackground
private val NavyMedium    = AliciaSurfaceAlt
private val AccentGreen   = AliciaAccent
private val AccentRed     = AliciaFormAlert
private val TextPrimary   = AliciaText
private val TextSecondary = AliciaTextSecondary
// Morado del círculo — reservado para "Recuerdos" para distinguirse a simple
// vista de Medicina (azul) y Bienestar/SOS (verde/rojo).
private val AccentPurple     = AliciaCircle
private val AccentPurpleSoft = AliciaCircleSoft

// ─────────────────────────────────────────────────────────────────────────────
//  Pantalla principal
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MemoriesScreen(viewModel: MemoriesViewModel = hiltViewModel()) {
    val state       by viewModel.state.collectAsState()
    val uploadState by viewModel.uploadState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = NavyDark,
        floatingActionButton = {
            if (state.isFamiliar) {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = AccentPurple,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(26.dp)) },
                    text = { Text("Agregar recuerdo", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
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

            item {
                Text("Línea de Tiempo", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (state.isFamiliar)
                        "Agrega fotos y momentos para que tu familiar los recuerde"
                    else
                        "Tus recuerdos y las personas que quieres",
                    color = TextSecondary,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )

                if (uploadState is MemoryPhotoUploadState.Uploading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NavyMedium, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = AccentPurple,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Subiendo foto del recuerdo…", color = TextSecondary, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (uploadState is MemoryPhotoUploadState.Error) {
                    Text(
                        (uploadState as MemoryPhotoUploadState.Error).message,
                        color = AccentRed,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                if (state.memories.isEmpty()) EmptyMemoriesState(isFamiliar = state.isFamiliar)
            }

            if (state.memories.isNotEmpty()) {
                items(state.memories, key = { it.id }) { memory ->
                    Box(modifier = Modifier.padding(bottom = 16.dp)) {
                        if (state.isFamiliar) {
                            MemoryManageCard(
                                memory = memory,
                                onDelete = { viewModel.deleteMemory(memory.id) }
                            )
                        } else {
                            MemoryTimelineCard(memory = memory)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(140.dp)) }
        }
    }

    if (showAddDialog) {
        AddMemoryDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, description, eventDate, tags, photoUri ->
                viewModel.addMemory(title, description, eventDate, tags, photoUri)
                showAddDialog = false
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tarjeta — vista Adulto Mayor (feed de solo lectura, tipo diario de recuerdos)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MemoryTimelineCard(memory: Memory) {
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NavyMedium),
        elevation = CardDefaults.cardElevation(3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            if (memory.photoUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(memory.photoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = memory.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .background(AccentPurpleSoft, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📷", fontSize = 48.sp)
                }
            }

            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    formatMemoryDate(memory.eventDate),
                    color = AccentPurple,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (memory.title.isNotBlank()) {
                    Text(
                        memory.title,
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (memory.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(memory.description, color = TextSecondary, fontSize = 15.sp, lineHeight = 20.sp)
                }

                if (memory.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        memory.tags.forEach { tag ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(AccentPurpleSoft, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = AccentPurple,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    memoryTagPhrase(tag),
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "Este es tu hijo Gustavo" / "Esta es tu nieta Sofía" — heurística de género por terminación. */
private fun memoryTagPhrase(tag: MemoryTag): String {
    val relationship = tag.relationship.trim()
    if (relationship.isBlank()) return tag.name
    val pronoun = if (relationship.endsWith("a", ignoreCase = true)) "Esta es tu" else "Este es tu"
    return "$pronoun $relationship ${tag.name}"
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tarjeta — vista Familiar (administrar: eliminar)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MemoryManageCard(memory: Memory, onDelete: () -> Unit) {
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyMedium),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(64.dp),
                contentAlignment = Alignment.Center
            ) {
                if (memory.photoUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(memory.photoUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = memory.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AccentPurpleSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📷", fontSize = 26.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    memory.title.ifBlank { "Sin título" },
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    formatMemoryDate(memory.eventDate),
                    color = AccentPurple,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                if (memory.tags.isNotEmpty()) {
                    Text(
                        memory.tags.joinToString(", ") { "${it.name} (${it.relationship})" },
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Eliminar recuerdo",
                    tint = AccentRed.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Estado vacío
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyMemoriesState(isFamiliar: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("📔", fontSize = 72.sp, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "Aún no hay recuerdos",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            if (isFamiliar)
                "Agrega una foto familiar con nombres y parentescos\npara que tu familiar la recuerde."
            else
                "Cuando tu familia agregue fotos y momentos,\naparecerán aquí.",
            color = TextSecondary,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Diálogo "Nuevo recuerdo"
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddMemoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        description: String,
        eventDate: Long,
        tags: List<MemoryTag>,
        photoUri: Uri?
    ) -> Unit
) {
    val context = LocalContext.current
    var title       by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var tagName         by remember { mutableStateOf("") }
    var tagRelationship by remember { mutableStateOf("") }
    val tags = remember { mutableStateOf(listOf<MemoryTag>()) }

    var localPhotoUri   by remember { mutableStateOf<Uri?>(null) }
    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }

    val today = remember { Calendar.getInstance() }
    var selectedYear  by remember { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(today.get(Calendar.MONTH)) }
    var selectedDay   by remember { mutableIntStateOf(today.get(Calendar.DAY_OF_MONTH)) }
    var showDatePicker by remember { mutableStateOf(false) }

    val selectedMillis = remember(selectedYear, selectedMonth, selectedDay) {
        Calendar.getInstance().apply {
            set(selectedYear, selectedMonth, selectedDay, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> if (success) localPhotoUri = cameraOutputUri }

    // Lanza la cámara solo tras confirmar el permiso — sin esto, ACTION_IMAGE_CAPTURE
    // hace crashear la app con SecurityException si el permiso aún no fue otorgado.
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createMemoryCameraUri(context)
            cameraOutputUri = uri
            takePictureLauncher.launch(uri)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) localPhotoUri = uri }

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
                Text("Nuevo recuerdo", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                // ── FOTO ───────────────────────────────────────────────────
                Text("Foto", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(NavyDark)
                            .border(
                                2.dp,
                                if (localPhotoUri != null) AccentPurple else TextSecondary.copy(alpha = 0.4f),
                                RoundedCornerShape(14.dp)
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
                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(14.dp))
                            )
                        } else {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(28.dp))
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(
                            onClick = {
                                val hasCameraPermission = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasCameraPermission) {
                                    val uri = createMemoryCameraUri(context)
                                    cameraOutputUri = uri
                                    takePictureLauncher.launch(uri)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = NavyDark, contentColor = AccentPurple)
                        ) { Text("📸 Cámara", fontSize = 14.sp) }
                        FilledTonalButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = NavyDark, contentColor = TextSecondary)
                        ) { Text("🖼️ Galería", fontSize = 14.sp) }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título", color = TextSecondary) },
                    placeholder = { Text("Ej. Cumpleaños 80 de la abuela", color = TextSecondary.copy(alpha = 0.5f)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 18.sp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción (opcional)", color = TextSecondary) },
                    placeholder = { Text("Ej. Fue en la casa de la finca, con toda la familia", color = TextSecondary.copy(alpha = 0.5f)) },
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 15.sp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ── FECHA ─────────────────────────────────────────────────
                Text("Fecha del recuerdo", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = NavyDark, contentColor = AccentPurple)
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(formatMemoryDate(selectedMillis), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── ETIQUETAS (personas) ────────────────────────────────────
                Text("Personas en la foto", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Ej. nombre \"Gustavo\", parentesco \"hijo\"",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = tagName,
                        onValueChange = { tagName = it },
                        label = { Text("Nombre", color = TextSecondary) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        colors = fieldColors(),
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 15.sp)
                    )
                    OutlinedTextField(
                        value = tagRelationship,
                        onValueChange = { tagRelationship = it },
                        label = { Text("Parentesco", color = TextSecondary) },
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 15.sp)
                    )
                    IconButton(
                        onClick = {
                            if (tagName.isNotBlank()) {
                                tags.value = tags.value + MemoryTag(tagName.trim(), tagRelationship.trim())
                                tagName = ""
                                tagRelationship = ""
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (tagName.isNotBlank()) AccentPurple else TextSecondary.copy(alpha = 0.2f))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Agregar persona", tint = Color.White)
                    }
                }

                if (tags.value.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tags.value.forEach { tag ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(AccentPurpleSoft, RoundedCornerShape(20.dp))
                                    .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                            ) {
                                Text(
                                    if (tag.relationship.isNotBlank()) "${tag.name} · ${tag.relationship}" else tag.name,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Quitar",
                                    tint = AccentPurple,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { tags.value = tags.value - tag }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── BOTONES ───────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(52.dp)) {
                        Text("Cancelar", color = TextSecondary, fontSize = 16.sp)
                    }
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                onConfirm(title.trim(), description.trim(), selectedMillis, tags.value, localPhotoUri)
                            }
                        },
                        enabled = title.isNotBlank(),
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                    ) {
                        Text("Guardar", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showDatePicker) {
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
            // Un recuerdo es del pasado — no tiene sentido elegir una fecha futura.
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= todayUtcMidnight
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
                }) { Text("Aceptar", color = AccentPurple, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar", color = TextSecondary) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun createMemoryCameraUri(context: Context): Uri {
    val dir  = File(context.cacheDir, "memory_photos").also { it.mkdirs() }
    val file = File(dir, "memory_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun formatMemoryDate(millis: Long): String {
    val fmt = SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("es", "CO"))
    return fmt.format(java.util.Date(millis)).replaceFirstChar { it.uppercase() }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor     = AccentPurple,
    unfocusedBorderColor   = TextSecondary.copy(alpha = 0.4f),
    focusedLabelColor      = AccentPurple,
    cursorColor            = AccentPurple,
    focusedContainerColor  = NavyDark,
    unfocusedContainerColor = NavyDark
)
