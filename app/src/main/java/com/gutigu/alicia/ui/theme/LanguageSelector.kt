package com.gutigu.alicia.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gutigu.alicia.R

/**
 * Fila de chips Español/English reutilizada en la pantalla de Bienvenida (para elegir
 * idioma desde el primer momento, antes de elegir perfil) y en Círculo (para poder
 * cambiarlo después sin tener que borrar el perfil). Ver [AppLanguage] para el
 * mecanismo de cambio real.
 */
@Composable
fun LanguageChipsRow(
    modifier: Modifier = Modifier,
    selectedColor: Color = AliciaAccent,
    unselectedColor: Color = AliciaSurfaceAlt,
    selectedTextColor: Color = AliciaBackground,
    unselectedTextColor: Color = AliciaTextSecondary
) {
    var current by remember { mutableStateOf(AppLanguage.current()) }

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LanguageChip(
            label = stringResource(R.string.language_spanish),
            selected = current == AppLanguage.SPANISH,
            selectedColor = selectedColor,
            unselectedColor = unselectedColor,
            selectedTextColor = selectedTextColor,
            unselectedTextColor = unselectedTextColor,
            onClick = {
                if (current != AppLanguage.SPANISH) {
                    current = AppLanguage.SPANISH
                    AppLanguage.set(AppLanguage.SPANISH)
                }
            }
        )
        LanguageChip(
            label = stringResource(R.string.language_english),
            selected = current == AppLanguage.ENGLISH,
            selectedColor = selectedColor,
            unselectedColor = unselectedColor,
            selectedTextColor = selectedTextColor,
            unselectedTextColor = unselectedTextColor,
            onClick = {
                if (current != AppLanguage.ENGLISH) {
                    current = AppLanguage.ENGLISH
                    AppLanguage.set(AppLanguage.ENGLISH)
                }
            }
        )
    }
}

@Composable
private fun LanguageChip(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    unselectedColor: Color,
    selectedTextColor: Color,
    unselectedTextColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) selectedColor else unselectedColor,
            contentColor = if (selected) selectedTextColor else unselectedTextColor
        )
    ) {
        Text(label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
