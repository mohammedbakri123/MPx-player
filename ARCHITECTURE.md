# MPx Player — Architecture

This document maps the code as it is today, so a contributor can answer three questions before touching anything: where does this feature live, what owns its state, and how do I test my change without breaking playback?

It is kept honest: every section describes the code on `master` now. If a section disagrees with the code, the code is right and this document must be updated in the same pull request.

---

## 1. Stack

- **Application Structure:** Single-module native Kotlin Android app, package `com.mpx.player` (`app/src/main/java/com/mpx/player/`, 207 Kotlin files).
- **UI & Presentation:** Jetpack Compose, Material 3 (`androidx.compose.material3`, with custom OLED true-black theme tokens; no experimental Expressive APIs are used), single-activity architecture (`MainActivity`), Compose Navigation (`ui/navigation/Navigation.kt`).
- **Playback Engine:** Native `libmpv` via `mpv-android-lib` 0.1.12. There is no ExoPlayer or Media3 in the application.
- **Persistence:** Room 2.8.5 (schema version 13, database `mpx_player.db` with indices on timestamp and query columns) for history, favorites, resume points, and playlists; `SharedPreferences` (`SharedPrefsAppSettingsRepository`) for user settings.
- **Dependency Injection:** Koin 4.2.2 (`di/AppModule.kt`, `di/PlaybackModule.kt`, `di/ViewModelModule.kt`).
- **Build System:** AGP 9.2.1, Kotlin 2.5.0-Beta2, KSP 2.3.13, Compose BOM 2026.06.01, compileSdk 36 (extension level 19), minSdk 24, targetSdk 34. Dependencies and version pins are managed in `gradle/libs.versions.toml`.

---

## 2. Architectural Layers

```
UI (Compose screens, components, theme tokens, navigation)
        │  collects state / invokes intents
Playback (PlaybackCoordinator, PlaybackStateStore, PlaybackService, queue, resume, subtitles)
        │  drives playback & observes events
Engine (PlayerEngine interface → MpvPlayerEngine facade → MpvCoreEngine + collaborators → MpvPort → libmpv)
        │
Data (Room DAOs, MediaStore scanners + cache, SharedPreferences)  ←  Domain (models & repository interfaces)
```

- **UI (`ui/`)** — Screens organized by tab under `ui/screens/` (`local`, `music`, `network`, `me`, `player`, `settings`, `about`), each with an accompanying ViewModel. Shared UI widgets live in `ui/components/`, design tokens (spacing, typography, true-black OLED colors) in `ui/theme/`, and routes in `ui/navigation/Navigation.kt` (`Screen` sealed class). Screens render state collected from ViewModels and dispatch user actions; they never touch playback engines or databases directly.
- **Playback (`playback/`)** — High-level playback coordination and session state:
  - `PlaybackStateStore` owns the mutable state flow (`_playbackState: MutableStateFlow<PlaybackState>`) and dispatches typed `PlaybackEvent`s.
  - `PlaybackCoordinator` orchestrates session lifecycles, transport controls, audio focus, preparing watchdog, and side-effects.
  - Extracted collaborators handle specific playback domains: `PlaybackQueue` (queue order, shuffle/repeat, persistence), `SubtitleManager` (external subtitle picking, styling, codepage), `EngineEventTranslator` (translating mpv events into state transitions), `PositionTracker` (throttled position updates), and `SleepTimer` (countdown timer).
  - `PlaybackService` is the foreground service keeping media playback alive in the background.
- **Engine (`playback/engine/`)** — Low-level player engine:
  - `PlayerEngine` defines the public contract for playback control and event observation.
  - `MpvPlayerEngine` serves as a facade implementing `PlayerEngine`, coordinating `MpvCoreEngine`, `MpvTrackSelection`, `MpvSubtitleRenderer`, `MpvSurfaceManager`, and `MpvEngineConfig`.
  - `MpvPort` abstracts the native `MPV` library calls (`RealMpvPort` in production, `FakeMpvPort` in unit tests).
- **Domain (`domain/`)** — Pure domain models (`VideoFile`, `MediaFolder`, `AudioTrack`, `Playlist`, `AppSettings`) and repository interfaces (`MediaRepository`, `MediaPersistenceRepository`, `AppSettingsRepository`). Contains zero Android framework, Room, or mpv dependencies.
- **Data (`data/`)** — Concrete implementations of domain repositories:
  - `MediaStoreRepository`: Thin facade coordinating `VideoMediaScanner`, `AudioMediaScanner`, `PlaylistMediaScanner`, `MediaStoreScannerHelper`, and `MediaStoreCache`.
  - `RoomMediaPersistenceRepository`: Manages SQLite persistence via `MpxDatabase` (Room v13).
  - `SharedPrefsAppSettingsRepository`: Handles persistent preferences.
- **Supporting Packages** — `network/` (stream URL parsing, shared link extraction), `update/` (`AppUpdateChecker` for in-app release checks against GitHub), and `util/` (`ThumbnailLoader`, `SubtitleSiblingFinder`, `SubtitleUtils`, `FileMediaInfoReader`, `FuzzySearch`, `LocaleHelper`, `MediaPermissions`, `PipUtils`).
- **Entry Points** — `MpxApplication` starts Koin modules (`appModule`, `playbackModule`, `viewModelModule`) and applies the stored night mode at process startup. `MainActivity` sets up Compose navigation, handles activity-result contracts, and receives required dependencies from Koin.

---

## 3. Playback Architecture & State Flow

### PlaybackStateStore & Event Dispatch

`PlaybackStateStore` (292 lines) is the single source of truth for all playback state:
1. It maintains `_playbackState: MutableStateFlow<PlaybackState>` and exposes the read-only `playbackState: StateFlow<PlaybackState>`.
2. It directly implements the narrow host interfaces required by collaborators: `SubtitleHost`, `QueueHost`, and `EngineEventHost`.
3. When collaborators emit events or request coordinator action, `PlaybackStateStore` dispatches strongly-typed `PlaybackEvent` instances (such as `MediaEnded`, `PlayQueueItem`, `TrackSelectionChanged`, `ErrorOccurred`).
4. To prevent startup races, events arriving before `PlaybackCoordinator` attaches its listener are buffered in order and flushed immediately upon attachment.

### PlaybackCoordinator

`PlaybackCoordinator` (1,063 lines) manages session coordination and implements `PlaybackStateSource`, `StreamPlayer`, `SleepTimerHost`, and `PositionSink`:
- Exposes `playbackState` directly via delegation to `PlaybackStateStore`.
- Handles high-level transport controls (`play`, `pause`, `seekTo`, `setSpeed`, `stop`).
- Manages the 20-second preparing watchdog timer to detect stuck media loads.
- Manages audio focus transitions (transient pause, ducking, regaining focus, becoming noisy).
- Listens to `PlaybackEvent`s from `PlaybackStateStore` and executes asynchronous side-effects (triggering the position ticker, updating history in the database via `Dispatchers.IO`, and resetting timers).
- Collaborators (`SubtitleManager`, `PlaybackQueue`, `EngineEventTranslator`) are constructed with `PlaybackStateStore` as their host, eliminating circular construction cycles and mutable attachment lifecycles.

### Playback Lifecycle Flow

```
User taps media in LocalScreen / MusicScreen
        │
ViewModel invokes PlaybackCoordinator.playVideoFiles(...)
        │
PlaybackQueue builds queue & updates PlaybackStateStore
        │
PlaybackCoordinator invokes PlayerEngine.load(...)
        │
MpvCoreEngine issues loadfile via MpvPort (RealMpvPort)
        │
mpv native events dispatched to MpvCoreEngine event observer
        │
EngineEventTranslator receives PlayerEngine.Listener callbacks
        │
PlaybackStateStore updates StateFlow<PlaybackState> & emits PlaybackEvent
        │
PlaybackCoordinator handles event (arms position ticker, persists history)
        │
UI screens collect PlaybackState and re-compose
```

---

## 4. Engine Architecture & Native Seam (MpvPort)

### The MpvPort Abstraction

Native calls to `libmpv` are decoupled behind the `MpvPort` interface (`playback/engine/MpvPort.kt`):
- `RealMpvPort`: Wraps `is.xyz.mpv.MPV` native methods for production execution on Android devices.
- `FakeMpvPort`: Provides an in-memory test double supporting deterministic command tracking, property writes/reads, property observation history, and event queuing for JVM unit tests.
- This seam allows all engine subsystems (`MpvCoreEngine`, `MpvTrackSelection`, `MpvSubtitleRenderer`, `MpvSurfaceManager`) to be thoroughly verified in unit tests without requiring an Android device or emulator.

### Engine Subsystems

- **`MpvPlayerEngine` (560 lines):** Facade implementing `PlayerEngine`. Delegates native commands and queries to internal engine collaborators. Retains presentation-specific aspect-ratio and video-zoom conversions (`setAspectRatio`, `setVideoZoom`). Also delegates `getMediaInfo()` (player Media Information page) and `getChapters()` (chapter markers on the seek bar) to `MpvTrackSelection`.
- **`MpvCoreEngine` (1,324 lines):** Core mpv lifecycle manager. Handles engine initialization, `loadfile` dispatch, position/duration tracking, playback state caching, property observation, and hardware decoder fallback chains (`attemptDecoderFallback`).
- **`MpvSurfaceManager` (173 lines):** Manages Android `Surface` attachment and libmpv video output (VO) recovery under `surfaceLock`. Restores `vo=gpu` upon surface creation, surface change, or playback resumption. Correctly suppresses redundant VO restoration during load preparation (`willLoad = true`) unless `voIsNull = true`.
- **`MpvTrackSelection` (373 lines):** Reads `track-list` and controls audio, video, and subtitle track selection (`aid`, `vid`, `sid`) via `MpvPort`. Interacts with `MpvSubtitleRenderer` to clear external subtitle pick reservations when embedded tracks or "Off" are selected. Also exposes `getMediaInfo()` (video/audio/subtitle technical info for the player Media Information page) and `getChapters()` (chapter list for the seek-bar markers).
- **`MpvSubtitleRenderer` (968 lines):** Manages subtitle styling options (`applySubtitleSettings`), codepage selection (`applySubtitleCodepage`), pending subtitle queueing during media loading, reapplication of remembered external subtitle picks, and asynchronous sub-add outcome confirmation with a 5000 ms timeout. Windows-1256 subtitles are always transcoded to a UTF-8 copy before sub-add (`uriToSubAdd`): mpv accepts the `sub-codepage` property set (verified by read-back) yet ignores it when decoding in this libmpv build, so the transcode — not the property — is the guaranteed path. The `sub-codepage` attempt remains only as a fallback when transcoding fails.
- **`MpvEngineConfig` (206 lines):** Maps user settings to mpv runtime options (audio delay, hardware decoding modes, and logarithmic video zoom mappings).
- **`MpvLifecycleManager` (218 lines) & `MpvMetricsTracker` (110 lines):** Handles MPV option configuration, initialization error capture, playback metrics, and dropped frame tracking.

---

## 5. Data Layer Architecture

### MediaStore Scanners & Cache Split

`MediaStoreRepository` (127 lines) is a lightweight facade implementing `MediaRepository`:
- **`VideoMediaScanner` (533 lines):** Scans video folders, individual folders, and whole-device video files from `MediaStore.Video.Media`.
- **`AudioMediaScanner` (214 lines):** Scans audio tracks, albums, artists, and music folders from `MediaStore.Audio.Media`.
- **`PlaylistMediaScanner` (204 lines):** Scans system and device playlists, resolving track members.
- **`MediaStoreScannerHelper` (218 lines):** Resolves storage volume names (`MediaStore.getExternalVolumeNames`, `StorageManager`) across internal storage, SD cards, and USB OTG drives, handling scoped storage path filtering.
- **`MediaStoreCache` (60 lines):** Instance-managed cache owned by `MediaStoreRepository` (registered as a Koin singleton). Retains `@Volatile` triples of the most recent folder and video scans, enabling instant rendering when navigating between tabs without flashing empty lists. Provides explicit invalidation methods (`invalidate()`, `invalidateFolders()`, `invalidateVideos()`). Static companion-object caching is completely eliminated.
- **Layering & Formatting:** Scanners return raw numeric values (byte sizes, millisecond durations) within domain models. String formatting (`formatSize`, `formatDuration`) is performed strictly in the UI layer (`ui/screens/local/components/LocalTopBars.kt`, list item rows), preventing data-to-UI layering inversions.
- **Pagination Feasibility Verdict:** Full cursor-based pagination was investigated and rejected: multi-volume storage queries cannot unify `LIMIT`/`OFFSET` without cross-cursor merging, folder/album groupings require in-memory aggregation of all tracks, and scoped-storage hidden file filtering must occur in Kotlin. Full in-memory scanning with instance caching remains optimal.

### Room Database (Version 13)

`MpxDatabase` (`data/local/db/MpxDatabase.kt`) manages SQLite persistence:
- **Entities & Tables:**
  - `playback_history` (`PlaybackHistoryEntity`): Indexed on `lastPlayedAt` for fast pagination and sorting.
  - `favourites` (`FavouriteEntity`): Indexed on `addedAt` and `mediaId`.
  - `playback_resume` (`PlaybackResumeEntity`): Indexed on `lastUpdated`.
  - `custom_playlists` (`CustomPlaylistEntity`): Indexed on `updatedAt`.
  - `playlist_items` (`PlaylistItemEntity`): Indexed on `playlistId` and `mediaUri`.
- **Migrations:** Upgraded from version 12 to 13 via `MIGRATION_12_13`, which created the five missing performance indices without data modification or table recreation.

---

## 6. Dependency Injection (Koin)

Dependencies are configured across three Koin modules in `app/src/main/java/com/mpx/player/di/`:

1. **`AppModule.kt` (38 lines):**
   - `single { MpxDatabase.getInstance(androidContext()) }`
   - `single<MediaRepository> { MediaStoreRepository(androidContext(), get()) }`
   - `single<MediaPersistenceRepository> { RoomMediaPersistenceRepository(get(), androidContext()) }`
   - `single<AppSettingsRepository> { SharedPrefsAppSettingsRepository(androidContext()) }`
2. **`PlaybackModule.kt` (45 lines):**
   - Registers `PlaybackCoordinator` as a process-wide `single` via `PlaybackCoordinator.production(...)`, injecting Koin's database, persistence, and settings singletons.
   - Registers explicit interface bindings:
     ```kotlin
     single<PlaybackStateSource> { get<PlaybackCoordinator>() }
     single<PlaybackController> { get<PlaybackCoordinator>() }
     single<StreamPlayer> { get<PlaybackCoordinator>() }
     ```
   - The mpv engine is deliberately constructed inside `PlaybackCoordinator.production` rather than as a separate Koin declaration, preserving its single-owner lifecycle.
3. **`ViewModelModule.kt` (86 lines):**
   - ViewModels resolve their dependencies via clean domain interfaces: `PlayerViewModel` injects `PlaybackStateSource`, `NetworkViewModel` injects `StreamPlayer`, and library ViewModels inject `MediaRepository` and `AppSettingsRepository`.
   - Concrete `PlaybackCoordinator` is never referenced or requested directly in `ViewModelModule`.

---

## 7. Current Line Counts

*Measured directly via `wc -l` on `master` (a0c878ab, v0.4.62):*

| Component / File | Path | Lines |
|---|---|---|
| `PlaybackCoordinator` | `app/src/main/java/com/mpx/player/playback/PlaybackCoordinator.kt` | 1,063 |
| `PlaybackStateStore` | `app/src/main/java/com/mpx/player/playback/PlaybackStateStore.kt` | 292 |
| `PlaybackQueue` | `app/src/main/java/com/mpx/player/playback/PlaybackQueue.kt` | 596 |
| `SubtitleManager` | `app/src/main/java/com/mpx/player/playback/SubtitleManager.kt` | 424 |
| `EngineEventTranslator` | `app/src/main/java/com/mpx/player/playback/EngineEventTranslator.kt` | 363 |
| `MpvCoreEngine` | `app/src/main/java/com/mpx/player/playback/engine/MpvCoreEngine.kt` | 1,324 |
| `MpvSurfaceManager` | `app/src/main/java/com/mpx/player/playback/engine/MpvSurfaceManager.kt` | 173 |
| `MpvTrackSelection` | `app/src/main/java/com/mpx/player/playback/engine/MpvTrackSelection.kt` | 373 |
| `MpvSubtitleRenderer` | `app/src/main/java/com/mpx/player/playback/engine/MpvSubtitleRenderer.kt` | 968 |
| `MpvEngineConfig` | `app/src/main/java/com/mpx/player/playback/engine/MpvEngineConfig.kt` | 206 |
| `MpvPlayerEngine` | `app/src/main/java/com/mpx/player/playback/engine/MpvPlayerEngine.kt` | 560 |
| `MediaStoreRepository` | `app/src/main/java/com/mpx/player/data/repository/MediaStoreRepository.kt` | 133 |
| `VideoMediaScanner` | `app/src/main/java/com/mpx/player/data/repository/VideoMediaScanner.kt` | 547 |
| `AudioMediaScanner` | `app/src/main/java/com/mpx/player/data/repository/AudioMediaScanner.kt` | 226 |
| `PlaylistMediaScanner` | `app/src/main/java/com/mpx/player/data/repository/PlaylistMediaScanner.kt` | 216 |
| `MediaStoreScannerHelper` | `app/src/main/java/com/mpx/player/data/repository/MediaStoreScannerHelper.kt` | 229 |
| `MediaStoreCache` | `app/src/main/java/com/mpx/player/data/repository/MediaStoreCache.kt` | 65 |
| `RoomMediaPersistenceRepository` | `app/src/main/java/com/mpx/player/data/repository/RoomMediaPersistenceRepository.kt` | 277 |
| `PlaybackModule` | `app/src/main/java/com/mpx/player/di/PlaybackModule.kt` | 39 |
| `AppModule` | `app/src/main/java/com/mpx/player/di/AppModule.kt` | 35 |
| `ViewModelModule` | `app/src/main/java/com/mpx/player/di/ViewModelModule.kt` | 86 |
| `PlayerScreen` | `app/src/main/java/com/mpx/player/ui/screens/player/PlayerScreen.kt` | 540 |
| `PlayerTopChrome` | `app/src/main/java/com/mpx/player/ui/screens/player/components/PlayerTopChrome.kt` | 275 |
| `FloatingIsland` | `app/src/main/java/com/mpx/player/ui/screens/player/components/FloatingIsland.kt` | 74 |
| `PlaybackSheetMediaInfoPage` | `app/src/main/java/com/mpx/player/ui/screens/player/components/playbacksheet/PlaybackSheetMediaInfoPage.kt` | 335 |
| `MusicScreen` | `app/src/main/java/com/mpx/player/ui/screens/music/MusicScreen.kt` | 627 |
| `LocalScreen` | `app/src/main/java/com/mpx/player/ui/screens/local/LocalScreen.kt` | 551 |
| `FileMediaInfoScreen` | `app/src/main/java/com/mpx/player/ui/screens/local/FileMediaInfoScreen.kt` | 117 |
| `FileMediaInfoReader` | `app/src/main/java/com/mpx/player/util/FileMediaInfoReader.kt` | 184 |
| `MeScreen` | `app/src/main/java/com/mpx/player/ui/screens/me/MeScreen.kt` | 534 |
| `SettingsScreen` | `app/src/main/java/com/mpx/player/ui/screens/settings/SettingsScreen.kt` | 380 |

---

## 8. Test Suite & Verification

### Test Inventory & Counts

*Measured by counting `@Test` annotations across source trees:*
- **JVM Unit Tests (`app/src/test/`):** Exactly **432 tests** across 30 test files.
  - *Source derivation:* PR #479 initial CI run executed 424 tests; CI fix commit `ea359f4e` added 1 test in `MpvSubtitleRendererTest` (bringing the total to 425); Phase 4 Step 4.2 added `MediaStoreCacheTest` with 7 tests, establishing the exact total of 432 unit tests.
- **Instrumented Tests (`app/src/androidTest/`):** **10 tests** across 2 files (`SurfaceAttachDetachDeviceTest`: 6, `ComposeUiSmokeTest`: 4).
- **Test Fixtures (`app/src/testFixtures/`):** Lightweight shared fakes (`FakeMpvPort`, `FakePlayerEngine`, `FakePlaybackStateSource`, `FakeSettingsRepository`, `FakeMediaPersistenceRepository`). Contains zero third-party mocking framework dependencies (no MockK or Mockito).

### Test Distribution by Area

| Area | Primary Test Files | Tests | Key Behaviors Covered |
|---|---|---|---|
| Engine & Native Port | `MpvCoreEngineTest`, `MpvTrackSelectionTest`, `MpvSubtitleRendererTest`, `MpvSurfaceManagerTest`, `MpvEngineConfigTest`, `MpvLifecycleManagerTest`, `MpvMetricsTrackerTest` | 131 | Native command dispatch, track switching, subtitle codepage / confirm timeout, surface VO restore, decoder fallback, buffering |
| Playback Coordination | `PlaybackCoordinatorTest`, `PlaybackStateStoreTest`, `PlaybackQueueTest`, `EngineEventTranslatorTest`, `SubtitleManagerTest`, `PositionTrackerTest`, `SleepTimerTest` | 130 | State publishing, event queueing, queue shuffle/repeat, watchdog, audio focus, session persistence |
| Data & Repositories | `MediaStoreCacheTest`, `MpxDatabaseMigrationTest` | 10 | Cache invalidation, fallback path lookup, Room v12→v13 index migration |
| ViewModels | `PlayerViewModelTest`, `LocalViewModelTest`, `MusicViewModelTest`, `SettingsViewModelTest`, `MeViewModelTest`, `NetworkViewModelTest` | 101 | UI state emissions, filtering, sorting, tab switching, settings mutations |
| DI & Architecture | `DiGraphTest` | 8 | Koin module resolution, interface bindings (`PlaybackStateSource`, `StreamPlayer`), singleton consistency |
| UI & Formatting | `FormattersTest`, `MediaSortComparatorTest` | 16 | Duration formatting, byte size scaling, locale consistency, list sorting |
| Network & Utilities | `StreamUrlTest`, `SharedLinkTest`, `FuzzySearchTest`, `SubtitleSiblingFinderTest`, `SubtitleUtilsTest` | 21 | URL matching, fuzzy search algorithms, subtitle sibling association |

### CI Verification Gates

Continuous Integration (`.github/workflows/ci.yml`) runs on GitHub Actions:
1. **`build` job:** Runs on all pull requests and pushes to `master`. Executes `./gradlew --no-daemon assembleDebug testDebugUnitTest` on `ubuntu-latest` with JDK 17 (Temurin) and Android SDK 36.
2. **`changes` job:** Evaluates changed paths on pull requests. Sets `should_minify=true` if changes touch build scripts, ProGuard configuration, the Android manifest, `playback/`, or `di/`.
3. **`build-minified` job:** Runs on version-tag pushes (`v*`), manual workflow dispatches, or when `changes` flags R8-sensitive modifications. Builds `assembleMinified` and executes `dexdump` smoke assertions ensuring native reflection targets (e.g. `is.xyz.mpv`) are preserved by R8.

> [!NOTE]
> **Local VM Testing Invariant:** Local JVM unit test execution fails on this development VM because the supervisor drops loopback TCP connections, terminating Gradle test-worker IPC (`SuiteTestDefinitionProcessor.stop()` NPE). Continuous Integration is the automated test execution gate; local development validates compilation via `./gradlew :app:assembleDebug :app:assembleMinified :app:compileDebugUnitTestSources --offline`.

---

## 9. Contributor Guide & FAQ

### Where do I change X?

| I want to… | Start from | Invariants & Notes |
|---|---|---|
| Add or modify a setting | `domain/settings/AppSettings.kt`, `data/settings/SharedPrefsAppSettingsRepository.kt`, `ui/screens/settings/SettingsViewModel.kt` | Add string resources across all 9 supported locales in `app/src/main/res/values-*/strings.xml`. |
| Change subtitle selection or codepage | `playback/SubtitleManager.kt` and `playback/engine/MpvSubtitleRenderer.kt` | Detection runs at load time from the raw URI, not only at pick time. Windows-1256 is always transcoded to a UTF-8 copy before sub-add — mpv accepts the `sub-codepage` set yet ignores it when decoding, so the transcode is the guaranteed path and the property set is only a fallback. Subtitle confirmation timeout is 5000 ms. |
| Modify queue behavior (shuffle, repeat, next/previous) | `playback/PlaybackQueue.kt` | `PlaybackQueue` is the sole source of truth for queue items. Persists state via `QueueStore`. |
| Handle engine events or decoder fallbacks | `playback/EngineEventTranslator.kt` and `playback/PlaybackStateStore.kt` | Translator maps raw engine callbacks to typed `PlaybackEvent`s; `PlaybackCoordinator` applies side-effects. |
| Change video surface or display output | `playback/engine/MpvSurfaceManager.kt` | All surface mutations must acquire `surfaceLock`. VO restoration to `gpu` is suppressed when `willLoad = true` unless `voIsNull = true`. |
| Update audio, video, or subtitle tracks | `playback/engine/MpvTrackSelection.kt` | Drives mpv `aid`, `vid`, `sid` properties. Selecting embedded tracks clears remembered external picks. |
| Add a network stream source | `network/StreamUrl.kt` and `ui/screens/network/NetworkViewModel.kt` | Streams route through `PlaybackCoordinator.playStream(...)` using `StreamPlayer`. |
| Add or modify a Room database table | `data/local/db/MpxDatabase.kt` and corresponding entity/DAO in `data/local/` | Always increment database version, add an explicit migration, and write a migration test in `MpxDatabaseMigrationTest.kt`. |
| Modify MediaStore scanning or caching | `data/repository/` (`VideoMediaScanner`, `AudioMediaScanner`, `MediaStoreCache`) | Keep scanners returning raw numeric values; format strings strictly in the UI. Invalidate cache via `MediaStoreCache`. |
| Add a new screen | `ui/navigation/Navigation.kt`, `ui/screens/<feature>/<Feature>Screen.kt`, and `<Feature>ViewModel.kt` | Register ViewModel in `di/ViewModelModule.kt` resolving dependencies via domain interfaces. |
