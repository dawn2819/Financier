package com.financier.app.common

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

    private const val PREF_LANGUAGE = "app_language"

    fun setLocale(context: Context, language: String): Context {
        saveLanguage(context, language)
        return updateResources(context, language)
    }

    fun getLanguage(context: Context): String {
        return context.getSharedPreferences("locale_pref", Context.MODE_PRIVATE)
            .getString(PREF_LANGUAGE, "vi") ?: "vi"
    }

    private fun saveLanguage(context: Context, language: String) {
        context.getSharedPreferences("locale_pref", Context.MODE_PRIVATE)
            .edit().putString(PREF_LANGUAGE, language).apply()
    }

    private fun updateResources(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return context.createConfigurationContext(config)
    }

    fun applyCurrentLocale(context: Context): Context {
        val language = getLanguage(context)
        return updateResources(context, language)
    }
}
