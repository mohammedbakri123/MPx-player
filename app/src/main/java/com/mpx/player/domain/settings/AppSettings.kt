package com.mpx.player.domain.settings

/**
 * Real, user-facing app settings. Persisted via [AppSettingsRepository].
 * Room-persistence worktree may later swap the implementation; the
 * interface and defaults here are the contract.
 */
data class AppSettings(
    val showHiddenFiles: Boolean = false,
    val showThumbnails: Boolean = true,
    val resumePlayback: Boolean = true,
    val backgroundPlay: Boolean = false,
    val gesturesEnabled: Boolean = true,
    val decoderMode: String = "HW", // HW, HW+, SW
    val audioBoost: Boolean = false,
    val subtitleSize: String = "Normal", // Small, Normal, Large
    val subtitleStyle: String = "White", // White, Yellow, Cyan, Default
    val appTheme: String = "Midnight", // Midnight, Dark, Classic, Light
    val popupPlayEnabled: Boolean = true,
    val popupPlayControls: String = "PrevNext", // PrevNext, Seek10
    val appLanguage: String = "system", // system, en, ar
)

interface AppSettingsRepository {
    fun load(): AppSettings
    fun save(settings: AppSettings)
}
