package com.gutigu.alicia.feature.dailycall

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.draw.clip
import com.gutigu.alicia.ui.theme.AliciaAccentMuted
import com.gutigu.alicia.ui.theme.AliciaAccentSoft
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gutigu.alicia.R
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
import com.gutigu.alicia.ui.theme.AliciaOnAccent

private val NavyDark      = AliciaBackground
private val NavyMedium    = AliciaSurfaceAlt
private val AccentGreen   = AliciaAccent
private val AccentAmber   = AliciaWarning
private val TextPrimary   = AliciaText
private val TextSecondary = AliciaTextSecondary

/**
 * Perfil + activación de la llamada diaria de Alicia (agente de voz vía Retell).
 * Es una pantalla del perfil Familiar (Guardián y Miembro): la familia configura la
 * llamada y aquí queda el registro de lo que Alicia habló con el adulto mayor. El
 * adulto mayor no ve esta pantalla — solo contesta el teléfono.
 */
@Composable
fun DailyCallScreen(viewModel: DailyCallViewModel = hiltViewModel()) {
    val profileOrNull by viewModel.profile.collectAsState()

    // Mientras profileOrNull sea null (aún no llegó el primer valor real de
    // Firestore), no se dibuja el formulario — así nunca se rellena con datos
    // vacíos que luego "se quedan pegados" cuando sí llega la data real.
    if (profileOrNull == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(NavyDark),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = AccentGreen)
        }
        return
    }
    val profile = profileOrNull!!

    val history by viewModel.history.collectAsState()
    var openedCall by remember { mutableStateOf<CallRecord?>(null) }
    // Quien ya activó las llamadas ve primero el historial; el formulario queda plegado.
    // Quien aún no las activa lo ve abierto, porque es lo que viene a completar.
    var showSettings by remember { mutableStateOf(!profile.callEnabled) }

    var fullName by remember { mutableStateOf("") }
    var treatment by remember { mutableStateOf("") }
    var useTu by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf("es") }
    var originCountry by remember { mutableStateOf("") }
    var interests by remember { mutableStateOf("") }
    var familyInfo by remember { mutableStateOf("") }
    var familyNotes by remember { mutableStateOf("") }
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
            useTu = profile.useTu
            language = profile.language
            originCountry = profile.originCountry
            interests = profile.interests
            familyInfo = profile.familyInfo
            familyNotes = profile.familyNotes
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
        useTu = useTu,
        language = language,
        originCountry = originCountry.trim(),
        interests = interests.trim(),
        familyInfo = familyInfo.trim(),
        familyNotes = familyNotes.trim(),
        emergencyContactName = emergencyName.trim(),
        emergencyContactPhone = emergencyPhone.trim(),
        phoneE164 = "+${country.dialCode}${localPhone.filter(Char::isDigit)}",
        preferredHour = hour,
        preferredMinute = minute,
        timeZoneId = TimeZone.getDefault().id,
        callEnabled = enabled,
        consentAcceptedAt = profile.consentAcceptedAt,
        registeredBy = profile.registeredBy,
        profileStatus = profile.profileStatus,
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
            Text(stringResource(R.string.call_title), color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.call_subtitle),
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
                        Text(stringResource(R.string.call_enabled_label), color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (profile.callEnabled) stringResource(R.string.call_will_ring_at, "%02d:%02d".format(profile.preferredHour, profile.preferredMinute))
                            else if (!canEnable) stringResource(R.string.call_incomplete_hint)
                            else stringResource(R.string.call_save_to_enable_hint),
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.call_status_prefix, profileStatusLabel(profile.profileStatus)),
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        item {
            Text(stringResource(R.string.call_history_title), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            if (history.isEmpty()) {
                Text(
                    stringResource(R.string.call_history_empty),
                    color = TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        items(history, key = { it.id }) { call ->
            Box(modifier = Modifier.padding(bottom = 10.dp)) {
                CallHistoryCard(call, onClick = { openedCall = call })
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSettings = !showSettings }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.call_data_section_title),
                    color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(if (showSettings) "▴" else "▾", color = TextSecondary, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (showSettings) {
            item {
                FormField(stringResource(R.string.call_full_name_label), fullName, { fullName = it }, stringResource(R.string.call_full_name_placeholder), capitalizeWords = true)
                FormField(stringResource(R.string.call_nickname_label), treatment, { treatment = it }, stringResource(R.string.call_nickname_placeholder), capitalizeWords = true)

                Text(stringResource(R.string.call_formality_label), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                TwoChoiceChips(
                    optionALabel = stringResource(R.string.call_formality_formal), optionASelected = !useTu, onSelectA = { useTu = false },
                    optionBLabel = stringResource(R.string.call_formality_informal), optionBSelected = useTu, onSelectB = { useTu = true }
                )

                Text(stringResource(R.string.call_language_label), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                TwoChoiceChips(
                    optionALabel = stringResource(R.string.language_spanish), optionASelected = language == "es", onSelectA = { language = "es" },
                    optionBLabel = stringResource(R.string.language_english), optionBSelected = language == "en", onSelectB = { language = "en" }
                )

                FormField(stringResource(R.string.call_origin_country_label), originCountry, { originCountry = it }, stringResource(R.string.call_origin_country_placeholder), capitalizeWords = true)
                FormField(stringResource(R.string.call_interests_label), interests, { interests = it }, stringResource(R.string.call_interests_placeholder), minLines = 2)
                FormField(stringResource(R.string.call_family_label), familyInfo, { familyInfo = it }, stringResource(R.string.call_family_placeholder), minLines = 2)
                FormField(stringResource(R.string.call_family_notes_label), familyNotes, { familyNotes = it }, stringResource(R.string.call_family_notes_placeholder), minLines = 3)
            }

            item {
                Text(stringResource(R.string.call_phone_label), color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CountryCodeField(selected = country, onSelect = { country = it })
                    OutlinedTextField(
                        value = localPhone,
                        onValueChange = { localPhone = it },
                        placeholder = { Text(stringResource(R.string.call_phone_placeholder), color = TextSecondary.copy(alpha = 0.5f)) },
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
                FormField(stringResource(R.string.call_emergency_name_label), emergencyName, { emergencyName = it }, stringResource(R.string.call_emergency_name_placeholder), capitalizeWords = true)
                FormField(stringResource(R.string.call_emergency_phone_label), emergencyPhone, { emergencyPhone = it }, stringResource(R.string.call_emergency_phone_placeholder), keyboardType = KeyboardType.Phone)
            }

            item {
                Text(stringResource(R.string.call_preferred_time_label), color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
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
                    Text(stringResource(R.string.action_save), color = AliciaOnAccent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

        }

        item { Spacer(modifier = Modifier.height(40.dp)) }
    }

    openedCall?.let { call ->
        CallConversationSheet(
            call = call,
            elderName = profile.treatment
                .ifBlank { profile.fullName.substringBefore(' ') }
                .ifBlank { stringResource(R.string.call_speaker_elder_default) },
            onDismiss = { openedCall = null }
        )
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

/** Traduce el valor guardado de [CallProfile.profileStatus] a un texto legible. */
@Composable
private fun profileStatusLabel(status: String): String = when (status) {
    CallProfileStatus.PENDING_INTRO -> stringResource(R.string.call_status_pending_intro)
    else -> status
}

@Composable
private fun TwoChoiceChips(
    optionALabel: String,
    optionASelected: Boolean,
    onSelectA: () -> Unit,
    optionBLabel: String,
    optionBSelected: Boolean,
    onSelectB: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ChoiceChip(optionALabel, optionASelected, onSelectA, Modifier.weight(1f))
        ChoiceChip(optionBLabel, optionBSelected, onSelectB, Modifier.weight(1f))
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) AccentGreen else NavyMedium,
            contentColor = if (selected) AliciaOnAccent else TextSecondary
        )
    ) {
        Text(label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
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
            Text(stringResource(R.string.call_consent_title), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                stringResource(R.string.call_consent_body),
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
                Text(stringResource(R.string.call_consent_checkbox_label), color = TextPrimary, fontSize = 14.sp)
            }
            if (acceptedAt != null) {
                Text(
                    stringResource(R.string.call_consent_accepted_at, formatDate(acceptedAt)),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 40.dp)
                )
            }
        }
    }
}

@Composable
private fun CallHistoryCard(call: CallRecord, onClick: () -> Unit) {
    val hasDetail = call.transcript.isNotEmpty() || call.summary.isNotBlank()
    val (icon, tint, label) = when (call.status) {
        CallStatus.COMPLETED -> Triple(Icons.Default.Check, AccentGreen, stringResource(R.string.call_status_completed))
        CallStatus.NO_ANSWER -> Triple(Icons.Default.Warning, AccentAmber, stringResource(R.string.call_status_no_answer))
        CallStatus.IN_VOICEMAIL -> Triple(Icons.Default.Warning, AccentAmber, stringResource(R.string.call_status_voicemail))
        CallStatus.FAILED -> Triple(Icons.Default.Warning, AccentAmber, stringResource(R.string.call_status_failed))
        CallStatus.UNKNOWN -> Triple(Icons.Default.Call, TextSecondary, stringResource(R.string.call_status_unknown))
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = hasDetail, onClick = onClick),
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
                    Text(call.summary, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, modifier = Modifier.padding(top = 4.dp))
                }
                if (call.transcript.isNotEmpty()) {
                    Text(
                        stringResource(R.string.call_view_conversation),
                        color = AccentGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Conversación de una llamada — lo que habló Alicia y lo que respondió el adulto mayor
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallConversationSheet(call: CallRecord, elderName: String, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NavyDark
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(stringResource(R.string.call_conversation_title), color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                val duration = if (call.durationSeconds > 0) {
                    " · " + stringResource(R.string.call_duration, "%d:%02d".format(call.durationSeconds / 60, call.durationSeconds % 60))
                } else ""
                Text(formatDateTime(call.startedAt) + duration, color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
            }

            if (call.summary.isNotBlank()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AliciaAccentSoft)
                            .padding(14.dp)
                    ) {
                        Text(stringResource(R.string.call_conversation_summary_title), color = AccentGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(call.summary, color = TextPrimary, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            if (call.transcript.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.call_conversation_empty),
                        color = TextSecondary,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(call.transcript) { line -> ConversationBubble(line, elderName) }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun ConversationBubble(line: TranscriptLine, elderName: String) {
    val isAlicia = line.speaker == CallSpeaker.ALICIA
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isAlicia) Arrangement.Start else Arrangement.End
    ) {
        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = if (isAlicia) Alignment.Start else Alignment.End
        ) {
            Text(
                if (isAlicia) stringResource(R.string.call_speaker_alicia) else elderName,
                color = if (isAlicia) AccentGreen else TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 2.dp, start = 4.dp, end = 4.dp)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isAlicia) NavyMedium else AliciaAccentMuted)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(line.text, color = TextPrimary, fontSize = 16.sp, lineHeight = 22.sp)
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
