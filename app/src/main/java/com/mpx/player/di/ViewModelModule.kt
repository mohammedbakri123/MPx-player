package com.mpx.player.di

import com.mpx.player.playback.PlaybackCoordinator
import com.mpx.player.ui.screens.local.LocalViewModel
import com.mpx.player.ui.screens.me.MeViewModel
import com.mpx.player.ui.screens.music.MusicViewModel
import com.mpx.player.ui.screens.network.NetworkViewModel
import com.mpx.player.ui.screens.player.PlayerViewModel
import com.mpx.player.ui.screens.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Migration Phase 2: the Koin module for ViewModels — one
 * `viewModel { ... }` line per migrated screen, all in this file.
 *
 * Phase 2a registers [PlayerViewModel]. Its constructor takes
 * interfaces ([com.mpx.player.playback.PlaybackStateSource],
 * [com.mpx.player.domain.settings.AppSettingsRepository]); Koin
 * resolves the first from the [PlaybackCoordinator] single — the coordinator
 * implements the interface, so no second instance is created — and
 * the second from the [appModule] settings single.
 *
 * Phase 2b registers [com.mpx.player.ui.screens.local.LocalViewModel].
 * Its constructor takes interfaces only
 * ([com.mpx.player.domain.repository.MediaRepository],
 * [com.mpx.player.domain.repository.MediaPersistenceRepository],
 * [com.mpx.player.domain.settings.AppSettingsRepository]); Koin
 * resolves all three from [appModule]. The video-permission flag is
 * the one value the ViewModel cannot know on its own (it needs a
 * Context to check): it arrives as a parameter, supplied by the
 * screen as `parametersOf(MediaPermissions.hasVideoPermission(...))`.
 * The screen keeps the flag in sync afterwards via
 * `setHasPermission` on the permission callback and on resume.
 * The named `savedStateHandle = get()` is the
 * [androidx.lifecycle.SavedStateHandle]: Koin creates it from the
 * NavBackStackEntry's CreationExtras, so the search states survive
 * dock-tab switches and process death (see LocalViewModel's Search
 * section) — it is NOT a container binding, so do not move it to
 * [appModule]. It is named (not positional) because the constructor's
 * `ioDispatcher` parameter sits between it and `hasPermission` and
 * keeps its default here.
 *
 * Phase 2c registers [com.mpx.player.ui.screens.music.MusicViewModel].
 * Its constructor takes interfaces only
 * ([com.mpx.player.domain.repository.MediaRepository],
 * [com.mpx.player.domain.repository.MediaPersistenceRepository],
 * [com.mpx.player.domain.settings.AppSettingsRepository]); all three
 * resolve from [appModule] singles.
 *
 * Phase 2d adds [NetworkViewModel] the same way: its constructor
 * takes [com.mpx.player.playback.StreamPlayer], resolved from the
 * [PlaybackCoordinator] single, which implements that interface too.
 *
 * Phase 2e registers [MeViewModel], which serves MeScreen,
 * HistoryScreen, and PlaylistsScreen. Both of its constructor
 * dependencies are interfaces with [appModule] singles
 * ([com.mpx.player.domain.repository.MediaPersistenceRepository],
 * [com.mpx.player.domain.settings.AppSettingsRepository]), so plain
 * `get()` resolves them.
 *
 * Phase 2e (Settings follow-up) registers [SettingsViewModel],
 * which serves SettingsScreen, OverlaySettingsScreen,
 * AdvancedSettingsScreen, and their hosted sub-screens. It takes
 * three interface dependencies
 * ([com.mpx.player.domain.settings.AppSettingsRepository],
 * [com.mpx.player.domain.repository.MediaPersistenceRepository],
 * [com.mpx.player.domain.repository.MediaRepository]), all with
 * [appModule] singles, so plain `get()` resolves them. Each
 * destination obtains its own instance with `koinViewModel()`.
 *
 * Screens obtain their ViewModel with `koinViewModel()`; they never
 * construct one directly.
 */
val viewModelModule = module {
    viewModel { PlayerViewModel(get<PlaybackCoordinator>(), get()) }
    viewModel { (hasPermission: Boolean) ->
        LocalViewModel(get(), get(), get(), hasPermission, savedStateHandle = get())
    }
    viewModel { MusicViewModel(get(), get(), get()) }
    viewModel { NetworkViewModel(get<PlaybackCoordinator>()) }
    viewModel { MeViewModel(get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get()) }
}
