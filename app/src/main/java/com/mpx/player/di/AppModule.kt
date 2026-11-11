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
 * Migration Phase 1a: the app-level Koin module — the one place the
 * database, repositories, and settings repository are constructed.
 *
 * Every declaration is a `single` bound to its domain interface, so
 * callers depend on the interface and Koin hands out the same
 * instance everywhere. The factories delegate to the existing
 * `getInstance` singletons where they exist ([MpxDatabase]), so no
 * second copy of the database is created.
 *
 * Not yet in this module: [com.mpx.player.playback.PlaybackCoordinator]
 * (see [playbackModule]) and the mpv engine — the coordinator builds
 * its engine internally, so the engine gets no Koin registration
 * until Phase 4 splits it apart.
 */
val appModule = module {
    single { MpxDatabase.getInstance(androidContext()) }

    single<MediaRepository> { MediaStoreRepository(androidContext(), get()) }

    single<MediaPersistenceRepository> {
        RoomMediaPersistenceRepository(get(), androidContext())
    }

    single<AppSettingsRepository> { SharedPrefsAppSettingsRepository(androidContext()) }
}
