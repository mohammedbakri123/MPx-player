package com.mpx.player.ui.screens.settings
import com.mpx.player.ui.theme.MpxTheme

import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import org.koin.compose.viewmodel.koinViewModel
import com.mpx.player.playback.PlaybackCoordinator
import com.mpx.player.util.PipUtils
import com.mpx.player.ui.components.MpxSecondaryButton
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxShapes

/**
 * Real, complete settings screen for Picture-in-Picture (PiP) and floating pop-up play.
 * Persists all configuration options via [SettingsViewModel] and [AppSettings]
 * into SharedPreferences, while clearly stating Android OS limits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlaySettingsScreen(
    onNavigateBack: () -> Unit,
    playbackCoordinator: PlaybackCoordinator,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Issue #198: do NOT resolve the Activity from LocalContext (see #179)
    // — it is a detached localized configuration context, so
    // context.findActivity() returns null and PiP entry silently fails.
    // The host view's context IS the real Activity.
    val activity = LocalView.current.context as? Activity
    // Migration Phase 2e: settings load/save live in SettingsViewModel
    // now — the screen holds no repository. PlaybackCoordinator arrives as
    // a parameter from MainActivity (Koin), never via getInstance.
    val viewModel: SettingsViewModel = koinViewModel()
    val settings by viewModel.settings.collectAsState()
    val isSupported = remember(context) { PipUtils.isPipSupported(context) }

    fun update(next: AppSettings) {
        viewModel.updateSettings(next)
        activity?.let { PipUtils.updatePipParams(it, next) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MpxTheme.colors.black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.overlay_settings_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        ),
                        color = MpxTheme.colors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = MpxTheme.colors.textPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MpxTheme.colors.black,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        ) {
            // 1. Device Capability Banner
            item {
                if (isSupported) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MpxShapes.medium)
                            .background(MpxTheme.colors.primary.copy(alpha = 0.12f))
                            .border(MpxHairline, MpxTheme.colors.primary.copy(alpha = 0.35f), MpxShapes.medium)
                            .padding(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MpxTheme.colors.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.pip_banner_supported),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MpxTheme.colors.textPrimary,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = stringResource(
                                        R.string.pip_banner_supported_details,
                                        Build.VERSION.RELEASE,
                                        Build.VERSION.SDK_INT,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MpxTheme.colors.textSecondary,
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MpxShapes.medium)
                            .background(MpxTheme.colors.primary.copy(alpha = 0.12f))
                            .border(MpxHairline, MpxTheme.colors.primary.copy(alpha = 0.35f), MpxShapes.medium)
                            .padding(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MpxTheme.colors.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.pip_banner_unsupported),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MpxTheme.colors.textPrimary,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.pip_banner_unsupported_reason),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MpxTheme.colors.textSecondary,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
            }

            // 2. Test PiP Button
            item {
                MpxSecondaryButton(
                    text = stringResource(R.string.pip_test_button),
                    icon = Icons.Filled.PictureInPictureAlt,
                    onClick = {
                        val isPlaying = playbackCoordinator.playbackState.value.isPlaying
                        val currentItem = playbackCoordinator.playbackState.value.currentItem
                        // Issue #225: pass the real playback state and video
                        // size — the old call defaulted to playing with 0x0
                        // dims (wrong action label, 16:9 window) and armed
                        // auto-enter for whatever session followed.
                        val entered = PipUtils.enterPip(
                            activity,
                            settings,
                            isPlaying,
                            playbackCoordinator.videoWidth,
                            playbackCoordinator.videoHeight,
                            currentItem?.isVideo == true,
                        )
                        if (!entered) {
                            val msg = if (!isSupported) {
                                context.getString(R.string.pip_not_supported)
                            } else {
                                context.getString(R.string.pip_test_fail_playing)
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(20.dp))
            }

            // 3. Section: Trigger & Auto-Activation
            item {
                SectionHeader(stringResource(R.string.overlay_section_trigger))

                // Issue #253: on a PiP-less device this toggle could be
                // enabled even though it can never take effect — the
                // Settings PiP category guards its toggle the same way.
                SettingsToggleRow(
                    title = stringResource(R.string.pip_auto_title),
                    subtitle = stringResource(R.string.pip_auto_desc),
                    checked = settings.popupPlayEnabled,
                    enabled = isSupported,
                    onCheckedChange = { update(settings.copy(popupPlayEnabled = it)) },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MpxTheme.colors.border,
                    thickness = MpxHairline,
                )
            }

            // 4. Section: PiP Window Controls Choice
            item {
                SectionHeader(stringResource(R.string.pip_window_controls_header))
                Text(
                    text = stringResource(R.string.pip_window_controls_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MpxTheme.colors.textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                val controlOptions = listOf(
                    Triple(
                        "PrevNext",
                        stringResource(R.string.pip_controls_prev_next),
                        stringResource(R.string.pip_controls_prev_next_desc),
                    ),
                    Triple(
                        "Seek10",
                        stringResource(R.string.pip_controls_seek10),
                        stringResource(R.string.pip_controls_seek10_desc),
                    ),
                    Triple(
                        "PlayPauseOnly",
                        stringResource(R.string.pip_controls_play_pause_only),
                        stringResource(R.string.pip_controls_play_pause_only_desc),
                    ),
                )

                controlOptions.forEach { (key, title, subtitle) ->
                    SettingsRadioRow(
                        title = title,
                        subtitle = subtitle,
                        selected = settings.popupPlayControls == key,
                        enabled = isSupported && settings.popupPlayEnabled,
                        onClick = { update(settings.copy(popupPlayControls = key)) },
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MpxTheme.colors.border,
                    thickness = MpxHairline,
                )
            }

            // 5. Section: Pop-up Play Size Options
            item {
                SectionHeader(stringResource(R.string.pip_size_header))
                Text(
                    text = stringResource(R.string.pip_size_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MpxTheme.colors.textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                val sizeOptions = listOf(
                    Triple(
                        "Medium",
                        stringResource(R.string.pip_size_medium),
                        stringResource(R.string.pip_size_medium_desc),
                    ),
                    Triple(
                        "Small",
                        stringResource(R.string.pip_size_small),
                        stringResource(R.string.pip_size_small_desc),
                    ),
                    Triple(
                        "Large",
                        stringResource(R.string.pip_size_large),
                        stringResource(R.string.pip_size_large_desc),
                    ),
                    Triple(
                        "Auto",
                        stringResource(R.string.pip_size_auto),
                        stringResource(R.string.pip_size_auto_desc),
                    ),
                )

                sizeOptions.forEach { (key, title, subtitle) ->
                    SettingsRadioRow(
                        title = title,
                        subtitle = subtitle,
                        selected = settings.popupPlaySize == key,
                        enabled = isSupported && settings.popupPlayEnabled,
                        onClick = { update(settings.copy(popupPlaySize = key)) },
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MpxTheme.colors.border,
                    thickness = MpxHairline,
                )
            }

            // 6. Section: Pop-up Play Position Options
            item {
                SectionHeader(stringResource(R.string.pip_position_header))
                Text(
                    text = stringResource(R.string.pip_position_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MpxTheme.colors.textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                val positionOptions = listOf(
                    Triple(
                        "BottomRight",
                        stringResource(R.string.pip_position_bottom_right),
                        stringResource(R.string.pip_position_bottom_right_desc),
                    ),
                    Triple(
                        "BottomLeft",
                        stringResource(R.string.pip_position_bottom_left),
                        stringResource(R.string.pip_position_bottom_left_desc),
                    ),
                    Triple(
                        "TopRight",
                        stringResource(R.string.pip_position_top_right),
                        stringResource(R.string.pip_position_top_right_desc),
                    ),
                    Triple(
                        "TopLeft",
                        stringResource(R.string.pip_position_top_left),
                        stringResource(R.string.pip_position_top_left_desc),
                    ),
                    Triple(
                        "Center",
                        stringResource(R.string.pip_position_center),
                        stringResource(R.string.pip_position_center_desc),
                    ),
                    Triple(
                        "RememberLast",
                        stringResource(R.string.pip_position_remember_last),
                        stringResource(R.string.pip_position_remember_last_desc),
                    ),
                )

                positionOptions.forEach { (key, title, subtitle) ->
                    SettingsRadioRow(
                        title = title,
                        subtitle = subtitle,
                        selected = settings.popupPlayPosition == key,
                        enabled = isSupported && settings.popupPlayEnabled,
                        // Issue #292: picking an explicit position also
                        // remembers it, so a later RememberLast reuses
                        // it. Picking RememberLast itself leaves the
                        // last explicit choice untouched. The rule lives
                        // in the ViewModel; the live PiP params are
                        // refreshed from the returned settings, as
                        // update() does above.
                        onClick = {
                            val next = viewModel.updatePopupPlayPosition(key)
                            activity?.let { PipUtils.updatePipParams(it, next) }
                        },
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MpxTheme.colors.border,
                    thickness = MpxHairline,
                )
            }

            // NOTE: the former section 7 (Window Docking & Dismissal
            // Behavior) was removed in #132: popupPlaySnapToEdge and
            // popupPlayCloseOnDone were presented as toggles but never
            // read by any playback/PiP code (edge snapping is enforced
            // by SystemUI; nothing checked close-on-done). The prefs
            // stay stored untouched, so wiring them later loses nothing.
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MpxTheme.colors.border,
                    thickness = MpxHairline,
                )
            }

            // 8. Section: Honest Notes on Android Platform Limits
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MpxShapes.medium)
                        .background(MpxTheme.colors.surfaceElevated.copy(alpha = 0.6f))
                        .border(1.dp, MpxTheme.colors.border.copy(alpha = 0.4f), MpxShapes.medium)
                        .padding(16.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = MpxTheme.colors.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.pip_limits_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MpxTheme.colors.textPrimary,
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        PlatformLimitItem(
                            number = "1",
                            title = stringResource(R.string.pip_limit_window_manager_title),
                            description = stringResource(R.string.pip_limit_window_manager_desc),
                        )

                        Spacer(Modifier.height(8.dp))

                        PlatformLimitItem(
                            number = "2",
                            title = stringResource(R.string.pip_limit_sandboxing_title),
                            description = stringResource(R.string.pip_limit_sandboxing_desc),
                        )

                        Spacer(Modifier.height(8.dp))

                        PlatformLimitItem(
                            number = "3",
                            title = stringResource(R.string.pip_limit_actions_title),
                            description = stringResource(R.string.pip_limit_actions_desc),
                        )

                        Spacer(Modifier.height(8.dp))

                        PlatformLimitItem(
                            number = "4",
                            title = stringResource(R.string.pip_limit_hardware_title),
                            description = stringResource(R.string.pip_limit_hardware_desc),
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        ),
        color = MpxTheme.colors.primary,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MpxTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MpxTheme.colors.textSecondary,
                lineHeight = 16.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MpxTheme.colors.primary,
                checkedTrackColor = MpxTheme.colors.primary.copy(alpha = 0.4f),
            ),
        )
    }
}

@Composable
private fun SettingsRadioRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = MpxTheme.colors.primary),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MpxTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MpxTheme.colors.textSecondary,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun PlatformLimitItem(
    number: String,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "$number.",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = MpxTheme.colors.primary,
            modifier = Modifier.width(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MpxTheme.colors.textPrimary,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MpxTheme.colors.textSecondary,
                lineHeight = 16.sp,
            )
        }
    }
}

