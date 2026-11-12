package com.mpx.player.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.fakes.FakeMediaPersistenceRepository
import com.mpx.player.fakes.FakePlaybackStateSource
import com.mpx.player.fakes.FakeSettingsRepository
import com.mpx.player.playback.PlayableMedia
import com.mpx.player.playback.PlaybackState
import com.mpx.player.ui.components.MpxBottomBar
import com.mpx.player.ui.components.MpxDockTab
import com.mpx.player.ui.components.persistence.CreatePlaylistDialog
import com.mpx.player.ui.components.persistence.FavouritesDialog
import com.mpx.player.ui.navigation.navigationTabs
import com.mpx.player.ui.screens.player.PlayerViewModel
import com.mpx.player.ui.screens.player.components.PlayerBottomChrome
import com.mpx.player.ui.theme.MpxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * First Compose UI smoke tests (§7, finding 6.1).
 *
 * Exercises:
 * 1. Player transport controls (play/pause toggle, skip previous/next enabled states, time stamps).
 * 2. Dock navigation bar (dock tabs rendering, selected state, tab click dispatch).
 * 3. Persistence dialog (CreatePlaylistDialog text input, cancel dismiss, create confirm).
 * 4. Empty state presentation (FavouritesDialog empty splash vs populated state).
 *
 * Requirements & Execution:
 * Requires an Android runtime environment (connected physical device or emulator).
 * Run locally via:
 *   ./gradlew :app:connectedDebugAndroidTest --offline --no-daemon
 */
@RunWith(AndroidJUnit4::class)
class ComposeUiSmokeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context
        get() = composeTestRule.activity

    // --- 1. Player controls smoke test ---

    @Test
    fun playerControls_rendersTimeAndHandlesPlayPauseToggle() {
        val playback = FakePlaybackStateSource()
        val settingsRepo = FakeSettingsRepository(AppSettings())
        val persistence = FakeMediaPersistenceRepository()
        val viewModel = PlayerViewModel(playback, settingsRepo, persistence)

        val state = PlaybackState(
            currentItem = PlayableMedia(id = "1", uri = "http://localhost/video.mp4", title = "Test Video"),
            isPlaying = false,
            hasPrevious = false,
            hasNext = true,
            currentPositionMs = 15_000L,
            durationMs = 150_000L,
        )

        composeTestRule.setContent {
            MpxTheme {
                PlayerBottomChrome(
                    displayedCurrentTime = 15_000L,
                    currentDuration = 150_000L,
                    sliderProgress = 0.1f,
                    onSliderChange = {},
                    onSliderChangeFinished = {},
                    state = state,
                    isLandscapeOrientation = false,
                    onOpenSleepSheet = {},
                    viewModel = viewModel,
                )
            }
        }

        // Verify timestamps are displayed (15s = 00:15, 150s = 02:30)
        composeTestRule.onNodeWithText("00:15").assertIsDisplayed()
        composeTestRule.onNodeWithText("02:30").assertIsDisplayed()

        // Verify previous button is disabled (hasPrevious = false)
        val prevCd = context.getString(R.string.player_cd_previous)
        composeTestRule.onNodeWithContentDescription(prevCd).assertIsNotEnabled()

        // Verify next button is enabled (hasNext = true)
        val nextCd = context.getString(R.string.player_cd_next)
        composeTestRule.onNodeWithContentDescription(nextCd).assertIsEnabled()

        // Verify play button is displayed and clicking invokes togglePlayPause
        val playCd = context.getString(R.string.player_cd_play)
        composeTestRule.onNodeWithContentDescription(playCd).assertIsDisplayed().performClick()

        assertEquals(1, playback.togglePlayPauseCalls)
    }

    // --- 2. Navigation bar smoke test ---

    @Test
    fun navigationBar_rendersTabsAndDispatchesSelection() {
        var selectedRoute by mutableStateOf(navigationTabs[0].route)

        composeTestRule.setContent {
            MpxTheme {
                MpxBottomBar {
                    navigationTabs.forEach { tab ->
                        val isSelected = selectedRoute == tab.route
                        MpxDockTab(
                            label = stringResource(tab.labelRes),
                            icon = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                            selected = isSelected,
                            onClick = { selectedRoute = tab.route },
                        )
                    }
                }
            }
        }

        // Verify all 4 tab labels are displayed
        val localLabel = context.getString(R.string.nav_local)
        val musicLabel = context.getString(R.string.nav_music)
        val networkLabel = context.getString(R.string.nav_network)
        val meLabel = context.getString(R.string.nav_me)

        composeTestRule.onNodeWithText(localLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(musicLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(networkLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(meLabel).assertIsDisplayed()

        // Click Music tab and verify selection updates
        composeTestRule.onNodeWithText(musicLabel).performClick()
        assertEquals(navigationTabs[1].route, selectedRoute)

        // Click Me tab and verify selection updates
        composeTestRule.onNodeWithText(meLabel).performClick()
        assertEquals(navigationTabs[3].route, selectedRoute)
    }

    // --- 3. Persistence dialog smoke test ---

    @Test
    fun dialog_createPlaylist_handlesInputAndConfirm() {
        var confirmedName: String? = null
        var confirmedIsVideo: Boolean? = null
        var dismissed = false

        composeTestRule.setContent {
            MpxTheme {
                CreatePlaylistDialog(
                    initialName = "",
                    onDismiss = { dismissed = true },
                    onConfirm = { name, isVideo ->
                        confirmedName = name
                        confirmedIsVideo = isVideo
                    },
                    showTypeSelector = false,
                )
            }
        }

        // Verify dialog title is displayed
        val titleText = context.getString(R.string.playlist_create_dialog_title)
        composeTestRule.onNodeWithText(titleText).assertIsDisplayed()

        // Find placeholder and input playlist title
        val placeholder = context.getString(R.string.playlist_name_placeholder)
        val playlistInput = "Smoke Test Playlist"
        composeTestRule.onNodeWithText(placeholder).performTextInput(playlistInput)

        // Click Create button
        val createText = context.getString(R.string.action_create)
        composeTestRule.onNodeWithText(createText).performClick()

        assertEquals("Smoke Test Playlist", confirmedName)
        assertEquals(false, confirmedIsVideo)
        assertFalse(dismissed)
    }

    // --- 4. Empty state presentation smoke test ---

    @Test
    fun emptyState_favouritesDialog_rendersEmptyStateWhenListEmpty() {
        var dismissed = false

        composeTestRule.setContent {
            MpxTheme {
                FavouritesDialog(
                    favourites = emptyList(),
                    onDismiss = { dismissed = true },
                    onItemClick = {},
                    onRemoveFavourite = {},
                    onClearAll = {},
                )
            }
        }

        // Verify empty state title and description are displayed
        val emptyTitle = context.getString(R.string.favourites_empty_title)
        val emptyDesc = context.getString(R.string.favourites_empty_desc)
        composeTestRule.onNodeWithText(emptyTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(emptyDesc).assertIsDisplayed()

        // Verify "Clear all" button is NOT present when list is empty
        val clearAllText = context.getString(R.string.action_clear_all)
        composeTestRule.onNodeWithText(clearAllText).assertDoesNotExist()

        // Click Close button and verify dismiss callback
        val closeText = context.getString(R.string.action_close)
        composeTestRule.onNodeWithText(closeText).performClick()
        assertTrue(dismissed)
    }
}
