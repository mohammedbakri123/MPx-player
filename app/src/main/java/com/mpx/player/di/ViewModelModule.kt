package com.mpx.player.di

import com.mpx.player.playback.PlaybackManager
import com.mpx.player.ui.screens.network.NetworkViewModel
import com.mpx.player.ui.screens.player.PlayerViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Migration Phase 2: the Koin module for ViewModels — one
 * `viewModel { ... }` line per migrated screen, all in this file.
 *
 * Phase 2a registers [PlayerViewModel] only. Its constructor takes
 * interfaces ([com.mpx.player.playback.PlaybackStateSource],
 * [com.mpx.player.domain.settings.AppSettingsRepository]); Koin
 * resolves the first from the [PlaybackManager] single — the manager
 * implements the interface, so no second instance is created — and
 * the second from the [appModule] settings single.
 *
 * Phase 2d adds [NetworkViewModel] the same way: its constructor
 * takes [com.mpx.player.playback.StreamPlayer], resolved from the
 * [PlaybackManager] single, which implements that interface too.
 *
 * Screens obtain their ViewModel with `koinViewModel()`; they never
 * construct one directly.
 */
val viewModelModule = module {
    viewModel { PlayerViewModel(get<PlaybackManager>(), get()) }
    viewModel { NetworkViewModel(get<PlaybackManager>()) }
}
