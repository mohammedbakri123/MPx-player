package com.mpx.player.ui.screens.player.components

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.ui.screens.player.PlayerViewModel

/**
 * SurfaceView video renderer with mpv attach/detach lifecycle (§6).
 *
 * Structural-equality guard on [lastMpvSubtitleSettings] is PRESERVED:
 * passing a new AppSettings instance per recomposition would re-push
 * to mpv every frame.
 */
@Composable
fun PlayerVideoSurface(
    viewModel: PlayerViewModel,
    currentSettings: AppSettings,
    lastMpvSubtitleSettings: Array<AppSettings?>,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            SurfaceView(ctx).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        viewModel.attachSurface(holder.surface)
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                        viewModel.surfaceChanged(width, height)
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        // SYNCHRONOUS barrier:
                        // Set vo=null and detach before surfaceDestroyed returns.
                        viewModel.detachSurface()
                    }
                })
            }
        },
        update = {
            // Push subtitle style changes to a running mpv engine (issue #75),
            // plus audio delay + video zoom (issue #153).
            if (lastMpvSubtitleSettings[0] != currentSettings) {
                viewModel.applySubtitleSettings(currentSettings)
                viewModel.applyPlaybackTweaks(currentSettings)
                lastMpvSubtitleSettings[0] = currentSettings
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}
