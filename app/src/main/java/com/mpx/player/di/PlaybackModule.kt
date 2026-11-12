package com.mpx.player.di

import com.mpx.player.playback.PlaybackController
import com.mpx.player.playback.PlaybackCoordinator
import com.mpx.player.playback.PlaybackStateSource
import com.mpx.player.playback.StreamPlayer
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Playback dependency injection module for Koin.
 *
 * Registers [PlaybackCoordinator] as a process-wide `single` constructed via
 * [PlaybackCoordinator.production], injecting Koin singletons for the database,
 * media persistence repository, and settings repository.
 *
 * Registers explicit interface bindings:
 * - [PlaybackStateSource]: exposed to ViewModels (e.g. `PlayerViewModel`) for collecting playback state.
 * - [PlaybackController]: exposed for transport and playback control actions.
 * - [StreamPlayer]: exposed to network ViewModels for stream URL playback.
 *
 * Invariants:
 * - The native mpv engine is deliberately NOT registered in Koin: [PlaybackCoordinator.production]
 *   constructs and owns its engine instance and wires internal listeners at creation time, preserving
 *   its single-owner lifecycle.
 */
val playbackModule = module {
    single {
        PlaybackCoordinator.production(
            context = androidContext(),
            database = get(),
            persistenceRepository = get(),
            settingsRepository = get(),
        )
    }
    single<PlaybackStateSource> { get<PlaybackCoordinator>() }
    single<PlaybackController> { get<PlaybackCoordinator>() }
    single<StreamPlayer> { get<PlaybackCoordinator>() }
}
