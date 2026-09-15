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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gutigu.alicia.ui.theme.AliciaAccent
import com.gutigu.alicia.ui.theme.AliciaBackground
import com.gutigu.alicia.ui.theme.AliciaFormAlert
import com.gutigu.alicia.ui.theme.AliciaSurfaceAlt
import com.gutigu.alicia.ui.theme.AliciaText
import com.gutigu.alicia.ui.theme.AliciaTextSecondary

// Paleta centralizada en ui/theme/Color.kt
private val NavyDark      = AliciaBackground   // fondo de pantalla
private val NavyMedium    = AliciaSurfaceAlt   // superficie de la tarjeta de texto
private val AccentGreen   = AliciaAccent
private val AccentRed     = AliciaFormAlert
private val TextPrimary   = AliciaText
private val TextSecondary = AliciaTextSecondary

/**
 * ⚠️ TODO_LEGAL — bloqueante antes de producción:
 * 1. [RESPONSABLE_PENDIENTE] abajo tiene razón social/NIT/domicilio sin completar
 *    (no son datos que se puedan inventar — pídanselos a quien registre la empresa).
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
private const val RESPONSABLE_PENDIENTE =
    "[Completar: razón social, NIT y domicilio del responsable del tratamiento]"
@Composable
fun PrivacyConsentScreen(
    onAccept: () -> Unit
) {
    val context = LocalContext.current
    var showDeclineConfirm by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(NavyDark)) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text(
                "Antes de empezar",
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
                    "¿Quién es responsable de tus datos?",
                    RESPONSABLE_PENDIENTE,
                    highlight = true
                )
                ConsentSection(
                    "¿Qué datos guardamos?",
                    "Tu nombre y teléfono, los nombres y teléfonos de las personas que agregues a tu círculo, y si confirmaste que estás bien cada día. Tu ubicación y la información de tus medicamentos son datos sensibles: no estás obligado(a) a autorizarlos, pero sin ellos no podemos ofrecerte las alertas de zonas seguras ni los recordatorios de medicinas."
                )
                ConsentSection(
                    "¿Para qué los usamos?",
                    "Únicamente para que la app funcione: avisar a tu círculo si necesitas ayuda o si no confirmaste tu bienestar. Nunca los usamos para publicidad ni los vendemos a nadie."
                )
                ConsentSection(
                    "¿Con quién los compartimos?",
                    "Solo con las personas que tú agregas a tu círculo de confianza. Nadie más tiene acceso. Usamos Firebase (Google) para guardar tu información de forma segura, lo que significa que puede procesarse en servidores fuera de Colombia, bajo los estándares de protección de Google."
                )
                ConsentSection(
                    "¿Cuánto tiempo guardamos tus datos?",
                    "Mientras tu círculo esté activo. Si sales del círculo o nos pides eliminar tu cuenta, borramos tus datos en un máximo de 30 días, salvo que la ley nos obligue a conservarlos más tiempo."
                )
                ConsentSection(
                    "Tus derechos",
                    "Puedes conocer, actualizar, corregir o pedir la eliminación de tus datos, y puedes retirar este consentimiento cuando quieras — al hacerlo, dejaremos de poder darte el servicio de alertas. Respondemos tus consultas en máximo 10 días hábiles y tus reclamos en máximo 15 días hábiles. Si no quedas conforme, puedes presentar una queja ante la Superintendencia de Industria y Comercio (SIC)."
                )
                ConsentSection(
                    "Contacto",
                    "Para ejercer estos derechos escríbenos a contacto@gutigu.com."
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
            ) {
                Text("Acepto y continúo", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { showDeclineConfirm = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("No acepto", color = AccentRed, fontSize = 16.sp)
            }
        }
    }

    if (showDeclineConfirm) {
        AlertDialog(
            onDismissRequest = { showDeclineConfirm = false },
            title = { Text("¿Seguro que no aceptas?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Sin aceptar no podemos avisar a tu círculo si necesitas ayuda. " +
                        "La app se va a cerrar — puedes volver a abrirla cuando quieras para decidir de nuevo."
                )
            },
            confirmButton = {
                TextButton(onClick = { (context as? Activity)?.finish() }) {
                    Text("Cerrar la app", color = AccentRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeclineConfirm = false }) {
                    Text("Seguir viendo")
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
