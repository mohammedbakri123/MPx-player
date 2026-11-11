package com.mpx.player.fakes

import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository

/**
 * Shared test fake for [AppSettingsRepository].
 *
 * Supports both [settings] and [stored] accessors used across the test suite,
 * and records [lastCommit] for persistence verification.
 */
class FakeSettingsRepository(
    var settings: AppSettings = AppSettings(),
) : AppSettingsRepository {

    var stored: AppSettings
        get() = settings
        set(value) {
            settings = value
        }

    var lastCommit: Boolean = false
        private set

    override fun load(): AppSettings = settings

    override fun save(settings: AppSettings, commit: Boolean) {
        this.settings = settings
        this.lastCommit = commit
    }
}
