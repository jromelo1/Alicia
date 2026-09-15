package com.gutigu.alicia.feature.safezones

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.gutigu.alicia.ui.theme.AliciaAccent
import com.gutigu.alicia.ui.theme.AliciaBackground
import com.gutigu.alicia.ui.theme.AliciaFormAlert
import com.gutigu.alicia.ui.theme.AliciaSurface
import com.gutigu.alicia.ui.theme.AliciaText
import com.gutigu.alicia.ui.theme.AliciaTextSecondary

// ─── Paleta, centralizada en ui/theme/Color.kt ──────────────────────────────────
private val NavyDark      = AliciaBackground   // fondo de pantalla
private val NavyMedium    = AliciaSurface      // superficie de tarjeta/panel
private val AccentGreen   = AliciaAccent
private val AccentRed     = AliciaFormAlert
private val TextPrimary   = AliciaText
private val TextSecondary = AliciaTextSecondary

@Composable
fun SafeZonesScreen(
    viewModel: GeofenceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showZoneList  by remember { mutableStateOf(true) }

    // Pedir permiso de ubicación precisa
    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.onPermissionsUpdated() }

    // Pedir permiso de ubicación en segundo plano (Android Q+)
    val bgPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.onPermissionsUpdated() }

    // Iniciar actualizaciones de ubicación cuando la pantalla es visible
    DisposableEffect(Unit) {
        viewModel.startLocationUpdates()
        onDispose { viewModel.stopLocationUpdates() }
    }

    Box(modifier = Modifier.fillMaxSize().background(NavyDark)) {

        when (val state = uiState) {
            is SafeZoneUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentGreen)
                }
            }

            is SafeZoneUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.message, color = Color(0xFFFF6B6B), fontSize = 16.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
                }
            }

            is SafeZoneUiState.Ready -> {
                MapContent(
                    state          = state,
                    showZoneList   = showZoneList,
                    onToggleList   = { showZoneList = !showZoneList },
                    onToggleZone   = { id, active -> viewModel.toggleZone(id, active) },
                    onDeleteZone   = { id -> viewModel.removeZone(id) },
                    onAddZone      = { showAddDialog = true },
                    onRequestFine  = {
                        locationPermLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    },
                    onRequestBg    = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            bgPermLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    }
                )
            }
        }
    }

    if (showAddDialog) {
        AddZoneDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, radius ->
                viewModel.addZoneAtCurrentLocation(name, radius)
                showAddDialog = false
            }
        )
    }
}

// ─── Contenido principal con mapa ──────────────────────────────────────────────

@Composable
private fun MapContent(
    state: SafeZoneUiState.Ready,
    showZoneList: Boolean,
    onToggleList: () -> Unit,
    onToggleZone: (String, Boolean) -> Unit,
    onDeleteZone: (String) -> Unit,
    onAddZone: () -> Unit,
    onRequestFine: () -> Unit,
    onRequestBg: () -> Unit
) {
    // Punto de cámara — ubicación actual o Bogotá como fallback
    val defaultLatLng = LatLng(4.7110, -74.0721)
    val currentLatLng = if (state.currentLat != null && state.currentLng != null) {
        LatLng(state.currentLat, state.currentLng)
    } else null

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(currentLatLng ?: defaultLatLng, 15f)
    }

    // Centrar el mapa cuando se obtiene la ubicación por primera vez
    LaunchedEffect(currentLatLng) {
        currentLatLng?.let {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 15f))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Mapa ──────────────────────────────────────────────────────────────
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = state.locationPermissionGranted,
                mapType = MapType.NORMAL
            ),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = false,   // usamos nuestro FAB
                zoomControlsEnabled = false
            )
        ) {
            // Marcador de ubicación actual (si no podemos usar el botón nativo)
            currentLatLng?.let { pos ->
                Marker(
                    state = rememberMarkerState(position = pos),
                    title = "Tu ubicación",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                )
            }

            // Círculo + marcador central por cada zona segura
            state.zones.forEach { zone ->
                val zoneLatLng = LatLng(zone.latitude, zone.longitude)
                val zoneColor  = if (zone.isActive) AccentGreen else TextSecondary

                Circle(
                    center      = zoneLatLng,
                    radius      = zone.radiusMeters.toDouble(),
                    fillColor   = zoneColor.copy(alpha = 0.18f),
                    strokeColor = zoneColor.copy(alpha = 0.7f),
                    strokeWidth = 3f
                )
                Marker(
                    state = rememberMarkerState(position = zoneLatLng),
                    title = zone.name,
                    snippet = "Radio: ${zone.radiusMeters.toInt()} m · ${if (zone.isActive) "Activa" else "Inactiva"}",
                    icon = BitmapDescriptorFactory.defaultMarker(
                        if (zone.isActive) BitmapDescriptorFactory.HUE_CYAN
                        else BitmapDescriptorFactory.HUE_YELLOW
                    )
                )
            }
        }

        // ── Banners (encima del mapa, arriba) ──────────────────────────────────
        val bannerCount = (if (!state.backgroundPermissionGranted) 1 else 0) +
                (if (state.hasNoGuardians) 1 else 0)

        Column(modifier = Modifier.align(Alignment.TopCenter)) {
            AnimatedVisibility(
                visible = !state.backgroundPermissionGranted,
                enter   = slideInVertically { -it },
                exit    = slideOutVertically { -it }
            ) {
                PermissionBanner(
                    hasFine = state.locationPermissionGranted,
                    onRequestFine = onRequestFine,
                    onRequestBg   = onRequestBg
                )
            }
            AnimatedVisibility(
                visible = state.hasNoGuardians,
                enter   = slideInVertically { -it },
                exit    = slideOutVertically { -it }
            ) {
                GuardianWarningBanner()
            }
        }

        // ── Título flotante (solo si la lista está oculta) ────────────────────
        if (!showZoneList) {
            Surface(
                modifier  = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = if (bannerCount > 0) (16 + bannerCount * 64).dp else 16.dp),
                shape     = RoundedCornerShape(12.dp),
                color     = NavyDark.copy(alpha = 0.85f),
                tonalElevation = 4.dp
            ) {
                Text(
                    text = "Zonas Seguras",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        // ── Panel de zonas (bottom sheet ligero) ──────────────────────────────
        AnimatedVisibility(
            visible  = showZoneList,
            enter    = slideInVertically { it },
            exit     = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ZoneListPanel(
                zones        = state.zones,
                onToggle     = onToggleZone,
                onDelete     = onDeleteZone,
                onCollapse   = onToggleList
            )
        }

        // ── FABs (esquina inferior derecha) ───────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = if (showZoneList) 300.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Centrar en mi ubicación
            SmallFloatingActionButton(
                onClick        = {},   // el mapa con isMyLocationEnabled ya lo hace
                containerColor = NavyMedium,
                contentColor   = AccentGreen
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Mi ubicación")
            }

            // Mostrar / ocultar lista de zonas
            SmallFloatingActionButton(
                onClick        = onToggleList,
                containerColor = NavyMedium,
                contentColor   = AccentGreen
            ) {
                Icon(
                    imageVector = if (showZoneList) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    contentDescription = "Lista de zonas"
                )
            }

            // Añadir zona en posición actual
            FloatingActionButton(
                onClick        = onAddZone,
                containerColor = AccentGreen,
                contentColor   = TextPrimary,
                modifier       = Modifier.size(60.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar zona", modifier = Modifier.size(28.dp))
            }
        }
    }
}

// ─── Panel inferior de zonas ───────────────────────────────────────────────────

@Composable
private fun ZoneListPanel(
    zones: List<SafeZone>,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onCollapse: () -> Unit
) {
    Surface(
        modifier       = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp),
        shape          = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color          = NavyMedium,
        tonalElevation = 8.dp
    ) {
        Column {
            // Handle + título
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(TextSecondary.copy(alpha = 0.4f))
                        .align(Alignment.CenterVertically)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Zonas Seguras (${zones.size})",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Default.ExpandMore, contentDescription = "Colapsar",
                        tint = TextSecondary)
                }
            }

            HorizontalDivider(color = TextSecondary.copy(alpha = 0.2f))

            if (zones.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Toca + para agregar tu primera zona",
                        color = TextSecondary,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(zones, key = { it.id }) { zone ->
                        ZoneRow(zone = zone, onToggle = onToggle, onDelete = onDelete)
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneRow(
    zone: SafeZone,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Indicador de color
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (zone.isActive) AccentGreen else TextSecondary)
        )
        Spacer(modifier = Modifier.width(12.dp))

        // Icono
        Icon(
            imageVector = if (zone.name.contains("Casa", ignoreCase = true))
                Icons.Default.Home else Icons.Default.LocationOn,
            contentDescription = null,
            tint = if (zone.isActive) AccentGreen else TextSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(zone.name, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("${zone.radiusMeters.toInt()} m", color = TextSecondary, fontSize = 13.sp)
        }

        Switch(
            checked = zone.isActive,
            onCheckedChange = { onToggle(zone.id, it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor  = AccentGreen,
                checkedTrackColor  = AccentGreen.copy(alpha = 0.4f),
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = TextSecondary.copy(alpha = 0.2f)
            )
        )

        IconButton(onClick = { showConfirm = true }) {
            Icon(Icons.Default.Delete, contentDescription = "Eliminar",
                tint = AccentRed.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title   = { Text("¿Eliminar zona?", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
            text    = { Text("Se eliminará \"${zone.name}\" del mapa.", fontSize = 16.sp) },
            confirmButton = {
                Button(
                    onClick = { onDelete(zone.id); showConfirm = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = AccentRed),
                    modifier = Modifier.height(48.dp)
                ) { Text("Eliminar", fontSize = 16.sp) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Cancelar", fontSize = 16.sp)
                }
            },
            containerColor = NavyMedium,
            titleContentColor = TextPrimary,
            textContentColor  = TextSecondary
        )
    }
}

// ─── Banner de permiso ─────────────────────────────────────────────────────────

@Composable
private fun PermissionBanner(
    hasFine: Boolean,
    onRequestFine: () -> Unit,
    onRequestBg: () -> Unit
) {
    val (msg, label, action) = if (!hasFine) {
        Triple("Activa la ubicación para ver el mapa", "Activar", onRequestFine)
    } else {
        Triple("Activa 'Siempre' en ubicación para alertas en segundo plano", "Activar", onRequestBg)
    }

    Surface(
        modifier       = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape          = RoundedCornerShape(12.dp),
        color          = Color(0xFFFCE9D2),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(msg, color = Color(0xFF8A5A0F), fontSize = 13.sp, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = action,
                colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8C00)),
                modifier = Modifier.height(38.dp)
            ) { Text(label, color = Color.White, fontSize = 13.sp) }
        }
    }
}

// ─── Banner: sin guardianes en el círculo ──────────────────────────────────────

@Composable
private fun GuardianWarningBanner() {
    Surface(
        modifier       = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape          = RoundedCornerShape(12.dp),
        color          = Color(0xFFFCE9D2),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFF8A5A0F), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Todavía no hay ningún guardián en tu círculo. Si sales de una zona segura, no habrá nadie a quien avisar.",
                color = Color(0xFF8A5A0F),
                fontSize = 13.sp
            )
        }
    }
}

// ─── Diálogo "Agregar zona" ────────────────────────────────────────────────────

@Composable
private fun AddZoneDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, radius: Float) -> Unit
) {
    var name       by remember { mutableStateOf("") }
    var radiusText by remember { mutableStateOf("150") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva Zona Segura", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nombre") },
                    placeholder = { Text("Ej: Casa, Parque, Panadería") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
                OutlinedTextField(
                    value = radiusText,
                    onValueChange = { radiusText = it.filter { c -> c.isDigit() } },
                    label = { Text("Radio en metros") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
                Text(
                    "Se centrará en tu ubicación actual.",
                    color = TextSecondary, fontSize = 13.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim(), (radiusText.toFloatOrNull() ?: 150f).coerceIn(50f, 1000f))
                    }
                },
                enabled = name.isNotBlank(),
                colors  = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                modifier = Modifier.height(48.dp)
            ) { Text("Agregar", fontSize = 16.sp, color = TextPrimary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", fontSize = 16.sp) }
        },
        containerColor = NavyMedium,
        titleContentColor = TextPrimary,
        textContentColor  = TextSecondary
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = AccentGreen,
    focusedLabelColor    = AccentGreen,
    cursorColor          = AccentGreen,
    focusedTextColor     = TextPrimary,
    unfocusedTextColor   = TextPrimary,
    unfocusedBorderColor = NavyDark
)
