package com.gutigu.alicia.feature.dailycall

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gutigu.alicia.feature.checkin.TimeAdjuster
import com.gutigu.alicia.feature.circle.CountryCodeField
import com.gutigu.alicia.feature.circle.CountryCodes
import com.gutigu.alicia.ui.theme.AliciaAccent
import com.gutigu.alicia.ui.theme.AliciaBackground
import com.gutigu.alicia.ui.theme.AliciaSurfaceAlt
import com.gutigu.alicia.ui.theme.AliciaText
import com.gutigu.alicia.ui.theme.AliciaTextSecondary
import com.gutigu.alicia.ui.theme.AliciaWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val NavyDark      = AliciaBackground
private val NavyMedium    = AliciaSurfaceAlt
private val AccentGreen   = AliciaAccent
private val AccentAmber   = AliciaWarning
private val TextPrimary   = AliciaText
private val TextSecondary = AliciaTextSecondary

/**
 * Perfil + activación de la llamada diaria de Alicia (agente de voz vía Retell).
 * La rellena quien tenga los datos a mano — Adulto Mayor o Familiar, ambos ven
 * y editan lo mismo, sincronizado por Firestore.
 */
@Composable
fun DailyCallScreen(viewModel: DailyCallViewModel = hiltViewModel()) {
    val profile by viewModel.profile.collectAsState()
    val history by viewModel.history.collectAsState()

    var fullName by remember { mutableStateOf("") }
    var treatment by remember { mutableStateOf("") }
    var originCountry by remember { mutableStateOf("") }
    var interests by remember { mutableStateOf("") }
    var familyInfo by remember { mutableStateOf("") }
    var emergencyName by remember { mutableStateOf("") }
    var emergencyPhone by remember { mutableStateOf("") }
    var country by remember { mutableStateOf(CountryCodes.default) }
    var localPhone by remember { mutableStateOf("") }
    var hour by remember { mutableStateOf(10) }
    var minute by remember { mutableStateOf(0) }
    var consentChecked by remember { mutableStateOf(false) }
    var loadedOnce by remember { mutableStateOf(false) }

    // Carga el formulario desde Firestore una sola vez — después el usuario manda,
    // no queremos pisar lo que está escribiendo si llega una actualización remota.
    LaunchedEffect(profile, loadedOnce) {
        if (!loadedOnce) {
            fullName = profile.fullName
            treatment = profile.treatment
            originCountry = profile.originCountry
            interests = profile.interests
            familyInfo = profile.familyInfo
            emergencyName = profile.emergencyContactName
            emergencyPhone = profile.emergencyContactPhone
            val (c, local) = CountryCodes.splitPhone(profile.phoneE164)
            country = c
            localPhone = local
            hour = profile.preferredHour
            minute = profile.preferredMinute
            consentChecked = profile.consentAcceptedAt != null
            loadedOnce = true
        }
    }

    fun buildProfile(enabled: Boolean) = CallProfile(
        fullName = fullName.trim(),
        treatment = treatment.trim(),
        originCountry = originCountry.trim(),
        interests = interests.trim(),
        familyInfo = familyInfo.trim(),
        emergencyContactName = emergencyName.trim(),
        emergencyContactPhone = emergencyPhone.trim(),
        phoneE164 = "+${country.dialCode}${localPhone.filter(Char::isDigit)}",
        preferredHour = hour,
        preferredMinute = minute,
        timeZoneId = TimeZone.getDefault().id,
        callEnabled = enabled,
        consentAcceptedAt = profile.consentAcceptedAt,
        lastCallNotes = profile.lastCallNotes,
        lastCallAt = profile.lastCallAt
    )

    val canEnable = fullName.isNotBlank() && localPhone.filter(Char::isDigit).length >= 6 && consentChecked

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDark)
            .padding(horizontal = 20.dp)
    ) {
        item { Spacer(modifier = Modifier.height(24.dp)) }

        item {
            Text("📞 Llamada diaria", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "Alicia llama todos los días a la hora que elijas, para conversar un rato",
                color = TextSecondary,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NavyMedium)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Llamada activada", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (profile.callEnabled) "Sonará a las %02d:%02d".format(profile.preferredHour, profile.preferredMinute)
                            else if (!canEnable) "Completa el nombre, el teléfono y el consentimiento para activarla"
                            else "Guarda para activarla",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    Switch(
                        checked = profile.callEnabled,
                        onCheckedChange = { checked -> viewModel.saveProfile(buildProfile(checked), consentChecked) },
                        enabled = canEnable || profile.callEnabled,
                        colors = SwitchDefaults.colors(checkedThumbColor = AccentGreen, checkedTrackColor = AccentGreen.copy(alpha = 0.4f))
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        item {
            Text("Datos para la llamada", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
        }

        item {
            FormField("Nombre completo", fullName, { fullName = it }, "Ej. Rosa Elena Gómez", capitalizeWords = true)
            FormField("¿Cómo le gusta que le llamen?", treatment, { treatment = it }, "Ej. Doña Rosa", capitalizeWords = true)
            FormField("País de origen", originCountry, { originCountry = it }, "Ej. Colombia", capitalizeWords = true)
            FormField("Gustos y temas de conversación", interests, { interests = it }, "Ej. le encanta hablar de sus nietos, jardinería y telenovelas", minLines = 2)
            FormField("Familia", familyInfo, { familyInfo = it }, "Ej. su hija se llama Marta, su nieto es Andrés", minLines = 2)
        }

        item {
            Text("Teléfono al que se llama", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CountryCodeField(selected = country, onSelect = { country = it })
                OutlinedTextField(
                    value = localPhone,
                    onValueChange = { localPhone = it },
                    placeholder = { Text("3001234567", color = TextSecondary.copy(alpha = 0.5f)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = fieldColors(),
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 18.sp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        item {
            FormField("Nombre del contacto de emergencia", emergencyName, { emergencyName = it }, "Ej. Marta Gómez", capitalizeWords = true)
            FormField("Teléfono de emergencia", emergencyPhone, { emergencyPhone = it }, "Solo para casos graves durante la llamada", keyboardType = KeyboardType.Phone)
        }

        item {
            Text("Hora preferida de la llamada", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            Spacer(modifier = Modifier.height(8.dp))
            TimeAdjuster(hour = hour, minute = minute, enabled = true, onAdjust = { deltaMinutes ->
                val total = hour * 60 + minute + deltaMinutes
                val norm = ((total % 1440) + 1440) % 1440
                hour = norm / 60; minute = norm % 60
            })
            Spacer(modifier = Modifier.height(20.dp))
        }

        item {
            ConsentSection(
                checked = consentChecked,
                acceptedAt = profile.consentAcceptedAt,
                onCheckedChange = { consentChecked = it }
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        item {
            Button(
                onClick = { viewModel.saveProfile(buildProfile(profile.callEnabled), consentChecked) },
                enabled = fullName.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
            ) {
                Text("Guardar", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(28.dp))
        }

        item {
            Text("Llamadas recientes", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            if (history.isEmpty()) {
                Text(
                    "Todavía no ha habido ninguna llamada.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        items(history, key = { it.id }) { call ->
            Box(modifier = Modifier.padding(bottom = 10.dp)) {
                CallHistoryCard(call)
            }
        }

        item { Spacer(modifier = Modifier.height(40.dp)) }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minLines: Int = 1,
    capitalizeWords: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Text(label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = TextSecondary.copy(alpha = 0.5f)) },
        singleLine = minLines == 1,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(
            capitalization = if (capitalizeWords) KeyboardCapitalization.Words else KeyboardCapitalization.Sentences,
            keyboardType = keyboardType
        ),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
        textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 16.sp)
    )
}

@Composable
private fun ConsentSection(
    checked: Boolean,
    acceptedAt: Long?,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyMedium)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Consentimiento para la llamada con IA", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Estos datos y el audio de la llamada se comparten con Retell, el servicio de voz con inteligencia artificial que hace la llamada en nombre de Alicia. Puedes desactivar la llamada diaria cuando quieras.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = CheckboxDefaults.colors(checkedColor = AccentGreen)
                )
                Text("Autorizo la llamada diaria con IA", color = TextPrimary, fontSize = 14.sp)
            }
            if (acceptedAt != null) {
                Text(
                    "Aceptado el ${formatDate(acceptedAt)}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 40.dp)
                )
            }
        }
    }
}

@Composable
private fun CallHistoryCard(call: CallRecord) {
    val (icon, tint, label) = when (call.status) {
        CallStatus.COMPLETED -> Triple(Icons.Default.Check, AccentGreen, "Completada")
        CallStatus.NO_ANSWER -> Triple(Icons.Default.Warning, AccentAmber, "No contestó")
        CallStatus.IN_VOICEMAIL -> Triple(Icons.Default.Warning, AccentAmber, "Buzón de voz")
        CallStatus.FAILED -> Triple(Icons.Default.Warning, AccentAmber, "No se pudo llamar")
        CallStatus.UNKNOWN -> Triple(Icons.Default.Call, TextSecondary, "Registrada")
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavyMedium)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row {
                    Text(formatDateTime(call.startedAt), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("· $label", color = tint, fontSize = 13.sp)
                }
                if (call.summary.isNotBlank()) {
                    Text(call.summary, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentGreen,
    unfocusedBorderColor = TextSecondary.copy(alpha = 0.4f),
    focusedLabelColor = AccentGreen,
    cursorColor = AccentGreen,
    focusedContainerColor = NavyMedium,
    unfocusedContainerColor = NavyMedium
)

private fun formatDate(millis: Long): String =
    SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("es", "CO")).format(Date(millis))

private fun formatDateTime(millis: Long): String =
    SimpleDateFormat("EEEE d 'de' MMMM, HH:mm", Locale("es", "CO")).format(Date(millis)).replaceFirstChar { it.uppercase() }
