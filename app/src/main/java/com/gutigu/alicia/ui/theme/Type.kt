@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.gutigu.alicia.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.gutigu.alicia.R

// Fuentes de marca — Lora (display, títulos) y Plus Jakarta Sans (cuerpo).
// Archivos reales bundleados en res/font/ (variable fonts, licencia SIL Open Font
// License — ver app/src/main/assets/font_licenses/). Cada peso se saca de la misma
// fuente variable vía FontVariation.Settings, tal como recomienda la guía oficial
// de Android para variable fonts.
private val LoraFamily = FontFamily(
    Font(R.font.lora, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.lora, weight = FontWeight.Bold,   variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)

private val PlusJakartaSansFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, weight = FontWeight.Normal,  variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.plus_jakarta_sans, weight = FontWeight.Medium,  variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.plus_jakarta_sans, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.plus_jakarta_sans, weight = FontWeight.Bold,    variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)

// Tamaño mínimo de UI del spec: 16sp. displayLarge/displayMedium/headlineLarge son los
// títulos grandes (Lora); el resto es cuerpo (Plus Jakarta Sans).
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = LoraFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    displayMedium = TextStyle(
        fontFamily = LoraFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 34.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = LoraFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp
    )
)
