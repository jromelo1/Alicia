package com.gutigu.alicia.feature.profile

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gutigu.alicia.R
import com.gutigu.alicia.ui.theme.AliciaAccent
import com.gutigu.alicia.ui.theme.AliciaBackground
import com.gutigu.alicia.ui.theme.AliciaFormAlert
import com.gutigu.alicia.ui.theme.AliciaSurfaceAlt
import com.gutigu.alicia.ui.theme.AliciaText
import com.gutigu.alicia.ui.theme.AliciaTextSecondary
import com.gutigu.alicia.ui.theme.AliciaOnAccent

// Paleta centralizada en ui/theme/Color.kt
private val NavyDark      = AliciaBackground   // fondo de pantalla
private val NavyMedium    = AliciaSurfaceAlt   // superficie de la tarjeta de texto
private val AccentGreen   = AliciaAccent
private val AccentRed     = AliciaFormAlert
private val TextPrimary   = AliciaText
private val TextSecondary = AliciaTextSecondary

/**
 * ⚠️ TODO_LEGAL — bloqueante antes de producción:
 * 1. privacy_responsible_pending (strings.xml) hoy dice solo "Gutigu" — texto temporal
 *    puesto para no mostrar un placeholder en corchetes en una grabación de demo.
 *    Sigue faltando razón social/NIT/domicilio real (no son datos que se puedan
 *    inventar — pídanselos a quien registre la empresa) antes de producción.
 * 2. Confirmar con un abogado si aplica registro en el RNBD de la SIC (Decreto 1074/2015).
 * 3. Confirmar el mecanismo de transferencia internacional (Firebase/Google Cloud corre
 *    fuera de Colombia) — el texto de abajo lo declara, pero su suficiencia legal la
 *    debe validar un abogado, no esta pantalla.
 * 4. AddMemberSheet permite agregar un guardián eligiéndolo de la agenda de contactos —
 *    esa persona nunca pasa por esta pantalla de consentimiento. Decisión de producto/legal
 *    pendiente: ¿se le debe notificar o pedir confirmación al unirse?
 * El resto del texto (estructura de secciones, derechos ARCO, plazos, queja ante la SIC)
 * sigue lenguaje sencillo alineado a la Ley 1581 de 2012 y el Decreto 1377 de 2013, pero
 * NO reemplaza la revisión de un abogado antes de tener usuarios reales.
 */
@Composable
fun PrivacyConsentScreen(
    onAccept: () -> Unit
) {
    val context = LocalContext.current
    var showDeclineConfirm by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(NavyDark)) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text(
                stringResource(R.string.privacy_title),
                color = TextPrimary,
                fontFamily = MaterialTheme.typography.displayMedium.fontFamily,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .background(NavyMedium, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ConsentSection(
                    stringResource(R.string.privacy_section1_title),
                    stringResource(R.string.privacy_responsible_pending)
                )
                ConsentSection(
                    stringResource(R.string.privacy_section2_title),
                    stringResource(R.string.privacy_section2_body)
                )
                ConsentSection(
                    stringResource(R.string.privacy_section3_title),
                    stringResource(R.string.privacy_section3_body)
                )
                ConsentSection(
                    stringResource(R.string.privacy_section4_title),
                    stringResource(R.string.privacy_section4_body)
                )
                ConsentSection(
                    stringResource(R.string.privacy_section5_title),
                    stringResource(R.string.privacy_section5_body)
                )
                ConsentSection(
                    stringResource(R.string.privacy_section6_title),
                    stringResource(R.string.privacy_section6_body)
                )
                ConsentSection(
                    stringResource(R.string.privacy_section7_title),
                    stringResource(R.string.privacy_section7_body)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
            ) {
                Text(stringResource(R.string.privacy_accept_button), color = AliciaOnAccent, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { showDeclineConfirm = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(stringResource(R.string.privacy_decline_button), color = AccentRed, fontSize = 16.sp)
            }
        }
    }

    if (showDeclineConfirm) {
        AlertDialog(
            onDismissRequest = { showDeclineConfirm = false },
            title = { Text(stringResource(R.string.privacy_decline_dialog_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(stringResource(R.string.privacy_decline_dialog_text))
            },
            confirmButton = {
                TextButton(onClick = { (context as? Activity)?.finish() }) {
                    Text(stringResource(R.string.privacy_close_app_button), color = AccentRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeclineConfirm = false }) {
                    Text(stringResource(R.string.privacy_keep_viewing_button))
                }
            }
        )
    }
}

/**
 * [highlight] resalta en rojo texto que sigue pendiente de completar (p. ej. la
 * identidad del responsable del tratamiento) — para que no pase inadvertido en pruebas.
 */
@Composable
private fun ConsentSection(title: String, body: String, highlight: Boolean = false) {
    Column {
        Text(title, color = AccentGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            body,
            color = if (highlight) AccentRed else TextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal
        )
    }
}
