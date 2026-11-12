package com.mpx.player.ui.screens.player.components.playbacksheet

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.playback.PlaybackController
import com.mpx.player.playback.PlaybackState
import com.mpx.player.ui.components.StepperRow
import com.mpx.player.ui.components.player.formatTimeMs
import com.mpx.player.ui.theme.MpxTheme
import kotlin.math.ceil

@Composable
internal fun PlaybackSheetSleepPage(
    state: PlaybackState,
    playbackController: PlaybackController,
) {
    val context = LocalContext.current
    state.sleepTimerRemainingSeconds?.let { remaining ->
        Text(
            text = formatTimeMs(remaining * 1000L),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MpxTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    }
    val choices = listOf(
        15 to R.string.player_sleep_timer_15,
        30 to R.string.player_sleep_timer_30,
        45 to R.string.player_sleep_timer_45,
        60 to R.string.player_sleep_timer_60,
        0 to R.string.player_sleep_timer_off,
    )
    // Presets as chips; unlike the old dialog the sheet stays open so the
    // remaining time above is visible immediately after a choice.
    choices.chunked(3).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.forEach { (mins, labelRes) ->
                val isSelected = if (mins == 0) {
                    state.sleepTimerMinutes == null
                } else {
                    state.sleepTimerMinutes == mins
                }
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (mins == 0) {
                            playbackController.cancelSleepTimer()
                            Toast.makeText(
                                context,
                                context.getString(R.string.player_sleep_timer_canceled_toast),
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            playbackController.setSleepTimer(mins)
                            Toast.makeText(
                                context,
                                context.getString(R.string.player_sleep_timer_set_toast, mins),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    label = { Text(stringResource(labelRes)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MpxTheme.colors.primary,
                        selectedLabelColor = MpxTheme.colors.textPrimary,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
            // Pad a short row so chips keep an even width.
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(8.dp))
    }
    // Custom time: same ±5 minute stepper idea as the old presets, but any
    // 5..180 minute value.
    // Issue #208: the stepper used to read the set value through a
    // `?: 30` fallback, so with no timer running it showed "30 min" while
    // the Off chip was selected — and the first + tap set a 35-minute timer
    // from that phantom base. With no timer the stepper now shows Off (the
    // same string the Off chip and the sleep chip near the controls use); the first tap
    // in either direction starts a timer at the 30-minute default, and
    // only then does the value step ±5 from the running timer.
    // Issue #238: while a timer runs, the base is the REMAINING time
    // ceiled to minutes — not sleepTimerMinutes, which is the set duration
    // frozen at set time (the tick loop only updates
    // sleepTimerRemainingSeconds). A minute into a 30 min timer the
    // stepper shows "29 min" and + sets 34, matching the headline
    // countdown and the sleep chip near the controls. Ceil, not floor: 29:59 remaining
    // is still "30 min" until it actually drops below 29 minutes.
    val timerMinutes: Int? = state.sleepTimerRemainingSeconds?.let { remaining ->
        ceil(remaining / 60.0).toInt()
    }
    StepperRow(
        label = stringResource(R.string.playback_sheet_sleep_custom),
        valueText = timerMinutes?.let { "$it min" }
            ?: stringResource(R.string.player_sleep_timer_off),
        minusContentDescription = stringResource(R.string.sleep_timer_decrease),
        plusContentDescription = stringResource(R.string.sleep_timer_increase),
        onMinus = {
            val mins = timerMinutes?.let { (it - 5).coerceIn(5, 180) } ?: 30
            // Issue #285: minus at the 5-minute floor is a no-op — with
            // 4:59 remaining the stepper shows "5 min" and the stepped
            // value is also 5, so re-arming setSleepTimer would reset the
            // countdown to a full 5:00, making the timer longer.
            if (timerMinutes == null || mins != timerMinutes) {
                playbackController.setSleepTimer(mins)
                Toast.makeText(
                    context,
                    context.getString(R.string.player_sleep_timer_set_toast, mins),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        },
        onPlus = {
            val mins = timerMinutes?.let { (it + 5).coerceIn(5, 180) } ?: 30
            // Issue #285: same as minus — plus at the 180-minute ceiling
            // is a no-op instead of re-arming a full 180.
            if (timerMinutes == null || mins != timerMinutes) {
                playbackController.setSleepTimer(mins)
                Toast.makeText(
                    context,
                    context.getString(R.string.player_sleep_timer_set_toast, mins),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        },
        onReset = if (state.sleepTimerMinutes != null) {
            {
                playbackController.cancelSleepTimer()
                Toast.makeText(
                    context,
                    context.getString(R.string.player_sleep_timer_canceled_toast),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } else {
            null
        },
    )
}
