package com.gutigu.alicia.feature.circle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gutigu.alicia.CircleMember
import com.gutigu.alicia.MemberRole
import com.gutigu.alicia.ui.theme.AliciaAccent
import com.gutigu.alicia.ui.theme.AliciaAccentBlue
import com.gutigu.alicia.ui.theme.AliciaBackground
import com.gutigu.alicia.ui.theme.AliciaFormAlert
import com.gutigu.alicia.ui.theme.AliciaSurface
import com.gutigu.alicia.ui.theme.AliciaSurfaceAlt
import com.gutigu.alicia.ui.theme.AliciaText
import com.gutigu.alicia.ui.theme.AliciaTextSecondary

// ─── Paleta, centralizada en ui/theme/Color.kt ───────────────────────────────
private val NavyDark      = AliciaBackground   // fondo de pantalla
private val NavyMedium    = AliciaSurface      // superficie de tarjeta
private val SurfaceAlt    = AliciaSurfaceAlt
private val AccentGreen   = AliciaAccent
private val AccentBlue    = AliciaAccentBlue
private val AccentRed     = AliciaFormAlert   // destructivo — distinto del alert #E8342A, reservado a emergencias
private val TextPrimary   = AliciaText
private val TextSecondary = AliciaTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleScreen(
    viewModel: CircleViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Estado local para el diálogo de confirmación de borrado
    var memberToDelete by remember { mutableStateOf<CircleMember?>(null) }

    Scaffold(
        containerColor = NavyDark,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openAddSheet() },
                containerColor = AccentGreen,
                contentColor = NavyDark,
                icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(26.dp)) },
                text = { Text("Agregar miembro", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // ── Encabezado ────────────────────────────────────
            Text(
                "Tu Círculo de Confianza",
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            state.circleCode?.let { code ->
                Row(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NavyMedium)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Código: ", color = TextSecondary, fontSize = 13.sp)
                    Text(code, color = AccentGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }

            val memberCount = state.members.size
            Text(
                when (memberCount) {
                    0    -> "Aún no hay nadie en tu círculo"
                    1    -> "1 persona en tu círculo"
                    else -> "$memberCount personas en tu círculo"
                },
                color = TextSecondary,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            // ── Leyenda de roles ──────────────────────────────
            AnimatedVisibility(visible = state.members.isNotEmpty()) {
                RoleLegend()
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Lista / Estado vacío ──────────────────────────
            if (state.members.isEmpty()) {
                EmptyCircleState(onAdd = { viewModel.openAddSheet() })
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.members, key = { it.userId }) { member ->
                        MemberCard(
                            member = member,
                            onEdit   = { viewModel.openEditSheet(member) },
                            onDelete = { memberToDelete = member }
                        )
                    }
                    // Espacio para que el FAB no tape el último item
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    // ── BottomSheet agregar / editar ──────────────────────────
    if (state.showSheet) {
        AddMemberSheet(
            sheetState    = sheetState,
            editingMember = state.editingMember,
            onDismiss     = { viewModel.closeSheet() },
            onSave        = { viewModel.saveMember(it) }
        )
    }

    // ── Diálogo de confirmación de borrado ────────────────────
    memberToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { memberToDelete = null },
            containerColor   = NavyMedium,
            title = {
                Text("¿Quitar a ${target.name}?", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Ya no recibirá notificaciones de tu círculo.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeMember(target.userId)
                        memberToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) {
                    Text("Quitar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToDelete = null }) {
                    Text("Cancelar", color = AccentGreen)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────
//  Tarjeta de miembro
// ─────────────────────────────────────────────

@Composable
private fun MemberCard(
    member: CircleMember,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyMedium),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar con iniciales
            MemberAvatar(name = member.name, role = member.role)

            Spacer(modifier = Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(member.name, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatPhone(member.phone),
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Etiquetas de notificaciones que recibe
                NotificationBadges(member.role)
            }

            // Acciones — 48dp (no 36dp) y separadas entre sí para no fallar el toque
            // con temblor de manos; "Quitar" además queda con un salto extra para no
            // quedar pegada a "Editar" y disparar el borrado por error.
            Column(horizontalAlignment = Alignment.End) {
                IconButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${member.phone}"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = "Llamar",
                        tint = AccentGreen, modifier = Modifier.size(24.dp))
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar",
                        tint = TextSecondary, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.height(10.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Quitar",
                        tint = AccentRed.copy(alpha = 0.8f), modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Avatar con iniciales
// ─────────────────────────────────────────────

@Composable
fun MemberAvatar(name: String, role: MemberRole, modifier: Modifier = Modifier) {
    val initials = name.split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
    val color = if (role == MemberRole.GUARDIAN) AccentGreen else AccentBlue

    Box(
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials.ifEmpty { "?" },
            color = color,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ─────────────────────────────────────────────
//  Etiquetas de notificaciones
// ─────────────────────────────────────────────

@Composable
private fun NotificationBadges(role: MemberRole) {
    val badges = when (role) {
        MemberRole.GUARDIAN -> listOf(
            "Emergencia" to AccentRed.copy(alpha = 0.8f),
            "Check-in"   to AccentGreen.copy(alpha = 0.8f),
            "Zonas"      to AccentBlue.copy(alpha = 0.8f)
        )
        MemberRole.MEMBER -> listOf(
            "Emergencia" to AccentRed.copy(alpha = 0.8f)
        )
        else -> emptyList()
    }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        badges.forEach { (label, color) ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(color.copy(alpha = 0.15f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Leyenda de roles
// ─────────────────────────────────────────────

@Composable
private fun RoleLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LegendItem(
            color = AccentGreen,
            label = "Guardián",
            sublabel = "Todas las alertas",
            modifier = Modifier.weight(1f)
        )
        LegendItem(
            color = AccentBlue,
            label = "Miembro",
            sublabel = "Solo emergencias",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String, sublabel: String, modifier: Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(sublabel, color = TextSecondary, fontSize = 11.sp)
        }
    }
}

// ─────────────────────────────────────────────
//  Estado vacío
// ─────────────────────────────────────────────

@Composable
private fun EmptyCircleState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("👥", fontSize = 72.sp, textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "Tu círculo está vacío",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            "Agrega a las personas de confianza que quieres\nque sepan si estás bien.",
            color = TextSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onAdd,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = NavyDark,
                modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Agregar primer miembro", color = NavyDark,
                fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────
//  Utilidades
// ─────────────────────────────────────────────

private fun formatPhone(raw: String): String {
    val (country, local) = CountryCodes.splitPhone(raw)
    val hasCountryPrefix = raw.trimStart().startsWith("+")
    return when {
        !hasCountryPrefix && local.length == 10 ->
            "(${local.take(3)}) ${local.substring(3, 6)}-${local.substring(6)}"
        local.length in 7..10 ->
            "+${country.dialCode} $local"
        else -> raw
    }
}
