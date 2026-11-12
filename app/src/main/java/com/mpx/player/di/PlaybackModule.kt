package com.mpx.player.di

import com.mpx.player.playback.PlaybackController
import com.mpx.player.playback.PlaybackCoordinator
import com.mpx.player.playback.PlaybackStateSource
import com.mpx.player.playback.StreamPlayer
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Migration Phase 1b, rewired in Phase 3 step 5, updated in Phase 8 Step 1.4:
 * the playback Koin module.
 *
 * Registers [PlaybackCoordinator] as a process-wide `single` built through
 * [PlaybackCoordinator.production], and binds it to [PlaybackStateSource],
 * [PlaybackController], and [StreamPlayer] interfaces (CP-2).
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
    single<PlaybackStateSource> { get<PlaybackCoordinator>() }
    single<PlaybackController> { get<PlaybackCoordinator>() }
    single<StreamPlayer> { get<PlaybackCoordinator>() }
}
