package com.mpx.player.ui.screens.settings

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.playback.PlaybackManager
import com.mpx.player.util.PipUtils
import com.mpx.player.ui.components.MpxSecondaryButton
import com.mpx.player.ui.theme.MpxBlack
import com.mpx.player.ui.theme.MpxBorder
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxPrimary
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxSurfaceElevated
import com.mpx.player.ui.theme.MpxTextPrimary
import com.mpx.player.ui.theme.MpxTextSecondary

/**
 * Real, complete settings screen for Picture-in-Picture (PiP) and floating pop-up play.
 * Persists all configuration options via [AppSettingsRepository] and [AppSettings]
 * into SharedPreferences, while clearly stating Android OS limits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlaySettingsScreen(
    onNavigateBack: () -> Unit,
    settingsRepository: AppSettingsRepository,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(settingsRepository.load()) }
    val isSupported = remember(context) { PipUtils.isPipSupported(context) }

    fun update(next: AppSettings) {
        settings = next
        settingsRepository.save(next)
        (context as? Activity)?.let { activity ->
            PipUtils.updatePipParams(activity, next)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MpxBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.overlay_settings_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        ),
                        color = MpxTextPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = MpxTextPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MpxBlack,
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
                            .background(MpxPrimary.copy(alpha = 0.12f))
                            .border(MpxHairline, MpxPrimary.copy(alpha = 0.35f), MpxShapes.medium)
                            .padding(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MpxPrimary,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.pip_banner_supported),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MpxTextPrimary,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = stringResource(
                                        R.string.pip_banner_supported_details,
                                        Build.VERSION.RELEASE,
                                        Build.VERSION.SDK_INT,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MpxTextSecondary,
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MpxShapes.medium)
                            .background(MpxPrimary.copy(alpha = 0.12f))
                            .border(MpxHairline, MpxPrimary.copy(alpha = 0.35f), MpxShapes.medium)
                            .padding(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MpxPrimary,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.pip_banner_unsupported),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MpxTextPrimary,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.pip_banner_unsupported_reason),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MpxTextSecondary,
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
                        val playbackManager = PlaybackManager.getInstance(context)
                        val isPlaying = playbackManager.playbackState.value.isPlaying
                        val entered = PipUtils.enterPip(context, settings, isPlaying)
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

                SettingsToggleRow(
                    title = stringResource(R.string.pip_auto_title),
                    subtitle = stringResource(R.string.pip_auto_desc),
                    checked = settings.popupPlayEnabled,
                    onCheckedChange = { update(settings.copy(popupPlayEnabled = it)) },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MpxBorder,
                    thickness = MpxHairline,
                )
            }

            // 4. Section: PiP Window Controls Choice
            item {
                SectionHeader(stringResource(R.string.pip_window_controls_header))
                Text(
                    text = stringResource(R.string.pip_window_controls_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MpxTextSecondary,
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
                        onClick = { update(settings.copy(popupPlayControls = key)) },
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MpxBorder,
                    thickness = MpxHairline,
                )
            }

            // 5. Section: Pop-up Play Size Options
            item {
                SectionHeader(stringResource(R.string.pip_size_header))
                Text(
                    text = stringResource(R.string.pip_size_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MpxTextSecondary,
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
                        onClick = { update(settings.copy(popupPlaySize = key)) },
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MpxBorder,
                    thickness = MpxHairline,
                )
            }

            // 6. Section: Pop-up Play Position Options
            item {
                SectionHeader(stringResource(R.string.pip_position_header))
                Text(
                    text = stringResource(R.string.pip_position_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MpxTextSecondary,
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
                        onClick = { update(settings.copy(popupPlayPosition = key)) },
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MpxBorder,
                    thickness = MpxHairline,
                )
            }

            // 7. Section: Window Docking & Dismissal Behavior
            item {
                SectionHeader(stringResource(R.string.overlay_section_behavior))

                SettingsToggleRow(
                    title = stringResource(R.string.pip_snap_edge_title),
                    subtitle = stringResource(R.string.pip_snap_edge_desc),
                    checked = settings.popupPlaySnapToEdge,
                    onCheckedChange = { update(settings.copy(popupPlaySnapToEdge = it)) },
                )

                Spacer(Modifier.height(8.dp))

                SettingsToggleRow(
                    title = stringResource(R.string.pip_close_on_done_title),
                    subtitle = stringResource(R.string.pip_close_on_done_desc),
                    checked = settings.popupPlayCloseOnDone,
                    onCheckedChange = { update(settings.copy(popupPlayCloseOnDone = it)) },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MpxBorder,
                    thickness = MpxHairline,
                )
            }

            // 8. Section: Honest Notes on Android Platform Limits
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MpxShapes.medium)
                        .background(MpxSurfaceElevated.copy(alpha = 0.6f))
                        .border(1.dp, MpxBorder.copy(alpha = 0.4f), MpxShapes.medium)
                        .padding(16.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = MpxPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.pip_limits_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MpxTextPrimary,
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
        color = MpxPrimary,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MpxTextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MpxTextSecondary,
                lineHeight = 16.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MpxPrimary,
                checkedTrackColor = MpxPrimary.copy(alpha = 0.4f),
            ),
        )
    }
}

@Composable
private fun SettingsRadioRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = MpxPrimary),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MpxTextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MpxTextSecondary,
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
            color = MpxPrimary,
            modifier = Modifier.width(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MpxTextPrimary,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MpxTextSecondary,
                lineHeight = 16.sp,
            )
        }
    }
}
