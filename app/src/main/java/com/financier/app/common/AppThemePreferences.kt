package com.financier.app.common

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** Frontend-only appearance preference; kept outside the finance database schema. */
object AppThemePreferences {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"
    private const val PREFERENCES = "appearance_preferences"
    private const val KEY_THEME = "theme_mode"

    fun selectedMode(context: Context): String? =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY_THEME, null)

    fun saveAndApply(context: Context, mode: String) {
        require(mode in setOf(SYSTEM, LIGHT, DARK))
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, mode).apply()
        apply(context)
    }

    fun apply(context: Context, legacyDarkMode: Boolean? = null) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val saved = preferences.getString(KEY_THEME, null)
        val mode = when (saved) {
            LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            DARK -> AppCompatDelegate.MODE_NIGHT_YES
            SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> legacyDarkMode?.let {
                preferences.edit().putString(KEY_THEME, if (it) DARK else LIGHT).apply()
                if (it) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            } ?: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }
}
