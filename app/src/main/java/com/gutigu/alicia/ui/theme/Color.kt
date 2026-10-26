package com.gutigu.alicia.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta oficial de Alicia — tomada de callmealicia.com para que la app y la web se vean
// como una sola marca: neutros fríos tipo Apple, tinta casi negra, acento terracota y
// verde solo para "todo bien". Rojo reservado a emergencias y errores.

// Neutros
val AliciaBackground    = Color(0xFFFBFBFD)   // web: --bg-2
val AliciaSurface       = Color(0xFFFFFFFF)   // web: --bg
val AliciaSurfaceAlt    = Color(0xFFF5F5F7)   // web: --bg-3
val AliciaLine          = Color(0xFFE8E8ED)   // web: --line-2
val AliciaInactive      = Color(0xFFA1A1A6)
val AliciaText          = Color(0xFF1D1D1F)   // web: --ink
val AliciaTextSecondary = Color(0xFF6E6E73)   // web: --muted (5,0:1 sobre el fondo)

// Acento de marca (terracota) — acciones principales, selección, títulos, enlaces.
// Lleva texto BLANCO encima (AliciaOnAccent): con texto oscuro no llega a contraste AA.
val AliciaAccent        = Color(0xFFB0502D)   // web: --accent
val AliciaOnAccent      = Color(0xFFFFFFFF)
val AliciaAccentSoft    = Color(0xFFFBF1EC)   // tinte del acento
val AliciaAccentMuted   = Color(0xFFF0C9B6)   // web: --accent-soft
val AliciaAccentDark    = Color(0xFF8A3D20)   // terracota oscuro — degradados y estados presionados
val AliciaInk2          = Color(0xFF424245)   // web: --ink-2 (grafito)

// Éxito / "todo bien" (estoy bien, medicamento tomado, círculo creado).
val AliciaSuccess       = Color(0xFF1F7A4D)
val AliciaSuccessSoft   = Color(0xFFEAF5EE)

// Segundo color de marca para distinguir roles/tipos de un vistazo (Miembro, citas médicas).
val AliciaSecondary     = Color(0xFF1D5C37)

// Emergencia y errores — el rojo de la web (#c4281c). El botón SOS tiene sus propios
// tonos en Panicscreen.kt; AliciaAlert es el fondo de la alerta ya activa.
val AliciaAlert         = Color(0xFFC4281C)
val AliciaFormAlert     = Color(0xFFC4281C)
val AliciaAlertSoft     = Color(0xFFFBE9E7)
val AliciaWarning       = Color(0xFFB9862A)   // ámbar de la web
val AliciaWarningStrong = Color(0xFF8F6516)   // ámbar oscuro para texto (AA sobre fondo claro)
val AliciaWarningSoft   = Color(0xFFFBEFE3)   // tinte ámbar

val AliciaCircle        = Color(0xFF86868B)
val AliciaCircleSoft    = Color(0xFFF5F5F7)
