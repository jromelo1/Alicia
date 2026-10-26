package com.gutigu.alicia.feature.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gutigu.alicia.R
import com.gutigu.alicia.ui.theme.AliciaAccent
import com.gutigu.alicia.ui.theme.AliciaSecondary
import com.gutigu.alicia.ui.theme.AliciaBackground
import com.gutigu.alicia.ui.theme.AliciaSurfaceAlt
import com.gutigu.alicia.ui.theme.AliciaText
import com.gutigu.alicia.ui.theme.AliciaTextSecondary
import com.gutigu.alicia.ui.theme.LanguageChipsRow
import com.gutigu.alicia.ui.theme.AliciaOnAccent

// Paleta centralizada en ui/theme/Color.kt
private val NavyDark      = AliciaBackground   // fondo de pantalla
private val NavyMedium    = AliciaSurfaceAlt   // superficie secundaria (campo, tarjeta sin elegir)
private val AccentGreen   = AliciaAccent
private val AccentBlue    = AliciaSecondary
private val TextPrimary   = AliciaText
private val TextSecondary = AliciaTextSecondary

@Composable
fun OnboardingScreen(onProfileSelected: (UserProfile, String) -> Unit) {
    var selectedProfile by rememberSaveable { mutableStateOf<UserProfile?>(null) }
    var userName by rememberSaveable { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Idioma de toda la app: lo primero que se elige, antes que el perfil,
            // para que el resto del flujo de configuración (privacidad, login,
            // código del círculo) ya aparezca en el idioma correcto.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                LanguageChipsRow()
            }

            Image(
                painter = painterResource(R.drawable.logo_alicia),
                contentDescription = null,
                modifier = Modifier.width(104.dp)
            )

            Text(
                text = stringResource(R.string.onboarding_title),
                color = AccentGreen,
                fontFamily = MaterialTheme.typography.displayLarge.fontFamily,
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                lineHeight = 44.sp
            )

            Text(
                text = stringResource(R.string.onboarding_subtitle),
                color = TextSecondary,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tarjeta Adulto Mayor
            ProfileCard(
                emoji = "👴",
                title = stringResource(R.string.onboarding_elder_title),
                description = stringResource(R.string.onboarding_elder_description),
                color = AccentGreen,
                isSelected = selectedProfile == UserProfile.ADULTO_MAYOR,
                onClick = { selectedProfile = UserProfile.ADULTO_MAYOR }
            )

            // Tarjeta Familiar
            ProfileCard(
                emoji = "👨‍👩‍👧",
                title = stringResource(R.string.onboarding_familiar_title),
                description = stringResource(R.string.onboarding_familiar_description),
                color = AccentBlue,
                isSelected = selectedProfile == UserProfile.FAMILIAR,
                onClick = { selectedProfile = UserProfile.FAMILIAR }
            )

            // Campo nombre (aparece al seleccionar perfil) — subido ~medio centímetro
            // (28dp) respecto a la posición que le daría el espaciado uniforme de la
            // columna, a pedido explícito: quedaba con demasiado aire debajo de las
            // tarjetas de perfil.
            if (selectedProfile != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-8).dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    OutlinedTextField(
                        value = userName,
                        onValueChange = { userName = it },
                        label = {
                            Text(
                                stringResource(
                                    if (selectedProfile == UserProfile.ADULTO_MAYOR)
                                        R.string.onboarding_name_label_elder else R.string.onboarding_name_label_familiar
                                ),
                                color = TextSecondary
                            )
                        },
                        placeholder = {
                            Text(
                                stringResource(
                                    if (selectedProfile == UserProfile.ADULTO_MAYOR)
                                        R.string.onboarding_name_placeholder_elder else R.string.onboarding_name_placeholder_familiar
                                ),
                                color = TextSecondary.copy(alpha = 0.5f)
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGreen,
                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.4f),
                            focusedLabelColor = AccentGreen,
                            cursorColor = AccentGreen,
                            focusedContainerColor = NavyMedium,
                            unfocusedContainerColor = NavyMedium,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 20.sp)
                    )

                    Button(
                        onClick = {
                            selectedProfile?.let { profile ->
                                onProfileSelected(profile, userName.trim())
                            }
                        },
                        enabled = userName.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                    ) {
                        Text(
                            stringResource(R.string.onboarding_start_button),
                            color = AliciaOnAccent,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(
    emoji: String,
    title: String,
    description: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) color else TextSecondary.copy(alpha = 0.3f)
    val bgColor     = if (isSelected) color.copy(alpha = 0.12f) else NavyMedium

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(24.dp)
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(emoji, fontSize = 48.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                title,
                color = if (isSelected) color else TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                description,
                color = TextSecondary,
                fontSize = 16.sp,
                lineHeight = 22.sp
            )
        }
    }
}
