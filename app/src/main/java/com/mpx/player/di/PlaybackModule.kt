package com.mpx.player.di

import com.mpx.player.playback.PlaybackManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Migration Phase 1b: the playback Koin module.
 *
 * Only [PlaybackManager] is registered here, as a `single` built
 * through its existing [PlaybackManager.getInstance] factory — Koin
 * returns the same process-wide singleton the rest of the app
 * already uses, it does not build a second one.
 *
 * The mpv engine is deliberately NOT registered. PlaybackManager
 * builds its engine internally
 * (`var engine: PlayerEngine = MpvPlayerEngine(appContext, ...)` in
 * PlaybackManager.kt) and wires the engine listener to itself at
 * construction time; a separate engine `single` in Koin would create
 * a second mpv engine instance that nothing listens to, and would
 * change behaviour. Engine registration is deferred to Phase 3, when
 * PlaybackManager is split apart and the engine can be handed in
 * through its constructor.
 *
 * Same note for the database / persistence / settings instances
 * PlaybackManager builds internally: [MpxDatabase.getInstance] is a
 * true singleton, so Koin's `single` and PlaybackManager's internal
 * reference are the same object; the settings and persistence
 * wrappers it constructs are stateless facades over the same
 * SharedPreferences file / Room database. Unifying those
 * constructors is Phase 3 work.
 */
val playbackModule = module {
    single { PlaybackManager.getInstance(androidContext()) }
}
