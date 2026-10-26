package com.gutigu.alicia.ui.theme

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Cambia el idioma de TODA la app (no solo el de la llamada con Alicia, que es un
 * campo de datos aparte en CallProfile). Usa el mecanismo estándar de AndroidX —
 * persiste solo, y en Android 13+ además queda visible en Ajustes del sistema
 * ("Idiomas de la app").
 */
object AppLanguage {
    const val SPANISH = "es"
    const val ENGLISH = "en"

    fun set(languageTag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
    }

    /**
     * Idioma efectivo actual. Si no se ha elegido nada explícitamente todavía,
     * sigue el idioma del sistema en vez de asumir español.
     */
    fun current(): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val tag = if (!appLocales.isEmpty) appLocales.toLanguageTags() else Locale.getDefault().toLanguageTag()
        return if (tag.startsWith(ENGLISH)) ENGLISH else SPANISH
    }
}
