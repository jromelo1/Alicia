package com.gutigu.alicia.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Spec v2: fondo claro fijo para el núcleo — nunca oscuro, sin importar el tema del
// sistema ni el wallpaper del usuario. Ver SPEC_NUCLEO_v2.md §0 y §1.1.
private val AliciaLightScheme = lightColorScheme(
    primary        = AliciaAccent,
    onPrimary      = AliciaOnAccent,
    secondary      = AliciaCircle,
    background     = AliciaBackground,
    onBackground   = AliciaText,
    surface        = AliciaSurface,
    onSurface      = AliciaText,
    surfaceVariant = AliciaSurfaceAlt,
    error          = AliciaAlert,
    onError        = Color.White
)

// Esquinas redondeadas consistentes en toda la app (12–16dp), inspiradas en las
// Apple HIG — reemplaza el mosaico anterior de radios sueltos (10dp, 14dp, 20dp...)
// puestos a mano en cada pantalla. Componentes que no fijan su propio `shape`
// (Card, Button, AlertDialog, OutlinedTextField, etc.) heredan esto automáticamente.
private val AliciaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(16.dp),
    large      = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AliciaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AliciaLightScheme,
        typography = Typography,
        shapes = AliciaShapes,
        content = content
    )
}
