package com.gutigu.alicia.feature.circle

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gutigu.alicia.MemberRole
import com.gutigu.alicia.ui.theme.AliciaAccent
import com.gutigu.alicia.ui.theme.AliciaAccentBlue
import com.gutigu.alicia.ui.theme.AliciaBackground
import com.gutigu.alicia.ui.theme.AliciaFormAlert
import com.gutigu.alicia.ui.theme.AliciaSurface
import com.gutigu.alicia.ui.theme.AliciaSurfaceAlt
import com.gutigu.alicia.ui.theme.AliciaText
import com.gutigu.alicia.ui.theme.AliciaTextSecondary

// Paleta centralizada en ui/theme/Color.kt
private val NavyDark      = AliciaBackground   // fondo de pantalla
private val NavyMedium    = AliciaSurface      // superficie de tarjeta
private val SurfaceAlt    = AliciaSurfaceAlt
private val AccentGreen   = AliciaAccent
private val AccentBlue    = AliciaAccentBlue
private val AccentRed     = AliciaFormAlert
private val TextPrimary   = AliciaText
private val TextSecondary = AliciaTextSecondary

/** Pantalla del Familiar tras iniciar sesión: unirse a un círculo con el código del Adulto Mayor. */
@Composable
fun JoinCircleScreen(
    memberName: String,
    onJoined: () -> Unit,
    viewModel: CircleOnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var code by rememberSaveable { mutableStateOf("") }
    var elderName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var country by remember { mutableStateOf(CountryCodes.default) }
    var role by rememberSaveable { mutableStateOf(MemberRole.GUARDIAN) }

    LaunchedEffect(state) {
        val ready = state as? CircleOnboardingState.Ready
        if (ready?.circleId != null) onJoined()
    }

    Box(modifier = Modifier.fillMaxSize().background(NavyDark)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("🔗", fontSize = 56.sp)
            Text(
                "Únete a un círculo",
                color = TextPrimary,
                fontFamily = MaterialTheme.typography.displayMedium.fontFamily,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                "Pídele a tu familiar el código que le aparece al configurar su teléfono",
                color = TextSecondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp
            )

            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase() },
                label = { Text("Código de invitación", color = TextSecondary) },
                placeholder = { Text("Ej. AB3CD9EF", color = TextSecondary.copy(alpha = 0.5f)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
                textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 22.sp, letterSpacing = 2.sp)
            )

            OutlinedTextField(
                value = elderName,
                onValueChange = { elderName = it },
                label = { Text("¿Cómo se llama tu familiar?", color = TextSecondary) },
                placeholder = { Text("Ej. María — el adulto mayor, no tú", color = TextSecondary.copy(alpha = 0.5f)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
                textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 18.sp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CountryCodeField(
                    selected = country,
                    onSelect = { country = it }
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Tu teléfono", color = TextSecondary) },
                    placeholder = { Text("Para que puedan avisarte por SMS", color = TextSecondary.copy(alpha = 0.5f)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = fieldColors(),
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 18.sp)
                )
            }

            Text(
                "¿Qué papel vas a tener?",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RoleCard(
                    title = "Guardián",
                    description = "Recibo todas las alertas:\npánico, check-in y zonas",
                    color = AccentGreen,
                    isSelected = role == MemberRole.GUARDIAN,
                    modifier = Modifier.weight(1f)
                ) { role = MemberRole.GUARDIAN }

                RoleCard(
                    title = "Miembro",
                    description = "Solo recibo alertas\nde emergencia",
                    color = AccentBlue,
                    isSelected = role == MemberRole.MEMBER,
                    modifier = Modifier.weight(1f)
                ) { role = MemberRole.MEMBER }
            }

            if (state is CircleOnboardingState.Error) {
                Text(
                    (state as CircleOnboardingState.Error).message,
                    color = AccentRed,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }

            val isSubmitting = state is CircleOnboardingState.Loading
            Button(
                onClick = {
                    val fullPhone = "+${country.dialCode}${phone.filter(Char::isDigit)}"
                    Log.d("JoinCircleScreen", "Botón 'Unirme al círculo' tocado — code=$code")
                    viewModel.joinWithCode(code, memberName, fullPhone, role, elderName.trim())
                },
                enabled = !isSubmitting && code.isNotBlank() && elderName.isNotBlank() &&
                    phone.filter(Char::isDigit).length >= 6,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = TextPrimary, modifier = Modifier.height(24.dp), strokeWidth = 2.dp)
                } else {
                    Text("Unirme al círculo", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentGreen,
    focusedLabelColor = AccentGreen,
    cursorColor = AccentGreen,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    unfocusedBorderColor = SurfaceAlt,
    focusedContainerColor = SurfaceAlt,
    unfocusedContainerColor = SurfaceAlt
)
