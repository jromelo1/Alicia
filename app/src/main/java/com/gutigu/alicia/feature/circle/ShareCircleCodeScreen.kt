package com.gutigu.alicia.feature.circle

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gutigu.alicia.ui.theme.AliciaAccent
import com.gutigu.alicia.ui.theme.AliciaBackground
import com.gutigu.alicia.ui.theme.AliciaSurfaceAlt
import com.gutigu.alicia.ui.theme.AliciaText
import com.gutigu.alicia.ui.theme.AliciaTextSecondary

// Paleta centralizada en ui/theme/Color.kt
private val NavyDark      = AliciaBackground   // fondo de pantalla
private val NavyMedium    = AliciaSurfaceAlt   // superficie del código (chip)
private val AccentGreen   = AliciaAccent
private val TextPrimary   = AliciaText
private val TextSecondary = AliciaTextSecondary

/**
 * Pantalla del Adulto Mayor tras el onboarding: crea su círculo y muestra el código
 * para compartir con la familia. Nunca pide correo ni contraseña.
 */
@Composable
fun ShareCircleCodeScreen(
    userName: String,
    onContinue: () -> Unit,
    viewModel: CircleOnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showRecover by rememberSaveable { mutableStateOf(false) }

    // Crea el círculo automáticamente solo mientras no se eligió "recuperar" —
    // si el Adulto Mayor ya tenía uno, no queremos crearle uno nuevo por accidente.
    LaunchedEffect(showRecover) {
        if (!showRecover) viewModel.ensureCircleAsOwner(userName)
    }

    if (showRecover) {
        RecoverCircleContent(
            state = state,
            onRecover = { code -> viewModel.recoverCircleAsElder(code, userName) },
            onBack = { showRecover = false },
            onContinue = onContinue
        )
        return
    }

    Box(
        modifier = Modifier.fillMaxSize().background(NavyDark),
        contentAlignment = Alignment.Center
    ) {
        when (val s = state) {
            is CircleOnboardingState.Idle,
            is CircleOnboardingState.Loading -> CircularProgressIndicator(color = AccentGreen)

            is CircleOnboardingState.Error -> Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("No pudimos crear tu círculo", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(s.message, color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                Button(
                    onClick = { viewModel.ensureCircleAsOwner(userName) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) { Text("Reintentar", color = TextPrimary, fontWeight = FontWeight.Bold) }
            }

            is CircleOnboardingState.Ready -> {
                val code = s.circleId
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text("🎉", fontSize = 56.sp)
                    Text(
                        "¡Ya tienes tu Círculo!",
                        color = TextPrimary,
                        fontFamily = MaterialTheme.typography.displayMedium.fontFamily,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Comparte este código con tu familia para que puedan cuidarte",
                        color = TextSecondary,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )

                    if (code != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NavyMedium, RoundedCornerShape(20.dp))
                                .padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                code,
                                color = AccentGreen,
                                fontSize = 40.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 6.sp
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Únete a mi Círculo de Confianza con este código: $code\n" +
                                            "Descarga la app y entra el código para que sepas que estoy bien cada día."
                                    )
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Compartir código"))
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = AccentGreen)
                            Spacer(modifier = Modifier.padding(4.dp))
                            Text("Compartir código", color = AccentGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                    ) {
                        Text("Continuar", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    }

                    Text(
                        "Podrás ver este código de nuevo en la pestaña Círculo",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        TextButton(
            onClick = { showRecover = true },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
        ) {
            Text(
                "¿Ya tenías un círculo con esta app? Recupéralo con tu código",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Recupera un círculo existente por código — para el Adulto Mayor que reinstaló o cambió de teléfono. */
@Composable
private fun RecoverCircleContent(
    state: CircleOnboardingState,
    onRecover: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    var code by rememberSaveable { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().background(NavyDark), contentAlignment = Alignment.Center) {
        when (state) {
            is CircleOnboardingState.Loading -> CircularProgressIndicator(color = AccentGreen)

            is CircleOnboardingState.Ready -> {
                val recoveredCode = state.circleId
                if (recoveredCode != null && state.recovered) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Text("🎉", fontSize = 56.sp)
                        Text(
                            "¡Recuperamos tu Círculo!",
                            color = TextPrimary,
                            fontFamily = MaterialTheme.typography.displayMedium.fontFamily,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Tu familia sigue conectada contigo, igual que antes.",
                            color = TextSecondary,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                        Button(
                            onClick = onContinue,
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                        ) {
                            Text("Continuar", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                } else {
                    // Ready sin recovered==true no debería llegar aquí; por seguridad, mostrar el formulario.
                    RecoverCircleForm(code = code, onCodeChange = { code = it }, onRecover = onRecover, onBack = onBack)
                }
            }

            is CircleOnboardingState.Error -> Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                RecoverCircleForm(code = code, onCodeChange = { code = it }, onRecover = onRecover, onBack = onBack)
                Text(state.message, color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
            }

            else -> RecoverCircleForm(code = code, onCodeChange = { code = it }, onRecover = onRecover, onBack = onBack)
        }
    }
}

@Composable
private fun RecoverCircleForm(
    code: String,
    onCodeChange: (String) -> Unit,
    onRecover: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("🔑", fontSize = 56.sp)
        Text(
            "Recupera tu círculo",
            color = TextPrimary,
            fontFamily = MaterialTheme.typography.displayMedium.fontFamily,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            "Ingresa el código que compartiste con tu familia — es el mismo de siempre, pídeselo si no lo recuerdas.",
            color = TextSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp
        )
        OutlinedTextField(
            value = code,
            onValueChange = { onCodeChange(it.uppercase()) },
            label = { Text("Código de invitación", color = TextSecondary) },
            placeholder = { Text("Ej. AB3CD9EF", color = TextSecondary.copy(alpha = 0.5f)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentGreen,
                unfocusedBorderColor = TextSecondary.copy(alpha = 0.4f),
                focusedLabelColor = AccentGreen,
                cursorColor = AccentGreen,
                focusedContainerColor = NavyMedium,
                unfocusedContainerColor = NavyMedium,
            ),
            modifier = Modifier.fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 22.sp, letterSpacing = 2.sp)
        )
        Button(
            onClick = { onRecover(code) },
            enabled = code.trim().length >= 6,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
        ) {
            Text("Recuperar mi círculo", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
        TextButton(onClick = onBack) {
            Text("Prefiero crear un círculo nuevo", color = TextSecondary, fontSize = 14.sp)
        }
    }
}
