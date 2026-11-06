package com.mpx.player.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import android.text.TextUtils
import android.view.View
import com.mpx.player.R
import java.util.Locale

/**
 * Utility for handling in-app language selection, immediate locale switching,
 * Arabic RTL layout direction detection, and per-app language integration
 * with Android 13+ (API 33+) LocaleManager.
 */
object LocaleHelper {
    const val LANGUAGE_SYSTEM = "system"
    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_ARABIC = "ar"
    const val LANGUAGE_SPANISH = "es"
    const val LANGUAGE_FRENCH = "fr"
    const val LANGUAGE_HINDI = "hi"
    const val LANGUAGE_CHINESE = "zh"
    const val LANGUAGE_RUSSIAN = "ru"
    const val LANGUAGE_GERMAN = "de"
    const val LANGUAGE_PORTUGUESE = "pt"

    /**
     * One language option: the stored code plus the display-name and
     * description string resources.
     */
    data class LanguageOption(val code: String, val titleRes: Int, val descRes: Int)

    /**
     * The single shared language list (issue #231): the language picker in
     * SettingsScreen and the language dialog on the Me screen offer the
     * same 10 options, in the same order, with the same names. Add a new
     * language here — and in [getLocale] and [languageTitleRes] — and both
     * pickers gain it.
     */
    val SUPPORTED_LANGUAGES: List<LanguageOption> = listOf(
        LanguageOption(LANGUAGE_SYSTEM, R.string.lang_system_default, R.string.lang_system_default_desc),
        LanguageOption(LANGUAGE_ENGLISH, R.string.lang_english, R.string.lang_english_desc),
        LanguageOption(LANGUAGE_ARABIC, R.string.lang_arabic, R.string.lang_arabic_desc),
        LanguageOption(LANGUAGE_SPANISH, R.string.lang_spanish, R.string.lang_spanish_desc),
        LanguageOption(LANGUAGE_FRENCH, R.string.lang_french, R.string.lang_french_desc),
        LanguageOption(LANGUAGE_HINDI, R.string.lang_hindi, R.string.lang_hindi_desc),
        LanguageOption(LANGUAGE_CHINESE, R.string.lang_chinese, R.string.lang_chinese_desc),
        LanguageOption(LANGUAGE_RUSSIAN, R.string.lang_russian, R.string.lang_russian_desc),
        LanguageOption(LANGUAGE_GERMAN, R.string.lang_german, R.string.lang_german_desc),
        LanguageOption(LANGUAGE_PORTUGUESE, R.string.lang_portuguese, R.string.lang_portuguese_desc),
    )

    /**
     * Display-name string for a language code (issue #231) — the same
     * mapping the language subtitle and the "language applied" snackbar use.
     * Unknown codes fall back to "System Default", matching [getLocale].
     */
    fun languageTitleRes(languageCode: String): Int = when (languageCode.lowercase()) {
        LANGUAGE_ARABIC -> R.string.lang_arabic
        LANGUAGE_ENGLISH -> R.string.lang_english
        LANGUAGE_SPANISH -> R.string.lang_spanish
        LANGUAGE_FRENCH -> R.string.lang_french
        LANGUAGE_HINDI -> R.string.lang_hindi
        LANGUAGE_CHINESE -> R.string.lang_chinese
        LANGUAGE_RUSSIAN -> R.string.lang_russian
        LANGUAGE_GERMAN -> R.string.lang_german
        LANGUAGE_PORTUGUESE -> R.string.lang_portuguese
        else -> R.string.lang_system_default
    }

    /**
     * Resolves the target [Locale] for a given language code.
     * If [LANGUAGE_SYSTEM] is passed, resolves to the device system locale.
     */
    fun getLocale(languageCode: String): Locale {
        return when (languageCode.lowercase()) {
            LANGUAGE_ARABIC -> Locale("ar")
            LANGUAGE_SPANISH -> Locale("es")
            LANGUAGE_FRENCH -> Locale("fr")
            LANGUAGE_HINDI -> Locale("hi")
            LANGUAGE_CHINESE -> Locale("zh")
            LANGUAGE_RUSSIAN -> Locale("ru")
            LANGUAGE_GERMAN -> Locale("de")
            LANGUAGE_PORTUGUESE -> Locale("pt")
            LANGUAGE_ENGLISH -> Locale("en")
            else -> {
                val systemLocales = Resources.getSystem().configuration.locales
                if (!systemLocales.isEmpty) {
                    systemLocales[0]
                } else {
                    Locale.getDefault()
                }
            }
        }
    }

    /**
     * Determines whether the given [Locale] requires Right-to-Left (RTL) layout.
     */
    fun isRtl(locale: Locale): Boolean {
        return TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL
    }

    /**
     * Creates a localized [Context] with the given [locale] and layout direction applied.
     */
    fun createLocalizedContext(base: Context, locale: Locale): Context {
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /**
     * Applies the in-app locale to the system environment where supported.
     * On Android 13+ (API 33+), updates the [LocaleManager.applicationLocales].
     */
    fun applyAppLocale(context: Context, languageCode: String) {
        val locale = getLocale(languageCode)
        Locale.setDefault(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                val localeList = if (languageCode.equals(LANGUAGE_SYSTEM, ignoreCase = true) || languageCode.isBlank()) {
                    LocaleList.getEmptyLocaleList()
                } else {
                    LocaleList.forLanguageTags(languageCode)
                }
                localeManager?.applicationLocales = localeList
            } catch (_: Exception) {
                // Defensive: do not crash if platform service is unavailable
            }
        }
    }

    /**
     * Reads the persisted language preference directly from SharedPreferences.
     */
    fun getPersistedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences("mpx_settings", Context.MODE_PRIVATE)
        return prefs.getString("app_language", LANGUAGE_SYSTEM) ?: LANGUAGE_SYSTEM
    }

    /**
     * Persists the language preference directly to SharedPreferences.
     */
    fun persistLanguage(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences("mpx_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("app_language", languageCode).apply()
    }
}
