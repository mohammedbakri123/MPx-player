package com.mpx.player.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.ui.theme.MpxTheme

/**
 * Issue #431 (houses #430, #432, #433): the Advanced settings screen,
 * mirroring mpvEx's Advanced screen — Backup & Restore, MPV
 * Configuration, Scripts — in MPx's own Compose style, strings and
 * navigation. Reached from Settings > Advanced > Advanced settings.
 *
 * The mpv.conf / input.conf editors are hosted here as internal
 * state (same drill-down pattern SettingsScreen uses): opening one
 * replaces the list, back closes the editor first. The editors write
 * filesDir/mpv (what libmpv reads) plus the pref cache, so the rows
 * below show the cached text's first line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(
    onNavigateBack: () -> Unit,
    settingsRepository: AppSettingsRepository,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(settingsRepository.load()) }

    // Issue #270 (same pattern as SettingsScreen): reload on resume —
    // the editor writes the pref directly, so coming back must refresh
    // the row subtitles.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                settings = settingsRepository.load()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var editing by rememberSaveable { mutableStateOf<MpvConfigType?>(null) }
    BackHandler(enabled = editing != null) { editing = null }
    // The editor saves through the repo; reload when it closes so the
    // row subtitles show the saved text.
    LaunchedEffect(editing) {
        if (editing == null) settings = settingsRepository.load()
    }

    val editingType = editing
    if (editingType != null) {
        ConfigEditorScreen(
            configType = editingType,
            settingsRepository = settingsRepository,
            storageUri = settings.mpvConfStorageUri,
            onNavigateBack = { editing = null },
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MpxTheme.colors.black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.pref_advanced_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                        color = MpxTheme.colors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.action_back),
                            tint = MpxTheme.colors.textPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MpxTheme.colors.black),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item { PreferenceSectionHeader(text = stringResource(R.string.adv_section_mpv_config)) }
            item {
                PreferenceCard {
                    PreferenceRow(
                        title = stringResource(R.string.adv_edit_mpv_conf),
                        subtitle = settings.mpvConf.lines().firstOrNull { it.isNotBlank() }?.trim()
                            ?: stringResource(R.string.adv_edit_tap_hint),
                        icon = Icons.Filled.Description,
                        onClick = { editing = MpvConfigType.MPV_CONF },
                    )
                    PreferenceDivider()
                    PreferenceRow(
                        title = stringResource(R.string.adv_edit_input_conf),
                        subtitle = settings.inputConf.lines().firstOrNull { it.isNotBlank() }?.trim()
                            ?: stringResource(R.string.adv_edit_tap_hint),
                        icon = Icons.Filled.Description,
                        onClick = { editing = MpvConfigType.INPUT_CONF },
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
            item {
                Text(
                    text = stringResource(R.string.adv_editor_applies_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MpxTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        }
    }
}
