package com.mpx.player

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.mpx.player.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Issue #437: the launch (splash) theme resolved its night qualifier
 * against the phone's system theme, not MPx's theme setting. MPx
 * applied its theme (System / Dark / Light) in Compose only, so with
 * the phone in light mode and MPx set to Dark the splash was always
 * white.
 *
 * The stored theme is applied to the process configuration here, in
 * [attachBaseContext] — before any activity is created — so the
 * night-qualified splash background (values-night/colors.xml) and
 * every other night-qualified resource follow the app's choice.
 * AppCompat is deliberately excluded from this app, so this is a
 * plain configuration override instead of AppCompatDelegate.
 *
 * MainActivity applies the same wrap in its own attachBaseContext
 * (alongside the locale wrap), so a recreated activity picks the
 * current setting up too.
 */
class MpxApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(applyAppThemeNightMode(base))
    }

    override fun onCreate() {
        super.onCreate()
        // Migration Phase 1: Koin is the one place objects are built.
        // Modules are added one per migration step; nothing reads from
        // Koin yet except the modules themselves — MainActivity starts
        // in step 1c.
        startKoin {
            androidContext(this@MpxApplication)
            modules(appModule)
        }
    }
}

/**
 * Returns [context] wrapped in a configuration whose night mode
 * matches the stored app theme (`app_theme` in `mpx_settings`):
 * Dark (and legacy Midnight/Classic) forces night, Light forces
 * not-night, System (or anything unknown) returns the context
 * untouched so the device decides. Mirrors the mode resolution in
 * Theme.kt / MainActivity.
 */
fun applyAppThemeNightMode(context: Context): Context {
    val stored = context.getSharedPreferences("mpx_settings", Context.MODE_PRIVATE)
        .getString("app_theme", "System")
    val night = when (stored) {
        "Dark", "Midnight", "Classic" -> Configuration.UI_MODE_NIGHT_YES
        "Light" -> Configuration.UI_MODE_NIGHT_NO
        else -> return context
    }
    val config = Configuration(context.resources.configuration)
    config.uiMode = config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv() or night
    return context.createConfigurationContext(config)
}
