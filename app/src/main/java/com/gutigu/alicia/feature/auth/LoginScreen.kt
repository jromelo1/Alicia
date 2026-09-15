package com.gutigu.alicia.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gutigu.alicia.ui.theme.AliciaAccent
import com.gutigu.alicia.ui.theme.AliciaAlertSoft
import com.gutigu.alicia.ui.theme.AliciaBackground
import com.gutigu.alicia.ui.theme.AliciaFormAlert
import com.gutigu.alicia.ui.theme.AliciaSurfaceAlt
import com.gutigu.alicia.ui.theme.AliciaText
import com.gutigu.alicia.ui.theme.AliciaTextSecondary

// Paleta centralizada en ui/theme/Color.kt
private val NavyDark    = AliciaBackground   // fondo de pantalla
private val NavyMedium  = AliciaSurfaceAlt   // borde/relleno de campos
private val AccentGreen = AliciaAccent
private val TextPrimary   = AliciaText
private val TextSecondary = AliciaTextSecondary

@Composable
fun LoginScreen(
    onAuthenticated: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated) onAuthenticated()
    }

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
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Logo / título
            Text(
                text = "Círculo de Confianza",
                color = AccentGreen,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (state.isSignUp) "Crear cuenta" else "Bienvenido de vuelta",
                color = TextSecondary,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Campos
            EmailField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                enabled = !state.isLoading
            )

            PasswordField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = "Contraseña",
                enabled = !state.isLoading
            )

            if (state.isSignUp) {
                PasswordField(
                    value = state.confirmPassword,
                    onValueChange = viewModel::onConfirmPasswordChange,
                    label = "Confirmar contraseña",
                    enabled = !state.isLoading,
                    isLast = true,
                    onDone = { viewModel.submit() }
                )
            }

            // Error
            if (state.error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = AliciaAlertSoft)
                ) {
                    Text(
                        text = state.error!!,
                        color = AliciaFormAlert,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }

            // Botón principal
            Button(
                onClick = { viewModel.submit() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !state.isLoading,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        color = TextPrimary,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (state.isSignUp) "Crear cuenta" else "Iniciar sesión",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            // Toggle modo
            TextButton(onClick = { viewModel.toggleMode() }) {
                Text(
                    text = if (state.isSignUp)
                        "¿Ya tienes cuenta? Inicia sesión"
                    else
                        "¿Nuevo aquí? Crear cuenta",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun EmailField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Email") },
        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = TextSecondary) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
        modifier = Modifier.fillMaxWidth(),
        colors = outlinedFieldColors()
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    isLast: Boolean = false,
    onDone: (() -> Unit)? = null
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TextSecondary) },
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (passwordVisible) "Ocultar" else "Mostrar",
                    tint = TextSecondary
                )
            }
        },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = if (isLast) ImeAction.Done else ImeAction.Next
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
            onDone = { onDone?.invoke() ?: focusManager.clearFocus() }
        ),
        modifier = Modifier.fillMaxWidth(),
        colors = outlinedFieldColors()
    )
}

@Composable
private fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentGreen,
    unfocusedBorderColor = NavyMedium,
    focusedLabelColor = AccentGreen,
    cursorColor = AccentGreen,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    unfocusedLabelColor = TextSecondary,
    focusedLeadingIconColor = AccentGreen
)
