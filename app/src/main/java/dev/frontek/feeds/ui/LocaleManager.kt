package dev.frontek.feeds.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Thin wrapper over AppCompat per-app locales. The app default is English
 * (the `values/` resources); Italian ships as `values-it/` and is picked
 * automatically when the system language is Italian. This lets the user
 * override that choice from Settings; AppCompat persists it across restarts.
 */
object LocaleManager {

    const val SYSTEM = "system"
    const val ITALIAN = "it"
    const val ENGLISH = "en"
    const val SPANISH = "es"
    const val FRENCH = "fr"

    /** Currently selected language tag, or [SYSTEM] when following the system. */
    fun current(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return SYSTEM
        return locales[0]?.language ?: SYSTEM
    }

    fun set(tag: String) {
        val list = if (tag == SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(list)
    }
}
