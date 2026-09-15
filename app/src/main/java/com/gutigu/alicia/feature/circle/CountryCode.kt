package com.gutigu.alicia.feature.circle

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.gutigu.alicia.ui.theme.AliciaAccent
import com.gutigu.alicia.ui.theme.AliciaSurface
import com.gutigu.alicia.ui.theme.AliciaSurfaceAlt
import com.gutigu.alicia.ui.theme.AliciaText
import com.gutigu.alicia.ui.theme.AliciaTextSecondary

/**
 * Un país con su prefijo telefónico internacional (sin "+").
 * Ej. Colombia -> dialCode = "57", así que un número guardado queda "+573001234567".
 */
data class CountryDialCode(
    val name: String,
    val iso: String,
    val flag: String,
    val dialCode: String
)

object CountryCodes {

    // Colombia se mantiene como valor por defecto para no romper el comportamiento
    // previo de la app (era el único país soportado, ahora es solo la preselección).
    val default = CountryDialCode("Colombia", "CO", "🇨🇴", "57")

    val all: List<CountryDialCode> = listOf(
        default,
        CountryDialCode("México", "MX", "🇲🇽", "52"),
        CountryDialCode("España", "ES", "🇪🇸", "34"),
        CountryDialCode("Estados Unidos", "US", "🇺🇸", "1"),
        CountryDialCode("Canadá", "CA", "🇨🇦", "1"),
        CountryDialCode("Argentina", "AR", "🇦🇷", "54"),
        CountryDialCode("Chile", "CL", "🇨🇱", "56"),
        CountryDialCode("Perú", "PE", "🇵🇪", "51"),
        CountryDialCode("Ecuador", "EC", "🇪🇨", "593"),
        CountryDialCode("Venezuela", "VE", "🇻🇪", "58"),
        CountryDialCode("Panamá", "PA", "🇵🇦", "507"),
        CountryDialCode("Costa Rica", "CR", "🇨🇷", "506"),
        CountryDialCode("República Dominicana", "DO", "🇩🇴", "1"),
        CountryDialCode("Guatemala", "GT", "🇬🇹", "502"),
        CountryDialCode("Honduras", "HN", "🇭🇳", "504"),
        CountryDialCode("El Salvador", "SV", "🇸🇻", "503"),
        CountryDialCode("Nicaragua", "NI", "🇳🇮", "505"),
        CountryDialCode("Bolivia", "BO", "🇧🇴", "591"),
        CountryDialCode("Paraguay", "PY", "🇵🇾", "595"),
        CountryDialCode("Uruguay", "UY", "🇺🇾", "598"),
        CountryDialCode("Brasil", "BR", "🇧🇷", "55"),
        CountryDialCode("Cuba", "CU", "🇨🇺", "53"),
        CountryDialCode("Puerto Rico", "PR", "🇵🇷", "1"),
        CountryDialCode("Australia", "AU", "🇦🇺", "61"),
        CountryDialCode("Reino Unido", "GB", "🇬🇧", "44"),
        CountryDialCode("Francia", "FR", "🇫🇷", "33"),
        CountryDialCode("Alemania", "DE", "🇩🇪", "49"),
        CountryDialCode("Italia", "IT", "🇮🇹", "39"),
        CountryDialCode("Portugal", "PT", "🇵🇹", "351"),
        CountryDialCode("Países Bajos", "NL", "🇳🇱", "31"),
        CountryDialCode("Suiza", "CH", "🇨🇭", "41"),
        CountryDialCode("Suecia", "SE", "🇸🇪", "46"),
        CountryDialCode("Bélgica", "BE", "🇧🇪", "32"),
        CountryDialCode("Japón", "JP", "🇯🇵", "81"),
        CountryDialCode("China", "CN", "🇨🇳", "86"),
        CountryDialCode("India", "IN", "🇮🇳", "91"),
        CountryDialCode("Israel", "IL", "🇮🇱", "972"),
        CountryDialCode("Nueva Zelanda", "NZ", "🇳🇿", "64")
    )

    /**
     * Dado un teléfono guardado (con o sin "+"), intenta separar el prefijo de país
     * del número local. Si no reconoce ningún prefijo (números guardados antes de
     * tener selector de país), asume Colombia y devuelve todos los dígitos como
     * número local — igual que el comportamiento anterior de la app.
     */
    fun splitPhone(raw: String): Pair<CountryDialCode, String> {
        val digits = raw.filter(Char::isDigit)
        if (!raw.trimStart().startsWith("+")) return default to digits
        val match = all
            .filter { digits.startsWith(it.dialCode) }
            .maxByOrNull { it.dialCode.length }
        return if (match != null) {
            match to digits.removePrefix(match.dialCode)
        } else {
            default to digits
        }
    }
}

// ─── Paleta centralizada en ui/theme/Color.kt ───────────────────────────────
private val SurfaceAlt    = AliciaSurfaceAlt
private val AccentGreen   = AliciaAccent
private val TextPrimary   = AliciaText
private val TextSecondary = AliciaTextSecondary
private val CardBg        = AliciaSurface

/**
 * Chip seleccionable con la bandera y el prefijo del país elegido. Al tocarlo abre
 * [CountryPickerDialog]. Se usa junto a un OutlinedTextField para el número local.
 */
@Composable
fun CountryCodeField(
    selected: CountryDialCode,
    onSelect: (CountryDialCode) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceAlt)
            .border(1.dp, TextSecondary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable { showPicker = true }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(selected.flag, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            "+${selected.dialCode}",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Icon(
            Icons.Default.ArrowDropDown,
            contentDescription = "Elegir país",
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }

    if (showPicker) {
        CountryPickerDialog(
            onDismiss = { showPicker = false },
            onSelect = {
                onSelect(it)
                showPicker = false
            }
        )
    }
}

@Composable
private fun CountryPickerDialog(
    onDismiss: () -> Unit,
    onSelect: (CountryDialCode) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        if (query.isBlank()) {
            CountryCodes.all
        } else {
            CountryCodes.all.filter {
                it.name.contains(query, ignoreCase = true) || it.dialCode.contains(query)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = CardBg,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "¿A qué país llamas?",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Buscar país", color = TextSecondary.copy(alpha = 0.6f)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGreen,
                        cursorColor = AccentGreen,
                        focusedContainerColor = SurfaceAlt,
                        unfocusedContainerColor = SurfaceAlt,
                        unfocusedBorderColor = SurfaceAlt
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 17.sp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn {
                    items(filtered, key = { it.iso }) { country ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(country) }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(country.flag, fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                country.name,
                                color = TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "+${country.dialCode}",
                                color = TextSecondary,
                                fontSize = 15.sp
                            )
                        }
                    }
                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                "Ningún país coincide con \"$query\"",
                                color = TextSecondary,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
