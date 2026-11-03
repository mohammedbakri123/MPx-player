package com.mpx.player.ui.screens.settings

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.text.format.Formatter
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.data.local.db.MpxDatabase
import com.mpx.player.data.repository.MediaStoreRepository
import com.mpx.player.domain.model.SettingCategory
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.playback.MpxMediaCodecSelector
import com.mpx.player.playback.PlaybackManager
import com.mpx.player.playback.PlaybackService
import com.mpx.player.util.LocaleHelper
import com.mpx.player.util.MediaPermissions
import com.mpx.player.util.PipUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onOpenOverlaySettings: (() -> Unit)? = null,
    settingsRepository: AppSettingsRepository,
    onOpenAboutBeta: (() -> Unit)? = null,
    onThemeChanged: ((String) -> Unit)? = null,
    currentLanguage: String = "system",
    onLanguageChanged: ((String) -> Unit)? = null,
    playbackManager: PlaybackManager? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val actualPlaybackManager = playbackManager ?: remember(context) { PlaybackManager.getInstance(context) }
    var settings by remember { mutableStateOf(settingsRepository.load()) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun update(next: AppSettings) {
        val oldTheme = settings.appTheme
        val oldLanguage = settings.appLanguage
        val oldEngine = settings.playerEngine
        val oldDecoder = settings.decoderMode
        settings = next
        settingsRepository.save(next)
        if (next.appTheme != oldTheme) {
            onThemeChanged?.invoke(next.appTheme)
        }
        if (next.appLanguage != oldLanguage) {
            onLanguageChanged?.invoke(next.appLanguage)
        }
        if (next.playerEngine != oldEngine) {
            actualPlaybackManager.setPlayerEngine(next.playerEngine)
        }
        if (next.decoderMode != oldDecoder) {
            val decMode = com.mpx.player.playback.DecoderMode.values().find { it.label == next.decoderMode } ?: com.mpx.player.playback.DecoderMode.HW
            actualPlaybackManager.setDecoderMode(decMode)
        }
    }

    fun toggleMusicTab(tabId: String, currentVal: Boolean) {
        if (currentVal && settings.visibleMusicTabsCount <= 1) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.settings_tab_min_one_required)
                )
            }
            return
        }
        val next = settings.withMusicTabToggled(tabId, !currentVal)
        update(next)
    }

    val activeLanguage = currentLanguage.ifBlank { settings.appLanguage }
    val languageSubtitle = when (activeLanguage.lowercase()) {
        LocaleHelper.LANGUAGE_ARABIC -> stringResource(R.string.lang_arabic)
        LocaleHelper.LANGUAGE_ENGLISH -> stringResource(R.string.lang_english)
        LocaleHelper.LANGUAGE_SPANISH -> stringResource(R.string.lang_spanish)
        LocaleHelper.LANGUAGE_FRENCH -> stringResource(R.string.lang_french)
        LocaleHelper.LANGUAGE_HINDI -> stringResource(R.string.lang_hindi)
        LocaleHelper.LANGUAGE_CHINESE -> stringResource(R.string.lang_chinese)
        else -> stringResource(R.string.lang_system_default)
    }

    val categories = listOf(
        SettingCategory("language", stringResource(R.string.settings_category_language), languageSubtitle, Icons.Filled.Language),
        SettingCategory("theme", stringResource(R.string.settings_category_theme), stringResource(R.string.settings_category_theme_sub), Icons.Filled.ColorLens),
        SettingCategory("pip", stringResource(R.string.settings_category_pip), stringResource(R.string.settings_category_pip_sub), Icons.Filled.PictureInPictureAlt),
        SettingCategory("tabs", stringResource(R.string.settings_category_tabs), stringResource(R.string.settings_category_tabs_sub), Icons.Filled.Tab),
        SettingCategory("list", stringResource(R.string.settings_category_list), stringResource(R.string.settings_category_list_sub), Icons.AutoMirrored.Filled.FormatListBulleted),
        SettingCategory("player", stringResource(R.string.settings_category_player), stringResource(R.string.settings_category_player_sub), Icons.Filled.PlayCircleOutline),
        SettingCategory("decoder", stringResource(R.string.settings_category_decoder), stringResource(R.string.settings_category_decoder_sub), Icons.Filled.Memory),
        SettingCategory("audio", stringResource(R.string.settings_category_audio), stringResource(R.string.settings_category_audio_sub), Icons.AutoMirrored.Filled.VolumeUp),
        SettingCategory("subtitle", stringResource(R.string.settings_category_subtitle), stringResource(R.string.settings_category_subtitle_sub), Icons.Filled.Subtitles),
        SettingCategory("general", stringResource(R.string.settings_category_general), stringResource(R.string.settings_category_general_sub), Icons.Filled.Tune),
        SettingCategory("development", stringResource(R.string.settings_category_development), stringResource(R.string.settings_category_development_sub), Icons.Filled.DeveloperMode),
        SettingCategory("about", stringResource(R.string.settings_category_about), stringResource(R.string.settings_category_about_sub), Icons.Filled.Info),
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            items(categories, key = { it.id }) { category ->
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedId = if (expandedId == category.id) null else category.id }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(category.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(category.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp), color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(2.dp))
                            Text(category.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                    }

                    if (expandedId == category.id) {
                        Column(modifier = Modifier.padding(start = 74.dp, end = 16.dp, bottom = 12.dp)) {
                            when (category.id) {
                                "language" -> {
                                    val languageOptions = listOf(
                                        Triple(
                                            LocaleHelper.LANGUAGE_SYSTEM,
                                            stringResource(R.string.lang_system_default),
                                            stringResource(R.string.lang_system_default_desc),
                                        ),
                                        Triple(
                                            LocaleHelper.LANGUAGE_ENGLISH,
                                            stringResource(R.string.lang_english),
                                            stringResource(R.string.lang_english_desc),
                                        ),
                                        Triple(
                                            LocaleHelper.LANGUAGE_ARABIC,
                                            stringResource(R.string.lang_arabic),
                                            stringResource(R.string.lang_arabic_desc),
                                        ),
                                        Triple(
                                            LocaleHelper.LANGUAGE_SPANISH,
                                            stringResource(R.string.lang_spanish),
                                            stringResource(R.string.lang_spanish_desc),
                                        ),
                                        Triple(
                                            LocaleHelper.LANGUAGE_FRENCH,
                                            stringResource(R.string.lang_french),
                                            stringResource(R.string.lang_french_desc),
                                        ),
                                        Triple(
                                            LocaleHelper.LANGUAGE_HINDI,
                                            stringResource(R.string.lang_hindi),
                                            stringResource(R.string.lang_hindi_desc),
                                        ),
                                        Triple(
                                            LocaleHelper.LANGUAGE_CHINESE,
                                            stringResource(R.string.lang_chinese),
                                            stringResource(R.string.lang_chinese_desc),
                                        ),
                                    )
                                    languageOptions.forEach { (langCode, langTitle, langDesc) ->
                                        ChoiceRowWithSubtitle(
                                            title = langTitle,
                                            subtitle = langDesc,
                                            selected = activeLanguage.equals(langCode, ignoreCase = true),
                                        ) {
                                            update(settings.copy(appLanguage = langCode))
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        stringResource(R.string.lang_settings_note),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                "theme" -> {
                                    listOf(
                                        "Midnight" to stringResource(R.string.theme_midnight),
                                        "Dark" to stringResource(R.string.theme_dark),
                                        "Classic" to stringResource(R.string.theme_classic),
                                        "Light" to stringResource(R.string.theme_light),
                                    ).forEach { (themeKey, themeLabel) ->
                                        ChoiceRow(themeLabel, settings.appTheme.equals(themeKey, ignoreCase = true)) {
                                            update(settings.copy(appTheme = themeKey))
                                        }
                                    }
                                }
                                "pip" -> {
                                    if (onOpenOverlaySettings != null) {
                                        TextButton(onClick = onOpenOverlaySettings) {
                                            Text(stringResource(R.string.overlay_settings_title), color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    val isSupported = PipUtils.isPipSupported(context)
                                    if (!isSupported) {
                                        Text(
                                            text = stringResource(R.string.pip_not_supported),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(bottom = 8.dp),
                                        )
                                    }
                                    ToggleRow(stringResource(R.string.pip_enable_toggle), settings.popupPlayEnabled) {
                                        update(settings.copy(popupPlayEnabled = it))
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(stringResource(R.string.pip_window_controls_header), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                                    ChoiceRow(stringResource(R.string.pip_controls_prev_next), settings.popupPlayControls == "PrevNext") {
                                        update(settings.copy(popupPlayControls = "PrevNext"))
                                    }
                                    ChoiceRow(stringResource(R.string.pip_controls_seek10), settings.popupPlayControls == "Seek10") {
                                        update(settings.copy(popupPlayControls = "Seek10"))
                                    }
                                }
                                "tabs" -> {
                                    MusicTabsSettingsSection(settings = settings, onToggleTab = ::toggleMusicTab)
                                }
                                "list" -> {
                                    Text(
                                        stringResource(R.string.pref_default_folder_view_mode),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                                    )
                                    ChoiceRow(stringResource(R.string.view_mode_list), settings.folderViewMode.equals("LIST", ignoreCase = true)) {
                                        update(settings.copy(folderViewMode = "LIST"))
                                    }
                                    ChoiceRow(stringResource(R.string.view_mode_grid), settings.folderViewMode.equals("GRID", ignoreCase = true)) {
                                        update(settings.copy(folderViewMode = "GRID"))
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    ToggleRow(stringResource(R.string.settings_separate_view_title), settings.separateViewLayout) {
                                        update(settings.copy(separateViewLayout = it))
                                    }
                                    Text(
                                        stringResource(R.string.settings_separate_view_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 6.dp),
                                    )
                                    ToggleRow(stringResource(R.string.list_file_view), settings.fileView) { update(settings.copy(fileView = it)) }
                                    Text(
                                        stringResource(R.string.list_file_view_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                                    )
                                    ToggleRow(stringResource(R.string.list_show_all_folders), settings.showAllFolders) { update(settings.copy(showAllFolders = it)) }
                                    ToggleRow(stringResource(R.string.list_show_hidden), settings.showHiddenFiles) { update(settings.copy(showHiddenFiles = it)) }
                                    ToggleRow(stringResource(R.string.list_show_thumbnails), settings.showThumbnails) { update(settings.copy(showThumbnails = it)) }
                                    Spacer(Modifier.height(10.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), thickness = 0.5.dp)
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        text = stringResource(R.string.settings_tabs_header),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(bottom = 4.dp),
                                    )
                                    MusicTabsSettingsSection(settings = settings, onToggleTab = ::toggleMusicTab)
                                }
                                "player" -> {
                                    ToggleRow(stringResource(R.string.player_resume_playback), settings.resumePlayback) { update(settings.copy(resumePlayback = it)) }
                                    ToggleRow(stringResource(R.string.player_background_play), settings.backgroundPlay) { enabled ->
                                        update(settings.copy(backgroundPlay = enabled))
                                        if (!enabled) {
                                            PlaybackService.stop(context)
                                            val pm = PlaybackManager.getInstance(context)
                                            pm.setVideoBackgroundActive(false)
                                            pm.setBackgroundPlaying(false)
                                        }
                                    }
                                    Text(
                                        stringResource(R.string.player_background_play_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                                    )
                                    ToggleRow(stringResource(R.string.player_auto_rotation), settings.autoScreenRotation) {
                                        val nextOrient = if (it) "auto" else "landscape"
                                        update(settings.copy(autoScreenRotation = it, screenOrientation = nextOrient))
                                    }
                                    Text(
                                        stringResource(R.string.player_auto_rotation_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                                    )
                                    if (!settings.autoScreenRotation) {
                                        Text(
                                            stringResource(R.string.player_locked_orientation_header),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                                        )
                                        ChoiceRow(stringResource(R.string.player_orientation_landscape), settings.screenOrientation.equals("landscape", ignoreCase = true)) {
                                            update(settings.copy(screenOrientation = "landscape"))
                                        }
                                        ChoiceRow(stringResource(R.string.player_orientation_portrait), settings.screenOrientation.equals("portrait", ignoreCase = true)) {
                                            update(settings.copy(screenOrientation = "portrait"))
                                        }
                                        Spacer(Modifier.height(4.dp))
                                    }
                                    ToggleRow(stringResource(R.string.player_gestures), settings.gesturesEnabled) { update(settings.copy(gesturesEnabled = it)) }
                                }
                                "decoder" -> {
                                    Text(
                                        "Playback Engine",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 4.dp),
                                    )
                                    listOf(
                                        "MEDIA3" to "Media3 ExoPlayer (Default)",
                                        "MPV" to "libmpv (Experimental Native Engine)",
                                    ).forEach { (engine, label) ->
                                        ChoiceRow(label, settings.playerEngine.equals(engine, ignoreCase = true)) {
                                            update(settings.copy(playerEngine = engine))
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Decoder Mode",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 4.dp),
                                    )
                                    listOf(
                                        "HW" to stringResource(R.string.decoder_hw),
                                        "HW+" to stringResource(R.string.decoder_hw_plus),
                                        "SW" to stringResource(R.string.decoder_sw),
                                    ).forEach { (mode, label) ->
                                        ChoiceRow(label, settings.decoderMode == mode) { update(settings.copy(decoderMode = mode)) }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        stringResource(R.string.decoder_explanation),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                "audio" -> {
                                    ToggleRow(stringResource(R.string.audio_volume_boost), settings.audioBoost) { update(settings.copy(audioBoost = it)) }
                                }
                                "subtitle" -> {
                                    Text(stringResource(R.string.subtitle_size_header), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                                    listOf(
                                        "Small" to stringResource(R.string.subtitle_size_small),
                                        "Normal" to stringResource(R.string.subtitle_size_normal),
                                        "Large" to stringResource(R.string.subtitle_size_large),
                                    ).forEach { (size, label) ->
                                        ChoiceRow(label, settings.subtitleSize == size) { update(settings.copy(subtitleSize = size)) }
                                    }

                                    Spacer(Modifier.height(8.dp))
                                    Text(stringResource(R.string.subtitle_style_header), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                                    listOf(
                                        "White" to stringResource(R.string.subtitle_style_white),
                                        "Yellow" to stringResource(R.string.subtitle_style_yellow),
                                        "Cyan" to stringResource(R.string.subtitle_style_cyan),
                                        "Default" to stringResource(R.string.subtitle_style_default),
                                    ).forEach { (styleKey, styleLabel) ->
                                        ChoiceRow(styleLabel, settings.subtitleStyle == styleKey) { update(settings.copy(subtitleStyle = styleKey)) }
                                    }
                                }
                                "general" -> {
                                    OutlinedButton(onClick = {
                                        val defaultSettings = AppSettings()
                                        update(defaultSettings)
                                    }) {
                                        Text(stringResource(R.string.general_reset_button), color = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        stringResource(R.string.general_language_hint),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                "development" -> {
                                    DevelopmentSettingsSection(
                                        context = context,
                                        playbackManager = actualPlaybackManager,
                                        settings = settings,
                                    )
                                }
                                "about" -> {
                                    Text(
                                        stringResource(R.string.about_app_description),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    if (onOpenAboutBeta != null) {
                                        OutlinedButton(
                                            onClick = onOpenAboutBeta,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                        ) {
                                            Icon(
                                                Icons.Filled.Info,
                                                null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                stringResource(R.string.about_beta_open_page_button),
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 74.dp), color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        )
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ChoiceRowWithSubtitle(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MusicTabsSettingsSection(
    settings: AppSettings,
    onToggleTab: (tabId: String, currentVal: Boolean) -> Unit,
) {
    Text(
        text = stringResource(R.string.settings_tabs_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )

    val lastActiveSub = stringResource(R.string.settings_tab_last_active_sub)
    val isOnlyOneVisible = settings.visibleMusicTabsCount <= 1

    TabToggleRow(
        label = stringResource(R.string.music_tab_tracks),
        checked = settings.showMusicTracksTab,
        isLastVisible = isOnlyOneVisible && settings.showMusicTracksTab,
        subtitle = lastActiveSub,
        onToggle = { onToggleTab("tracks", settings.showMusicTracksTab) },
    )
    TabToggleRow(
        label = stringResource(R.string.music_tab_playlists),
        checked = settings.showMusicPlaylistsTab,
        isLastVisible = isOnlyOneVisible && settings.showMusicPlaylistsTab,
        subtitle = lastActiveSub,
        onToggle = { onToggleTab("playlists", settings.showMusicPlaylistsTab) },
    )
    TabToggleRow(
        label = stringResource(R.string.music_tab_albums),
        checked = settings.showMusicAlbumsTab,
        isLastVisible = isOnlyOneVisible && settings.showMusicAlbumsTab,
        subtitle = lastActiveSub,
        onToggle = { onToggleTab("albums", settings.showMusicAlbumsTab) },
    )
    TabToggleRow(
        label = stringResource(R.string.music_tab_artists),
        checked = settings.showMusicArtistsTab,
        isLastVisible = isOnlyOneVisible && settings.showMusicArtistsTab,
        subtitle = lastActiveSub,
        onToggle = { onToggleTab("artists", settings.showMusicArtistsTab) },
    )
    TabToggleRow(
        label = stringResource(R.string.music_tab_folders),
        checked = settings.showMusicFoldersTab,
        isLastVisible = isOnlyOneVisible && settings.showMusicFoldersTab,
        subtitle = lastActiveSub,
        onToggle = { onToggleTab("folders", settings.showMusicFoldersTab) },
    )
}

@Composable
private fun TabToggleRow(
    label: String,
    checked: Boolean,
    isLastVisible: Boolean = false,
    subtitle: String? = null,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
            if (isLastVisible && subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            ),
        )
    }
}

private data class DevDiagnosticsData(
    val appId: String = "",
    val versionName: String = "",
    val versionCode: String = "",
    val buildType: String = "",
    val targetSdk: String = "",
    val minSdk: String = "",
    val deviceModel: String = "",
    val osVersion: String = "",
    val dbPath: String = "",
    val dbSizeFormatted: String = "",
    val dbWalFormatted: String = "",
    val dbRecordStats: String = "",
    val hasVideoPerm: Boolean = false,
    val hasAudioPerm: Boolean = false,
    val storageVolumes: String = "",
    val videoFoldersCount: Int = 0,
    val videosCount: Int = 0,
    val audioTracksCount: Int = 0,
    val audioFoldersCount: Int = 0,
    val audioAlbumsCount: Int = 0,
    val audioArtistsCount: Int = 0,
    val playlistsCount: Int = 0,
    val lastScannedTime: String = "",
)

@Composable
private fun DevelopmentSettingsSection(
    context: Context,
    playbackManager: PlaybackManager,
    settings: AppSettings,
) {
    val playbackState by playbackManager.playbackState.collectAsState()
    var devDiagnostics by remember { mutableStateOf<DevDiagnosticsData?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refreshStats() {
        if (isRefreshing) return
        isRefreshing = true
        scope.launch {
            val stats = loadDevDiagnostics(context)
            devDiagnostics = stats
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        refreshStats()
    }

    val stats = devDiagnostics

    // Section 1: App & Build Information
    DiagnosticSection(title = stringResource(R.string.dev_section_app_build)) {
        DiagnosticRow(
            label = stringResource(R.string.dev_label_package),
            value = stats?.appId ?: context.packageName,
            isMonospace = true,
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_version),
            value = stats?.versionName ?: "0.1.0-beta",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_build_code),
            value = stats?.versionCode ?: "2",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_build_type),
            value = stats?.buildType ?: "Debug",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_target_sdk),
            value = stats?.targetSdk ?: "34",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_min_sdk),
            value = stats?.minSdk ?: "24",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_device),
            value = stats?.deviceModel ?: "${Build.MANUFACTURER} ${Build.MODEL}",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_os_version),
            value = stats?.osVersion ?: "Android ${Build.VERSION.RELEASE}",
        )
    }

    Spacer(Modifier.height(8.dp))

    // Section 2: Room SQLite Database
    DiagnosticSection(title = stringResource(R.string.dev_section_database)) {
        DiagnosticRow(
            label = stringResource(R.string.dev_label_db_name),
            value = "mpx_player.db (Room v1)",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_db_path),
            value = stats?.dbPath ?: context.getDatabasePath("mpx_player.db").absolutePath,
            isMonospace = true,
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_db_size),
            value = stats?.dbSizeFormatted ?: stringResource(R.string.dev_val_refreshing),
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_db_wal_size),
            value = stats?.dbWalFormatted ?: stringResource(R.string.dev_val_refreshing),
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_db_records),
            value = stats?.dbRecordStats ?: stringResource(R.string.dev_val_refreshing),
        )
    }

    Spacer(Modifier.height(8.dp))

    // Section 3: MediaStore Library Scan
    DiagnosticSection(title = stringResource(R.string.dev_section_media_scan)) {
        val permVideo = if (stats?.hasVideoPerm == true) {
            stringResource(R.string.dev_val_perm_granted)
        } else {
            stringResource(R.string.dev_val_perm_denied)
        }
        val permAudio = if (stats?.hasAudioPerm == true) {
            stringResource(R.string.dev_val_perm_granted)
        } else {
            stringResource(R.string.dev_val_perm_denied)
        }
        DiagnosticRow(
            label = stringResource(R.string.dev_label_permissions),
            value = stringResource(R.string.dev_val_perm_video, permVideo, permAudio),
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_scan_volumes),
            value = stats?.storageVolumes ?: "primary",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_video_folders),
            value = "${stats?.videoFoldersCount ?: 0} folders",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_scanned_videos),
            value = "${stats?.videosCount ?: 0} videos",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_audio_tracks),
            value = "${stats?.audioTracksCount ?: 0} tracks",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_audio_folders),
            value = "${stats?.audioFoldersCount ?: 0} folders",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_audio_albums),
            value = "${stats?.audioAlbumsCount ?: 0} albums",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_audio_artists),
            value = "${stats?.audioArtistsCount ?: 0} artists",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_playlists),
            value = "${stats?.playlistsCount ?: 0} playlists",
        )
    }

    Spacer(Modifier.height(8.dp))

    // Section 4: Decoder & Playback Engine
    DiagnosticSection(title = stringResource(R.string.dev_section_playback_engine)) {
        val configuredDecoderDesc = when (settings.decoderMode) {
            "HW" -> "Hardware (MediaCodec)"
            "HW+" -> "Hardware Plus (Async Queue)"
            "SW" -> "Software (CPU Codecs)"
            else -> settings.decoderMode
        }
        val playerStateText = when (playbackState.playbackState) {
            androidx.media3.common.Player.STATE_READY -> if (playbackState.isPlaying) "Playing" else "Paused"
            androidx.media3.common.Player.STATE_BUFFERING -> "Buffering"
            androidx.media3.common.Player.STATE_ENDED -> "Ended"
            androidx.media3.common.Player.STATE_IDLE -> "Idle"
            else -> "Idle"
        }
        val activeMediaText = playbackState.currentItem?.let {
            "${it.title} (${if (it.isVideo) "Video" else "Audio"})"
        } ?: stringResource(R.string.dev_val_none_idle)

        val activeCodec = playbackState.activeDecoderName ?: stringResource(R.string.dev_val_none_idle)
        val isSwCodec = playbackState.activeDecoderName?.let { MpxMediaCodecSelector.isSoftwareCodecName(it) }
        val codecType = when {
            playbackState.activeDecoderName == null -> "Configured for ${settings.decoderMode}"
            isSwCodec == true -> stringResource(R.string.dev_val_codec_sw)
            else -> stringResource(R.string.dev_val_codec_hw)
        }
        val fallbackNote = playbackState.decoderFallbackNote ?: stringResource(R.string.dev_val_direct_codec)

        DiagnosticRow(
            label = stringResource(R.string.dev_label_engine_name),
            value = if (playbackManager.isMpv) "libmpv (mpv-android-lib 0.1.12)" else "AndroidX Media3 ExoPlayer 1.3.1",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_engine_type),
            value = if (playbackManager.isMpv) "C/C++ Native libmpv Core" else "Native Kotlin/Java Core (Standalone)",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_decoder_mode),
            value = "${settings.decoderMode} ($configuredDecoderDesc)",
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_player_state),
            value = playerStateText,
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_active_media),
            value = activeMediaText,
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_active_decoder),
            value = activeCodec,
            isMonospace = playbackState.activeDecoderName != null,
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_codec_type),
            value = codecType,
        )
        DiagnosticRow(
            label = stringResource(R.string.dev_label_decoder_fallback),
            value = fallbackNote,
        )
    }

    Spacer(Modifier.height(10.dp))

    // Refresh Diagnostics Button
    OutlinedButton(
        onClick = { refreshStats() },
        enabled = !isRefreshing,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (isRefreshing) stringResource(R.string.dev_val_refreshing) else stringResource(R.string.dev_btn_refresh_diagnostics),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }

    stats?.lastScannedTime?.takeIf { it.isNotBlank() }?.let { time ->
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.dev_label_last_updated, time),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun DiagnosticSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    isMonospace: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = if (isMonospace) {
                MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            } else {
                MaterialTheme.typography.bodySmall
            },
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.58f),
        )
    }
}

private suspend fun loadDevDiagnostics(context: Context): DevDiagnosticsData = withContext(Dispatchers.IO) {
    val pInfo = try {
        context.packageManager.getPackageInfo(context.packageName, 0)
    } catch (_: Exception) {
        null
    }
    val versionName = pInfo?.versionName ?: "0.1.0-beta"
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pInfo?.longVersionCode?.toString() ?: "2"
    } else {
        @Suppress("DEPRECATION")
        pInfo?.versionCode?.toString() ?: "2"
    }
    val isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val buildType = if (isDebug) "Debug" else "Release"
    val targetSdk = context.applicationInfo.targetSdkVersion.toString()
    val minSdk = "24 (Android 7.0 Nougat)"
    val device = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})"
    val osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    val dbFile = context.getDatabasePath("mpx_player.db")
    val walFile = File(dbFile.path + "-wal")
    val shmFile = File(dbFile.path + "-shm")
    val dbExists = dbFile.exists()
    val dbSizeBytes = if (dbExists) dbFile.length() else 0L
    val walSizeBytes = if (walFile.exists()) walFile.length() else 0L
    val shmSizeBytes = if (shmFile.exists()) shmFile.length() else 0L
    val totalDbBytes = dbSizeBytes + walSizeBytes + shmSizeBytes
    val formattedSize = if (dbExists) {
        val formatted = Formatter.formatFileSize(context, totalDbBytes)
        "$formatted ($totalDbBytes bytes)"
    } else {
        context.getString(R.string.dev_label_db_not_created)
    }
    val walFormatted = if (walFile.exists()) {
        val formatted = Formatter.formatFileSize(context, walSizeBytes)
        "$formatted ($walSizeBytes bytes)"
    } else {
        "0 B (no active WAL log)"
    }

    val recordStats = try {
        val db = MpxDatabase.getInstance(context)
        val favCount = db.favouriteDao().getCount()
        val histCount = db.playbackHistoryDao().getCount()
        val playCount = db.playlistDao().getPlaylistsWithCount().size
        val resCount = db.playbackResumeDao().getAllResumePositions().size
        "$favCount favs • $histCount history • $playCount playlists • $resCount resume"
    } catch (_: Exception) {
        "0 favs • 0 history • 0 playlists • 0 resume"
    }

    val hasVideoPerm = MediaPermissions.hasVideoPermission(context)
    val hasAudioPerm = MediaPermissions.hasAudioPermission(context)
    val mediaRepo = MediaStoreRepository(context)

    val volumes = try {
        val volNames = mediaRepo.getExternalVolumeNames()
        if (volNames.isNotEmpty()) volNames.joinToString(", ") else "primary (internal)"
    } catch (_: Exception) {
        "primary (internal)"
    }

    var videoFolders = 0
    var videoCount = 0
    var audioTracks = 0
    var audioFolders = 0
    var audioAlbums = 0
    var audioArtists = 0
    var playlists = 0

    if (hasVideoPerm) {
        try {
            val folders = mediaRepo.getMediaFolders(showAllFolders = true)
            videoFolders = folders.size
            videoCount = folders.sumOf { it.videoCount }
        } catch (_: Exception) {}
    }

    if (hasAudioPerm) {
        try {
            audioTracks = mediaRepo.getAudioTracks().size
            audioFolders = mediaRepo.getAudioFolders().size
            audioAlbums = mediaRepo.getAudioAlbums().size
            audioArtists = mediaRepo.getAudioArtists().size
            playlists = mediaRepo.getPlaylists().size
        } catch (_: Exception) {}
    }

    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    DevDiagnosticsData(
        appId = context.packageName,
        versionName = versionName,
        versionCode = versionCode,
        buildType = buildType,
        targetSdk = targetSdk,
        minSdk = minSdk,
        deviceModel = device,
        osVersion = osVersion,
        dbPath = dbFile.absolutePath,
        dbSizeFormatted = formattedSize,
        dbWalFormatted = walFormatted,
        dbRecordStats = recordStats,
        hasVideoPerm = hasVideoPerm,
        hasAudioPerm = hasAudioPerm,
        storageVolumes = volumes,
        videoFoldersCount = videoFolders,
        videosCount = videoCount,
        audioTracksCount = audioTracks,
        audioFoldersCount = audioFolders,
        audioAlbumsCount = audioAlbums,
        audioArtistsCount = audioArtists,
        playlistsCount = playlists,
        lastScannedTime = timeFormat.format(Date()),
    )
}
