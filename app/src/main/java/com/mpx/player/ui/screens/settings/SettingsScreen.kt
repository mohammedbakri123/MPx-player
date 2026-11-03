package com.mpx.player.ui.screens.settings

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Subtitles
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
import com.mpx.player.domain.model.SettingCategory
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.ui.theme.MpxPrimary
import com.mpx.player.ui.theme.MpxTextMuted
import com.mpx.player.util.LocaleHelper
import com.mpx.player.util.PipUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    settingsRepository: AppSettingsRepository,
    onThemeChanged: ((String) -> Unit)? = null,
    currentLanguage: String = "system",
    onLanguageChanged: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(settingsRepository.load()) }
    var expandedId by remember { mutableStateOf<String?>(null) }

    fun update(next: AppSettings) {
        val oldTheme = settings.appTheme
        val oldLanguage = settings.appLanguage
        settings = next
        settingsRepository.save(next)
        if (next.appTheme != oldTheme) {
            onThemeChanged?.invoke(next.appTheme)
        }
        if (next.appLanguage != oldLanguage) {
            onLanguageChanged?.invoke(next.appLanguage)
        }
    }

    val activeLanguage = currentLanguage.ifBlank { settings.appLanguage }
    val languageSubtitle = when (activeLanguage.lowercase()) {
        LocaleHelper.LANGUAGE_ARABIC -> stringResource(R.string.lang_arabic)
        LocaleHelper.LANGUAGE_ENGLISH -> stringResource(R.string.lang_english)
        else -> stringResource(R.string.lang_system_default)
    }

    val categories = listOf(
        SettingCategory("language", stringResource(R.string.settings_category_language), languageSubtitle, Icons.Filled.Language),
        SettingCategory("theme", stringResource(R.string.settings_category_theme), stringResource(R.string.settings_category_theme_sub), Icons.Filled.ColorLens),
        SettingCategory("pip", stringResource(R.string.settings_category_pip), stringResource(R.string.settings_category_pip_sub), Icons.Filled.PictureInPictureAlt),
        SettingCategory("list", stringResource(R.string.settings_category_list), stringResource(R.string.settings_category_list_sub), Icons.AutoMirrored.Filled.FormatListBulleted),
        SettingCategory("player", stringResource(R.string.settings_category_player), stringResource(R.string.settings_category_player_sub), Icons.Filled.PlayCircleOutline),
        SettingCategory("decoder", stringResource(R.string.settings_category_decoder), stringResource(R.string.settings_category_decoder_sub), Icons.Filled.Memory),
        SettingCategory("audio", stringResource(R.string.settings_category_audio), stringResource(R.string.settings_category_audio_sub), Icons.AutoMirrored.Filled.VolumeUp),
        SettingCategory("subtitle", stringResource(R.string.settings_category_subtitle), stringResource(R.string.settings_category_subtitle_sub), Icons.Filled.Subtitles),
        SettingCategory("general", stringResource(R.string.settings_category_general), stringResource(R.string.settings_category_general_sub), Icons.Filled.Tune),
        SettingCategory("about", stringResource(R.string.settings_category_about), stringResource(R.string.settings_category_about_sub), Icons.Filled.Info),
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
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
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(category.icon, null, tint = MpxPrimary, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(category.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp), color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(2.dp))
                            Text(category.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MpxTextMuted, modifier = Modifier.size(20.dp))
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
                                "list" -> {
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
                                }
                                "player" -> {
                                    ToggleRow(stringResource(R.string.player_resume_playback), settings.resumePlayback) { update(settings.copy(resumePlayback = it)) }
                                    ToggleRow(stringResource(R.string.player_background_play), settings.backgroundPlay) { update(settings.copy(backgroundPlay = it)) }
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
                                        Text(stringResource(R.string.general_reset_button), color = MpxPrimary)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        stringResource(R.string.general_language_hint),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                "about" -> {
                                    Text(
                                        stringResource(R.string.about_app_description),
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
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
            colors = SwitchDefaults.colors(checkedThumbColor = MpxPrimary, checkedTrackColor = MpxPrimary.copy(alpha = 0.4f)),
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
            colors = RadioButtonDefaults.colors(selectedColor = MpxPrimary),
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
            colors = RadioButtonDefaults.colors(selectedColor = MpxPrimary),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}
