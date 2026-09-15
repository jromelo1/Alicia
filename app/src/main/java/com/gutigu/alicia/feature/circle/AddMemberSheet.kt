package com.gutigu.alicia.feature.circle

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
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

// Abre la agenda filtrada a contactos con número — no requiere READ_CONTACTS
private object PickPhoneContact : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: android.content.Context, input: Unit) =
        Intent(Intent.ACTION_PICK, Phone.CONTENT_URI)
    override fun parseResult(resultCode: Int, intent: Intent?) =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

// ─── Paleta, centralizada en ui/theme/Color.kt ───────────────────────────────
private val NavyDark     = AliciaBackground   // fondo de pantalla
private val NavyMedium   = AliciaSurface      // superficie de tarjeta
private val SurfaceAlt   = AliciaSurfaceAlt
private val AccentGreen  = AliciaAccent
private val AccentBlue   = AliciaAccentBlue
private val AccentRed    = AliciaFormAlert
private val TextPrimary  = AliciaText
private val TextSecondary= AliciaTextSecondary

// ─────────────────────────────────────────────
//  Form state local — no necesita entrar al ViewModel
// ─────────────────────────────────────────────

private data class MemberFormState(
    val name: String = "",
    val phone: String = "",
    val country: CountryDialCode = CountryCodes.default,
    val role: MemberRole = MemberRole.GUARDIAN,
    val notifyOnPanic: Boolean = true,
    val nameError: String? = null,
    val phoneError: String? = null
)

private fun MemberFormState.validate(): MemberFormState {
    val nameErr  = if (name.isBlank()) "Escribe el nombre de la persona" else null
    val phoneErr = when {
        phone.isBlank()                    -> "Escribe el número de teléfono"
        phone.filter(Char::isDigit).length < 6 -> "El número se ve incompleto para ${country.name}"
        else                               -> null
    }
    return copy(nameError = nameErr, phoneError = phoneErr)
}

private val MemberFormState.isValid get() = nameError == null && phoneError == null

// ─────────────────────────────────────────────
//  BottomSheet principal
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMemberSheet(
    sheetState: SheetState,
    editingMember: CircleMember?,
    onDismiss: () -> Unit,
    onSave: (CircleMember) -> Unit
) {
    val context = LocalContext.current

    // Pre-poblar el formulario si estamos editando
    var form by remember(editingMember) {
        mutableStateOf(
            if (editingMember != null) {
                val (country, localDigits) = CountryCodes.splitPhone(editingMember.phone)
                MemberFormState(
                    name          = editingMember.name,
                    phone         = localDigits,
                    country       = country,
                    role          = editingMember.role,
                    notifyOnPanic = editingMember.notifyOnPanic
                )
            } else {
                MemberFormState()
            }
        )
    }

    // Launcher que abre la agenda del teléfono y rellena el formulario automáticamente
    val pickContactLauncher = rememberLauncherForActivityResult(PickPhoneContact) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.query(
            uri,
            arrayOf(Phone.NUMBER, Phone.DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val rawPhone = cursor.getString(cursor.getColumnIndexOrThrow(Phone.NUMBER)) ?: ""
                val name     = cursor.getString(cursor.getColumnIndexOrThrow(Phone.DISPLAY_NAME)) ?: ""
                val (country, localDigits) = CountryCodes.splitPhone(rawPhone)
                form = form.copy(
                    name       = name,
                    phone      = localDigits,
                    country    = country,
                    nameError  = null,
                    phoneError = null
                )
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NavyMedium,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TextSecondary.copy(alpha = 0.5f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // ── Título ────────────────────────────────
            Text(
                text = if (editingMember != null) "Editar miembro" else "Agregar al círculo",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Importar desde la agenda ───────────────
            Button(
                onClick = { pickContactLauncher.launch(Unit) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SurfaceAlt,
                    contentColor = AccentGreen
                )
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Importar desde Contactos",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                "— o escribe los datos manualmente —",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ── Nombre ────────────────────────────────
            OutlinedTextField(
                value = form.name,
                onValueChange = { form = form.copy(name = it, nameError = null) },
                label = { Text("Nombre", color = TextSecondary) },
                placeholder = { Text("Ej. María García", color = TextSecondary.copy(alpha = 0.5f)) },
                isError = form.nameError != null,
                supportingText = form.nameError?.let { err ->
                    { Text(err, color = AccentRed) }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = TextPrimary,
                    fontSize = 18.sp
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ── Teléfono ──────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CountryCodeField(
                    selected = form.country,
                    onSelect = { form = form.copy(country = it) }
                )

                OutlinedTextField(
                    value = form.phone,
                    onValueChange = { form = form.copy(phone = it, phoneError = null) },
                    label = { Text("Teléfono", color = TextSecondary) },
                    placeholder = { Text("Ej. 3001234567", color = TextSecondary.copy(alpha = 0.5f)) },
                    isError = form.phoneError != null,
                    supportingText = form.phoneError?.let { err ->
                        { Text(err, color = AccentRed) }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done
                    ),
                    colors = fieldColors(),
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = TextPrimary,
                        fontSize = 18.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Rol ───────────────────────────────────
            Text("¿Qué papel tendrá en tu círculo?",
                color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RoleCard(
                    title = "Guardián",
                    description = "Recibe todas las alertas:\npánico, check-in y zonas",
                    color = AccentGreen,
                    isSelected = form.role == MemberRole.GUARDIAN,
                    modifier = Modifier.weight(1f)
                ) { form = form.copy(role = MemberRole.GUARDIAN) }

                RoleCard(
                    title = "Miembro",
                    description = "Solo recibe alertas\nde emergencia",
                    color = AccentBlue,
                    isSelected = form.role == MemberRole.MEMBER,
                    modifier = Modifier.weight(1f)
                ) { form = form.copy(role = MemberRole.MEMBER) }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Guardar ───────────────────────────────
            Button(
                onClick = {
                    val validated = form.validate()
                    form = validated
                    if (validated.isValid) {
                        val member = CircleMember(
                            userId        = editingMember?.userId ?: "",
                            name          = validated.name.trim(),
                            phone         = "+${validated.country.dialCode}${validated.phone.filter(Char::isDigit)}",
                            role          = validated.role,
                            notifyOnPanic = validated.notifyOnPanic
                        )
                        onSave(member)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (editingMember != null) "Guardar cambios" else "Agregar al círculo",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Tarjeta de selección de rol
// ─────────────────────────────────────────────

@Composable
fun RoleCard(
    title: String,
    description: String,
    color: Color,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val borderColor = if (isSelected) color else TextSecondary.copy(alpha = 0.3f)
    val bgColor     = if (isSelected) color.copy(alpha = 0.12f) else SurfaceAlt

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            // Indicador de selección
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) color else Color.Transparent)
                    .border(2.dp, if (isSelected) color else TextSecondary.copy(alpha = 0.5f), CircleShape)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                title,
                color = if (isSelected) color else TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                description,
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

// ─────────────────────────────────────────────
//  Colores para OutlinedTextField
// ─────────────────────────────────────────────

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = AccentGreen,
    unfocusedBorderColor = TextSecondary.copy(alpha = 0.4f),
    errorBorderColor     = AccentRed,
    cursorColor          = AccentGreen,
    focusedLabelColor    = AccentGreen,
    unfocusedLabelColor  = TextSecondary,
    focusedContainerColor   = SurfaceAlt,
    unfocusedContainerColor = SurfaceAlt,
    errorContainerColor     = SurfaceAlt
)
