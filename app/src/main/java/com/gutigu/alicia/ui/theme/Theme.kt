package com.gutigu.alicia.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Spec v2: fondo claro fijo para el núcleo — nunca oscuro, sin importar el tema del
// sistema ni el wallpaper del usuario. Ver SPEC_NUCLEO_v2.md §0 y §1.1.
private val AliciaLightScheme = lightColorScheme(
    primary        = AliciaAccent,
    onPrimary      = AliciaBackground,
    secondary      = AliciaCircle,
    background     = AliciaBackground,
    onBackground   = AliciaText,
    surface        = AliciaSurface,
    onSurface      = AliciaText,
    surfaceVariant = AliciaSurfaceAlt,
    error          = AliciaAlert,
    onError        = Color.White
)

@Composable
fun AliciaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AliciaLightScheme,
        typography = Typography,
        content = content
    )
}
