package com.mpx.player.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.ui.components.MpxSecondaryButton
import com.mpx.player.ui.theme.MpxSpaceL
import com.mpx.player.ui.theme.MpxSpaceM
import com.mpx.player.ui.theme.MpxTheme

/**
 * Player Layout editor (#158, mpvEx parity): a basic, working editor for
 * the Player Menu rows — visibility toggles + up/down reorder, persisted
 * in [AppSettings.playerMenuLayout] (visible ids) and
 * [AppSettings.hiddenPlayerMenuIds] (explicitly hidden ids, #201) and
 * applied by the Playback sheet and the top-bar actions (#160): the sheet
 * renders the rows in this order the next time it opens, and the top bar
 * follows it live.
 *
 * Limitation vs mpvEx: mpvEx's editor is a full drag-to-arrange editor
 * covering every player button including the transport row and double-tap
 * zones. This editor covers the 13 Player Menu rows only; the transport
 * row stays fixed and the full drag-to-arrange editor is a follow-up.
 */
@Composable
fun PlayerLayoutScreen(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
) {
    val visible = parsePlayerMenuLayout(settings.playerMenuLayout, settings.hiddenPlayerMenuIds)
    val hidden = PLAYER_MENU_IDS.filter { it !in visible }

    fun applyVisible(next: List<String>) {
        // #201: keep the hidden set in sync — every known id not in the
        // visible list is recorded as explicitly hidden, so the parse
        // never resurrects it as a "missing" id.
        onSettingsChanged(
            settings.copy(
                playerMenuLayout = next.joinToString(","),
                hiddenPlayerMenuIds = (PLAYER_MENU_IDS - next.toSet()).joinToString(","),
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = MpxSpaceL),
    ) {
        Text(
            text = stringResource(R.string.pref_layout_editor_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MpxTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = MpxSpaceL),
        )
        Spacer(modifier = Modifier.height(MpxSpaceL))

        PreferenceSectionHeader(text = stringResource(R.string.pref_layout_visible_header))
        PreferenceCard {
            visible.forEachIndexed { index, id ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MpxSpaceL, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(playerMenuRowLabelRes(id)),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MpxTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            val next = visible.toMutableList()
                            val moved = next.removeAt(index)
                            next.add(index - 1, moved)
                            applyVisible(next)
                        },
                        enabled = index > 0,
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.pref_layout_move_up),
                            tint = MpxTheme.colors.textSecondary,
                        )
                    }
                    IconButton(
                        onClick = {
                            val next = visible.toMutableList()
                            val moved = next.removeAt(index)
                            next.add(index + 1, moved)
                            applyVisible(next)
                        },
                        enabled = index < visible.lastIndex,
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.pref_layout_move_down),
                            tint = MpxTheme.colors.textSecondary,
                        )
                    }
                    IconButton(onClick = { applyVisible(visible - id) }) {
                        Icon(
                            Icons.Filled.VisibilityOff,
                            contentDescription = stringResource(R.string.pref_layout_hide),
                            tint = MpxTheme.colors.textSecondary,
                        )
                    }
                }
                if (index < visible.lastIndex) PreferenceDivider()
            }
        }

        if (hidden.isNotEmpty()) {
            Spacer(modifier = Modifier.height(MpxSpaceL))
            PreferenceSectionHeader(text = stringResource(R.string.pref_layout_hidden_header))
            PreferenceCard {
                hidden.forEachIndexed { index, id ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MpxSpaceL, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(playerMenuRowLabelRes(id)),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MpxTheme.colors.textMuted,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { applyVisible(visible + id) }) {
                            Icon(
                                Icons.Filled.Visibility,
                                contentDescription = stringResource(R.string.pref_layout_show),
                                tint = MpxTheme.colors.textSecondary,
                            )
                        }
                    }
                    if (index < hidden.lastIndex) PreferenceDivider()
                }
            }
        }

        Spacer(modifier = Modifier.height(MpxSpaceL))
        MpxSecondaryButton(
            text = stringResource(R.string.pref_layout_reset),
            onClick = { applyVisible(PLAYER_MENU_IDS) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MpxSpaceL),
        )
        Spacer(modifier = Modifier.size(MpxSpaceM))
    }
}
