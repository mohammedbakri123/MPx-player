package com.mpx.player.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.ui.components.MpxListRow
import com.mpx.player.ui.theme.MpxSpaceL
import com.mpx.player.ui.theme.MpxTheme

/**
 * One searchable settings entry: a category or an individual setting.
 * Titles/subtitles are resolved in the current locale for matching; the
 * English [keywords] cover terms that differ from the visible label
 * (e.g. "dark" finds the Appearance row).
 */
data class SettingsSearchEntry(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int?,
    val keywords: String,
    val targetCategory: String,
)

/** Static search index over MPx's real settings (titles + summaries). */
fun buildSettingsSearchIndex(): List<SettingsSearchEntry> = listOf(
    SettingsSearchEntry(R.string.pref_appearance_title, R.string.pref_appearance_summary, "theme,dark,light,system,accent,color,palette,amoled", "appearance"),
    SettingsSearchEntry(R.string.theme_option_dark, null, "dark,theme,night,black", "appearance"),
    SettingsSearchEntry(R.string.theme_option_light, null, "light,theme,white", "appearance"),
    SettingsSearchEntry(R.string.palette_section_title, null, "accent,color,palette,blue,emerald,violet,amber,rose,teal", "appearance"),
    SettingsSearchEntry(R.string.pref_layout_title, R.string.pref_layout_summary, "layout,buttons,menu,customize", "layout"),
    SettingsSearchEntry(R.string.settings_category_language, R.string.settings_category_language_sub, "language,locale,arabic,english,spanish", "language"),
    SettingsSearchEntry(R.string.settings_category_tabs, R.string.settings_category_tabs_sub, "tabs,music,tracks,albums,artists,playlists,folders", "tabs"),
    SettingsSearchEntry(R.string.settings_category_player, R.string.pref_player_summary, "player,resume,background,speed,rotation", "player"),
    SettingsSearchEntry(R.string.player_resume_playback, null, "resume,continue,playback", "player"),
    SettingsSearchEntry(R.string.player_background_play, null, "background,audio", "player"),
    SettingsSearchEntry(R.string.player_default_speed_header, null, "speed,playback", "player"),
    SettingsSearchEntry(R.string.pref_gestures_title, R.string.pref_gestures_summary, "gestures,double,tap,swipe,brightness,volume,seek", "gestures"),
    SettingsSearchEntry(R.string.pref_gesture_seek_title, null, "double,tap,seek,skip", "gestures"),
    SettingsSearchEntry(R.string.settings_category_pip, R.string.settings_category_pip_sub, "pip,picture,popup,overlay", "pip"),
    SettingsSearchEntry(R.string.pref_folders_title, R.string.pref_folders_summary, "folders,blacklist,hide,exclude", "folders"),
    SettingsSearchEntry(R.string.settings_category_list, R.string.settings_category_list_sub, "list,view,folders,hidden,thumbnails", "list"),
    SettingsSearchEntry(R.string.settings_category_decoder, R.string.settings_category_decoder_sub, "decoder,hw,hardware,software,sw,mpv,media3,engine", "decoder"),
    SettingsSearchEntry(R.string.settings_category_subtitle, R.string.settings_category_subtitle_sub, "subtitles,size,color,style,sync,delay", "subtitle"),
    SettingsSearchEntry(R.string.settings_category_downloader, R.string.settings_category_downloader_sub, "downloader,download,quality,format,folder,directory,ytdlp,sponsorblock,proxy", "downloader"),
    SettingsSearchEntry(R.string.download_quality_title, null, "quality,resolution,codec,format,sorter", "downloader"),
    SettingsSearchEntry(R.string.download_directory_title, null, "folder,directory,downloads,location,storage", "downloader"),
    SettingsSearchEntry(R.string.download_audio_directory_title, null, "audio,folder,directory,music", "downloader"),
    SettingsSearchEntry(R.string.download_audio_format_title, null, "audio,format,mp3,m4a,opus,flac", "downloader"),
    SettingsSearchEntry(R.string.download_audio_preset_title, null, "audio,preset,original,format,quality,no,conversion", "downloader"),
    SettingsSearchEntry(R.string.download_embed_thumbnail, null, "thumbnail,cover,embed,art", "downloader"),
    SettingsSearchEntry(R.string.download_group_playlists, null, "playlist,playlists,group,folder,folders", "downloader"),
    SettingsSearchEntry(R.string.download_group_by_site, null, "site,website,extractor,group,folder,youtube,soundcloud", "downloader"),
    SettingsSearchEntry(R.string.download_private_mode, null, "private,mode,history,record", "downloader"),
    SettingsSearchEntry(R.string.download_to_sd_card, null, "sd,card,external,storage", "downloader"),
    SettingsSearchEntry(R.string.download_sponsorblock_title, null, "sponsorblock,sponsor,skip,intro,outro", "downloader"),
    SettingsSearchEntry(R.string.download_custom_command_title, null, "custom,command,template,ytdlp,options", "downloader"),
    SettingsSearchEntry(R.string.pref_advanced_title, R.string.settings_category_development_sub, "advanced,development,diagnostics,database,reset,logs", "advanced"),
    SettingsSearchEntry(R.string.general_reset_button, null, "reset,defaults,clear", "advanced"),
    SettingsSearchEntry(R.string.settings_category_about, R.string.settings_category_about_sub, "about,version,beta,feedback", "about"),
)

/**
 * Working settings search (#158, mpvEx parity): searches MPx's real
 * settings by title, summary and keywords, and opens the matching
 * category when a result is tapped. Back closes the search first.
 *
 * Issue #303: [query] is hoisted into SettingsScreen (rememberSaveable,
 * next to isSearching/selectedId) and passed down — it must NOT live
 * here. Opening a result sets isSearching = false, removing this screen
 * from the composition and discarding any state it owns, so even a
 * rememberSaveable query here would reset on every return from a
 * category. The other remember state here is intentionally local: the
 * search index is static and cheap to rebuild, the FocusRequester is
 * transient (focus is re-requested on each entry), and the results are
 * derived from the query.
 */
@Composable
fun PreferencesSearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenCategory: (String) -> Unit,
) {
    val context = LocalContext.current
    val entries = remember { buildSettingsSearchIndex() }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val results = remember(query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) {
            emptyList()
        } else {
            entries.filter { entry ->
                context.getString(entry.titleRes).lowercase().contains(q) ||
                    (entry.subtitleRes != null && context.getString(entry.subtitleRes).lowercase().contains(q)) ||
                    entry.keywords.lowercase().contains(q)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .focusRequester(focusRequester),
            placeholder = { Text(stringResource(R.string.pref_search_hint)) },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null, tint = MpxTheme.colors.textMuted)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel), tint = MpxTheme.colors.textMuted)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MpxTheme.colors.surfaceRaised,
                unfocusedContainerColor = MpxTheme.colors.surfaceRaised,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = MpxTheme.colors.textPrimary,
                unfocusedTextColor = MpxTheme.colors.textPrimary,
                cursorColor = MpxTheme.colors.primary,
            ),
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (query.isBlank()) {
                item {
                    Text(
                        text = stringResource(R.string.pref_search_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MpxTheme.colors.textMuted,
                        modifier = Modifier.padding(horizontal = MpxSpaceL, vertical = 16.dp),
                    )
                }
            } else if (results.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.pref_search_no_results, query.trim()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MpxTheme.colors.textMuted,
                        modifier = Modifier.padding(horizontal = MpxSpaceL, vertical = 16.dp),
                    )
                }
            } else {
                item { Spacer(modifier = Modifier.height(8.dp)) }
                items(results, key = { "${it.targetCategory}:${it.titleRes}" }) { entry ->
                    PreferenceCard {
                        MpxListRow(
                            title = stringResource(entry.titleRes),
                            subtitle = entry.subtitleRes?.let { stringResource(it) },
                            onClick = { onOpenCategory(entry.targetCategory) },
                            trailing = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MpxTheme.colors.textMuted,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
