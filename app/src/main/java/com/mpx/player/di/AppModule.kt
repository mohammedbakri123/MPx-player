package com.mpx.player.di

import com.mpx.player.data.local.db.MpxDatabase
import com.mpx.player.data.repository.MediaStoreRepository
import com.mpx.player.data.repository.RoomMediaPersistenceRepository
import com.mpx.player.data.settings.SharedPrefsAppSettingsRepository
import com.mpx.player.domain.repository.MediaPersistenceRepository
import com.mpx.player.domain.repository.MediaRepository
import com.mpx.player.domain.settings.AppSettingsRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Application-level dependency injection module for Koin.
 *
 * Constructs and registers the primary application singletons:
 * - [MpxDatabase]: Room database instance (version 13).
 * - [MediaRepository]: backed by [MediaStoreRepository].
 * - [MediaPersistenceRepository]: backed by [RoomMediaPersistenceRepository].
 * - [AppSettingsRepository]: backed by [SharedPrefsAppSettingsRepository].
 *
 * Every repository declaration is registered as a `single` bound to its domain
 * interface, allowing consumers to depend purely on domain abstractions.
 */
val appModule = module {
    single { MpxDatabase.getInstance(androidContext()) }

    single<MediaRepository> { MediaStoreRepository(androidContext(), get()) }

    single<MediaPersistenceRepository> {
        RoomMediaPersistenceRepository(get(), androidContext())
    }

    single<AppSettingsRepository> { SharedPrefsAppSettingsRepository(androidContext()) }
}
