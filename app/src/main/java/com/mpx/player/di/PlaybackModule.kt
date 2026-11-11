package com.mpx.player.di

import com.mpx.player.playback.PlaybackCoordinator
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Migration Phase 1b, rewired in Phase 3 step 5: the playback Koin
 * module.
 *
 * Only [PlaybackCoordinator] is registered here, as a `single` built
 * through its [PlaybackCoordinator.production] factory — Koin
 * returns the same process-wide instance the rest of the app
 * already uses, it does not build a second one. [PlaybackService]
 * injects this same single.
 *
 * The mpv engine is deliberately NOT registered. The coordinator
 * builds its engine internally
 * (`val engine: PlayerEngine = MpvPlayerEngine(appContext, initialDecoderMode)`
 * in [PlaybackCoordinator.production]) and wires the engine listener
 * to itself at construction time; a separate engine `single` in Koin would create
 * a second mpv engine instance that nothing listens to, and would
 * change behaviour. Engine registration is deferred to Phase 4, when
 * the engine is split apart and can be handed in through its
 * constructor.
 *
 * Same note for the database / persistence / settings instances:
 * In Phase 3 step 3.1 (D-10), Koin's [MpxDatabase], [MediaPersistenceRepository],
 * and [AppSettingsRepository] singles are injected into [PlaybackCoordinator.production],
 * eliminating duplicate repository instances.
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
}
