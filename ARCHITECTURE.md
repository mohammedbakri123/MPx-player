# MPx Player — Architecture

This document maps the code as it is today, so a new contributor can answer
three questions before touching anything: where does this feature live, what
owns its state, and how do I test my change without breaking playback?

It is kept honest: sections marked **current** describe the code on `master`
now; sections marked **target** describe where the migration plan is taking
it. If a section disagrees with the code, the code is right and this file
needs an update in the same pull request.

The migration plan itself lives outside the repo, with the maintainer. The
migration status at the bottom of this file is the in-repo record of how far
it has got.

## Stack (current)

- Single-module native Kotlin Android app, package `com.mpx.player`
  (`app/src/main/java/com/mpx/player/`, 123 Kotlin files)
- UI: Jetpack Compose, Material 3, one activity (`MainActivity`),
  Compose Navigation
- Playback: mpv only, via `mpv-android-lib` 0.1.12. There is no Media3 /
  ExoPlayer in the app any more.
- Persistence: Room 2.8.5 for library data, SharedPreferences for settings
- Build: AGP 9.2.1, Kotlin 2.5.0-Beta2, KSP 2.3.13, Compose BOM 2026.06.01,
  compileSdk 36, minSdk 24, targetSdk 34. Dependencies and versions
  are managed in `gradle/libs.versions.toml`.

## Layers (current)

```
UI (Compose screens, theme, navigation, components)
        │  calls
Playback (PlaybackCoordinator, PlaybackService, queue, resume, subtitles)
        │  calls
Engine (PlayerEngine interface → MpvPlayerEngine → mpv)
        │
Data (Room, MediaStore, SharedPreferences)  ←  Domain (models + interfaces)
```

- **UI** — `ui/`: screens per tab under `ui/screens/` (`local`, `music`,
  `network`, `me`, `player`, `settings`, `about`), each beside its
  ViewModel (Phase 2 pattern — see below); shared pieces under
  `ui/components/`, design tokens under `ui/theme/`, routes under
  `ui/navigation/Navigation.kt` (`Screen` sealed class: `local`, `music`,
  `network`, `me`, `player`). Screens render state and send events; they
  are not supposed to talk to mpv directly.
- **Playback** — `playback/`: `PlaybackCoordinator` is the centre of the app.
  It owns the published state, the position ticker, the preparing
  watchdog, the sleep timer, transport and session functions, and —
  through the three extracted pieces — the queue, resume positions,
  subtitle handling, and the engine listener; it exposes playback
  state as `StateFlow<PlaybackState>`.
  `PlaybackService` is the foreground service for background playback.
  `PlaybackQueueStore` persists the queue; `PlaybackResumeManager` saves
  and restores positions.
- **Engine** — `playback/engine/`: `PlayerEngine` is the interface the
  player UI is supposed to program against (`load`, `play`, `pause`,
  `seekTo`, `setSpeed`, `selectTrack`, `replaceMediaItem`, `stop`,
  `release`, `setDecoderMode`, plus a `Listener` for state/track/error
  callbacks). `MpvPlayerEngine` is the only implementation — since
  Phase 4 steps 1–4 it is a thin facade over `MpvCoreEngine`, which
  owns the mpv lifecycle, the load/play commands, position/state
  observation and the decoder fallback chain, over
  `MpvTrackSelection`, which owns track listing/switching, over
  `MpvSubtitleRenderer`, which owns subtitle rendering/confirmation,
  and over `MpvEngineConfig`, which owns settings-to-mpv
  configuration (playback tweaks, hwdec mode, video-zoom
  conversion). What stays in the facade is presentation:
  `setAspectRatio`/`setVideoZoom` and the #226 ZOOM state they own.
- **Domain** — `domain/`: plain models (`VideoFile`, `MediaFolder`, music
  and persistence models) and repository interfaces (`MediaRepository`,
  `MediaPersistenceRepository`, `AppSettingsRepository` with `load()` /
  `save()`). No Android, Room, or mpv types belong here.
- **Data** — `data/`: the implementations. `MediaStoreRepository` reads
  video/audio URIs from MediaStore. `RoomMediaPersistenceRepository`
  implements persistence on top of `MpxDatabase` (Room, version 12,
  database `mpx_player.db`, entities `Favourite`, `PlaybackHistory`,
  `CustomPlaylist`, `PlaylistItem`, `PlaybackResume`). Settings live in
  `SharedPrefsAppSettingsRepository`.
- **Supporting** — `network/` (stream URLs, shared links), `update/`
  (`AppUpdateChecker`, compares the installed version against the latest
  GitHub release), `util/` (`ThumbnailLoader`, `SubtitleSiblingFinder`,
  `SubtitleUtils`, `FuzzySearch`, `LocaleHelper`, `MediaPermissions`,
  `PipUtils`).
- **App entry** — `MpxApplication` (registered as `.MpxApplication` in the
  manifest) starts Koin (`di/AppModule`, `di/PlaybackModule`,
  `di/ViewModelModule` — the one place objects are built) and applies the
  stored theme's night mode before any activity is created.
  `MainActivity` hosts the navigation, the tab scaffold, and the
  activity-result launchers; it receives its `PlaybackCoordinator` and
  settings repository from Koin.

## How a video gets from a tap to mpv (current)

1. The Local tab (`ui/screens/local/LocalScreen.kt`) lists folders and
   files through `LocalViewModel`, which reads them from
   `MediaStoreRepository`.
2. Tapping a video calls into `PlaybackCoordinator` (`playVideoFiles`,
   `playQueue`, `playLiveQueueItem`, …), which builds a queue of
   `PlayableMedia` items.
3. `PlaybackCoordinator` hands the queue to the engine through the
   `PlayerEngine` interface (`load(items, startIndex, resumeMs)`), and
   `MpvPlayerEngine` drives mpv.
4. mpv events come back through `PlayerEngine.Listener` into
   `PlaybackCoordinator`, which updates its `StateFlow<PlaybackState>`.
5. `PlayerScreen` collects that state and renders transport, position,
   tracks, and subtitles. The foreground `PlaybackService` keeps audio
   going when the app leaves the foreground.
6. Resume positions are saved by `PlaybackResumeManager` into Room
   (`PlaybackResumeEntity`) and restored on the next play.

## The two things that surprise newcomers (current)

**Dependency injection and ViewModels exist now — but playback still
goes through one god object.** Since Phases 1–2, objects are built in
`di/` (Koin: `di/AppModule`, `di/PlaybackModule`, `di/ViewModelModule`)
and every screen has a ViewModel beside it whose dependencies arrive
through its constructor, so screens are testable with fake
repositories. What has NOT changed yet: screens still hold
`PlaybackCoordinator` directly for transport (play/pause, queue,
subtitles) — that is the one remaining exception to the ViewModel
pattern, and it is deliberate (see below). `PlaybackCoordinator`
is the centre of playback: it builds the database, persistence,
settings, resume manager, engine, and the three extracted pieces
through its `production()` factory, and it is their host. No
`getInstance` call site remains in UI or playback code — the only
ones left are `MpxDatabase.getInstance` in `di/AppModule` and in
`PlaybackCoordinator.production()`, which are the legitimate
construction points. The other hand-built construction left is the
settings repository, built directly in
`PlaybackCoordinator.production()`, in `PlaybackService.start()`,
and in `MpvPlayerEngine` (Phase 4), plus two data/util internals —
`MediaStoreRepository` (`showHiddenFiles` read) and `PipUtils`
(default argument) — which have no phase assigned yet.

**Two files are very large.** `PlaybackCoordinator` is 1,086 lines
(the moved `PlaybackManager` body plus the coordinator's seams,
`production()` factory, and KDoc) and `MpvCoreEngine` is 1,694 lines
with the `MpvSubtitleRenderer` at 832 lines, the `MpvPlayerEngine`
facade at 512 lines, `MpvEngineConfig` at 206 lines and
`MpvTrackSelection` at 255 lines (the old
2,674-line monolith, split by Phase 4 steps 1–4); `PlayerScreen` (2,666),
`MusicScreen` (2,414), `LocalScreen` (1,895), and `SettingsScreen`
(1,766) are similarly large. The old `PlaybackManager` (987 lines)
is deleted — its body lives in `PlaybackCoordinator` and its pieces in
`SubtitleManager` (424 lines), `PlaybackQueue` (593 lines), and
`EngineEventTranslator` (363 lines). A small fix in any of these means
reading a lot of unrelated code first. Phase 4 split the engine instead
(`MpvPlayerEngine`, 2,674 lines, is now a 512-line facade over four
focused classes); these playback pieces stay as they are.

## Where do I change X?

| I want to… | Start from | Notes |
|---|---|---|
| Add a setting | `domain/settings/AppSettings.kt` (the model), `data/settings/SharedPrefsAppSettingsRepository.kt` (load/save), `ui/screens/settings/SettingsViewModel.kt` (the write) and `ui/screens/settings/SettingsScreen.kt` (the row) | New user-facing strings need entries in all 9 locales — see CONTRIBUTING.md |
| Add a library sort option | `ui/screens/local/LocalViewModel.kt` (Local) or `ui/screens/music/MusicViewModel.kt` (Music) | Sorting lives in the ViewModel, not in the repository or the screen |
| Change a subtitle behaviour | `playback/SubtitleManager.kt` (discovery, selection, pending pick) and `playback/engine/MpvSubtitleRenderer.kt` (rendering, codepage) | External subtitles are picked in the player; encoding detection sets `sub-codepage` before the sub-add. The track list itself is published by `PlaybackCoordinator.updateTracksState()` (audio+video+subtitle together). |
| Change queue behaviour (shuffle, repeat, next/previous, restore) | `playback/PlaybackQueue.kt` (queue order, the end-of-item decision, persistence via the `QueueStore` interface) | The engine listeners that execute the queue's end-of-item decision live in `playback/EngineEventTranslator.kt` (Phase 3 step 3) |
| Change what an engine event does (play/pause, READY/ENDED, track change, error, decoder fallback) | `playback/EngineEventTranslator.kt` (the `PlayerEngine.Listener` implementation) and `playback/PlaybackCoordinator.kt` (its `EngineEventHost`: published state, ticker, watchdog) | The translator decides what an event means; the coordinator applies the state/lifecycle effects. The preparing watchdog stays in the coordinator (it is also armed from session start), and the codec-error banner text is formatted through the coordinator's `CoordinatorStrings` seam (it needs string resources) |
| Add a network source | `network/` (`StreamUrl.kt`, `SharedLink.kt`) and `ui/screens/network/NetworkViewModel.kt` | Playback of a stream still goes through `PlaybackCoordinator` and the engine |
| Change what resume remembers | `playback/PlaybackResumeManager.kt` and `data/local/entity/PlaybackResumeEntity.kt` | Positions are keyed by media id/URI; see `resumeKey` in the manager |
| Change how the player looks | `ui/screens/player/PlayerScreen.kt`, `ui/components/player/`, `ui/theme/` | Keep the UX law: true black in dark mode, one accent (identity blue), back = exactly one step |
| Change what mpv is told | `playback/engine/MpvCoreEngine.kt` (lifecycle, load/play, observation, decoder fallback), `playback/engine/MpvTrackSelection.kt` (track listing/switching), `playback/engine/MpvSubtitleRenderer.kt` (subtitle rendering/confirmation, `replaceMediaItem`), `playback/engine/MpvPlayerEngine.kt` (settings-to-mpv) and `playback/engine/MpvConfigSync.kt` | Settings reach mpv as options; do not call mpv from a screen |
| Add a screen | `ui/navigation/Navigation.kt` (route), a new file under `ui/screens/<feature>/` with its ViewModel beside it, wire it in `MainActivity` | Every new screen gets a ViewModel beside it (Phase 2 pattern) |

## Package map (current)

- `ui/` — Compose screens, theme, navigation, components.
- `ui/screens/<feature>/` — each screen together with its ViewModel,
  e.g. `ui/screens/player/PlayerScreen.kt` beside
  `ui/screens/player/PlayerViewModel.kt`.
- `domain/` — plain models and repository interfaces (as today).
- `data/` — Room, MediaStore, and settings implementations (as today).
- `playback/` — the `PlaybackCoordinator` (1,086 lines, 34 unit
  tests) plus the queue, resume, and subtitle pieces, plus
  `PlaybackService`. Phase 3 split the old `PlaybackManager` god
  object into the coordinator and the pieces: the subtitle piece is
  done (`playback/SubtitleManager.kt`, 424 lines, 18 unit tests),
  the queue piece is done (`playback/PlaybackQueue.kt`, 593 lines,
  29 unit tests), the engine-event translation is done
  (`playback/EngineEventTranslator.kt`, 363 lines, 28 unit tests),
  and the coordinator owns the published state, the position ticker,
  the preparing watchdog, the sleep timer, transport and session
  functions, and the three host implementations.
- `playback/engine/` — the `PlayerEngine` interface and its mpv
  implementation. Phase 4 splits the engine by concern: steps 1–4 are
  done (`playback/engine/MpvCoreEngine.kt`, 1,694 lines, 6 unit tests
  for its testable path helper, `MpvTrackSelection.kt`, 255 lines,
  no tests — every function touches mpv directly,
  `MpvSubtitleRenderer.kt`, 832 lines, 9 unit tests for its two pure
  companion helpers, and `MpvEngineConfig.kt`, 206 lines, 11 unit
  tests for its three pure companion helpers), with `MpvPlayerEngine`
  (512 lines) as the facade; Phase 4 is complete and the
  `PlayerEngine` interface is unchanged.
- `di/` — the Koin modules (`AppModule`, `PlaybackModule`,
  `ViewModelModule`). The one place in the app where objects are
  constructed.
- `network/`, `update/`, `util/` — largely as today.

A finished screen follows one pattern, every time:

1. The screen obtains its ViewModel with `koinViewModel()`.
2. The ViewModel exposes one immutable state as a `StateFlow` and a small
   set of intent functions (`onPlay(uri)`, `onToggleFavourite()`, …).
3. The screen collects that state and renders it. It holds no repository,
   database, or engine reference. (Screens still hold
   `PlaybackCoordinator` directly for transport — that is the one remaining
   exception to this rule, and it is deliberate, not a second pattern.)
4. The ViewModel's dependencies arrive through its constructor, so a test
   can pass fakes.

## Glossary

- **Engine** — the thing that actually decodes and renders media: mpv,
  reached only through the `PlayerEngine` interface.
- **Coordinator** — the small public face of playback that
  ViewModels talk to. `PlaybackCoordinator.kt` owns the state,
  ticker, watchdog, sleep timer, and transport; it is the only
  playback owner.
- **Queue store** — `PlaybackQueueStore`: persists the current play queue
  so it survives the app being killed.
- **Resume position** — the saved playback position for a media item,
  stored in Room and restored on the next play.
- **Content URI vs raw file path** — Android gives the app a `content://`
  URI for a media file; mpv sometimes needs the raw filesystem path
  instead (subtitles are the main case). `MpvFdOpener` bridges the two.
  Subtitle files found next to a video keep their raw path so mpv can
  open them directly.

## Migration status

- Phase 0 (this document + CONTRIBUTING.md build notes): done.
- Phase 1 (Koin foundation): done. `di/AppModule` (database, MediaStore
  + Room repositories, settings repository), `di/PlaybackModule`
  (`PlaybackCoordinator` as a `single`, built by
  `PlaybackCoordinator.production()`), and `MainActivity` on Koin.
  The mpv engine has no Koin registration —
  `PlaybackCoordinator.production()` builds it (with the database,
  repositories, and resume manager).
- Phase 2 (one ViewModel per screen, player first): done. Every screen
  has a ViewModel beside it, obtained with `koinViewModel()` from
  `di/ViewModelModule`, depending on interfaces only, each with unit
  tests using fakes (70 tests across the six test files, no mocking
  framework):
  - 2a `PlayerViewModel` (`ui/screens/player/`, 198 lines, 7 tests):
    playback-state collection, settings load / reload-on-resume, queue
    restore on first entry, rotation override, keep-screen-on. Depends
    on `AppSettingsRepository` and the minimal
    `playback/PlaybackStateSource` (`playbackState` +
    `restorePersistedQueueIfEmpty()`), which `PlaybackCoordinator`
    implements.
  - 2b `LocalViewModel` (`ui/screens/local/`, ~1,050 lines, 19 tests):
    settings load / reload, folder browsing, sort / view / filter,
    selection, favourites, playlists, search. Search state
    (whole-page query, in-folder query, in-folder open flag) is backed
    by the `SavedStateHandle`, not plain state, so it survives dock-tab
    switches and process death.
  - 2c `MusicViewModel` (`ui/screens/music/`, ~880 lines, 8 tests):
    all five tabs' filter / sort, blacklist, selection, album / artist
    / folder resolvers, playlist dual shapes, favourite group-toggle,
    view mode, history paging. `selectedTab`, search state, and
    `historyLimit` stay `rememberSaveable` in the screen (dock-tab
    switches pop the destination and clear the ViewModel) and are
    mirrored into the ViewModel.
  - 2d `NetworkViewModel` (`ui/screens/network/`, 6 tests): URL state
    as one `NetworkUiState`; streams play through the minimal
    `StreamPlayer` interface (`playStream`), implemented by
    `PlaybackCoordinator`.
  - 2e `MeViewModel` (`ui/screens/me/`, 243 lines, 12 tests): Me /
    History / Playlists state — settings, badge flows, favourites
    actions, history total + paging, playlists.
  - 2e `SettingsViewModel` (`ui/screens/settings/`, 450 lines,
    18 tests): every settings write (including menu, blacklist, and
    diagnostics reads) goes through the ViewModel; `MainActivity`
    passes it the `PlaybackCoordinator` it needs for the few
    playback-coupled reads.
  - Consolidation: no `getInstance` call site remains in UI code —
    `PlayerScreen`, `MeScreen`, `HistoryScreen`, and `PlaylistsScreen`
    take `PlaybackCoordinator` as a required parameter from `MainActivity`.
    What is still NOT migrated: screens still hold
    `PlaybackCoordinator` for transport, and `PlaybackCoordinator` /
    `PlaybackService` / `MpvPlayerEngine` still build objects by hand
    (see "The two things that surprise newcomers" above). Both are
    deliberate and stay — the engine has no Koin registration by design.
- Phase 3 (break up `PlaybackManager`): done. Step 1:
  `SubtitleManager` (`playback/`, 424 lines, 18 tests) owns external
  subtitle discovery, the manual-pick load path, the pending
  confirmation, and subtitle style settings. Step 2: `PlaybackQueue`
  (`playback/`, 593 lines, 29 tests) owns the live queue, queue
  building, shuffle and repeat, queue persistence (behind the new
  `QueueStore` interface), and the pure end-of-item decision
  (`endedAction`); the coordinator stays its `QueueHost`. Step 3:
  `EngineEventTranslator` (`playback/`, 363 lines, 28 tests) owns the
  `PlayerEngine.Listener` implementation — play/pause effects, the
  READY/ENDED publishes and the ENDED decision execution, transitions,
  track/subtitle delegation, speed/size/decoder/error publishes; the
  coordinator stays its `EngineEventHost` (published state, ticker,
  watchdog). Step 4: `PlaybackCoordinator` (`playback/`, 1,086 lines,
  34 tests) owns the published state, the position ticker, the
  preparing watchdog, the sleep timer, transport and session functions,
  and the three host implementations (`SubtitleHost`, `QueueHost`,
  `EngineEventHost`); it implements `PlaybackStateSource` and
  `StreamPlayer`. Step 5: the callers are rewired to the coordinator
  (`MainActivity` and the screens take `PlaybackCoordinator` from Koin,
  `PlaybackService` injects the same `single`) and `PlaybackManager`
  is deleted — the engine-error log tag stays "PlaybackManager" so
  existing logcat filters keep working.
  `PlaybackResumeManager` already exists and is wired behind the
  coordinator. Unit-test total is now 179 (70 from
  Phase 2 + 18 for `SubtitleManager` + 29 for `PlaybackQueue` + 28
  for `EngineEventTranslator` + 34 for `PlaybackCoordinator`).
- Phase 4 (split `MpvPlayerEngine` by concern): steps 1–4 done.
  `MpvCoreEngine` (`playback/engine/`, 1,694 lines, 6 tests) owns the
  mpv lifecycle (MPV creation/init, release), the load/play commands
  (`load`, `play`, `pause`, `seekTo`, `setSpeed`, `stop`,
  next/previous), position/state observation (cached
  position/duration/buffered/playing/state, property getters, mpv
  event + property observation) and the decoder fallback chain
  (`attemptDecoderFallback`, `reloadForDecoderFallback`).
  `MpvTrackSelection` (`playback/engine/`, 255 lines, no tests) owns
  track listing/switching (`selectTrack`, `selectAudioTrack`,
  `selectSubtitleTrack`, `getAudioTracks`, `getSubtitleTracks`,
  `getVideoTrackIds`), reaching mpv through the core's `mpv` — no
  second MPV instance. Its one coupling back to the subtitle concern
  is the `MpvTrackSelection.Host` seam: a manual Off or embedded subtitle
  pick clears the remembered external pick + armed pin (owned by
  `MpvSubtitleRenderer`, step 3) at exactly the points the old monolith
  did; the seam stays because the track class is built before the
  renderer, so it reaches it through the facade.
  `MpvSubtitleRenderer` (`playback/engine/`, 832 lines, 9 tests) owns
  subtitle rendering/confirmation: the subtitle style options
  (`applySubtitleSettings`, via `subtitleMpvOptions`), the subtitle
  codepage (`applySubtitleCodepage`), the pending-subtitle queue
  dispatch on FILE_LOADED (including the #189 embedded auto-select),
  the remembered external pick + pin (`reapplyRememberedPick`), the
  #332 sub-add outcome confirmation, and `replaceMediaItem` (sub-add +
  #332 confirmation). It reaches mpv through the core and the
  track-list through `MpvTrackSelection` — no second MPV instance, no
  facade reference. The `PendingSubtitle`/`PendingSubtitles` queue data
  moved here with the dispatch; the core still builds the queue in
  `load()` (same package) and stores it in `pendingSubtitles`. The
  facade reaches the renderer through the `MpvCoreEngine.Host` seam
  (called at exactly the points the old monolith ran that code); the
  init-time subtitle application uses the renderer's companion
  `applySubtitleSettingsTo(core, settings)` because the core's init runs
  before the renderer exists, and the other host callbacks guard with
  `::subtitles.isInitialized`. `stop()`/`release()` run the renderer's
  subtitle cleanup first, then the core stop/release — same order as
  the old monolith.
  `MpvPlayerEngine` (512 lines) stays the `PlayerEngine`
  implementation and is a thin facade delegating to the core, the
  track class, the renderer and the config class; the `PlayerEngine`
  interface is unchanged. `MpvEngineConfig` (`playback/engine/`, 207
  lines, 11 tests) owns settings-to-mpv configuration: the playback
  tweaks (`applyPlaybackTweaks` — audio delay + video zoom), the
  decoder mode (`applyHwdecForMode`, via the pure `hwdecValueFor` /
  `hwdecValueForStage` mappings) and the log2 video-zoom conversion
  (`mpvVideoZoom`, kept as a companion so the facade's presentation
  functions `setVideoZoom`/`setAspectRatio` can call it qualified
  for the same conversion). It reaches mpv through the core — no
  second MPV instance. Its one coupling back to the facade is the
  #226 ZOOM interplay: `applyPlaybackTweaks` must not overwrite
  ZOOM's 2x magnification on an unrelated settings change, and
  leaving ZOOM must restore the pre-ZOOM zoom — but the aspect/zoom
  presentation state stays in the facade (`setAspectRatio` /
  `setVideoZoom` own it), so the config class reaches it only
  through the narrow `MpvEngineConfig.ZoomState` interface the
  facade implements as an anonymous object delegating to its private
  vars (one boolean read, two restore-point read/writes). The
  init-time config work uses the config companion functions
  (`applyPlaybackTweaksTo(core, settings, zoomState)`,
  `hwdecValueFor`) because the core's init runs before the config
  instance exists; `applyHwdecForMode` is an instance call, safe
  because the core invokes it only from `load()`. The core and
  the track class are verifiable by CI build + device only
  (constructing the core creates a real MPV instance; every track
  function touches mpv directly); the one unit-testable piece in the
  core is the pure `isPathMatch` path helper, which lives in the
  core's companion object for that reason, and the renderer's two pure
  companion helpers (`isUsableSubtitleUri`, `isSameExternalSubtitle`)
  are tested the same way. Unit-test total is now 205 — step 4 adds 11
  tests for the config class's three pure companion helpers; the rest
  of the moved code touches mpv
  directly and is not testable without it.
- Phase 5 (contributor path): done. The PR template asks what
  changed, what did not change, and how it was verified (with the
  build result stated); CI runs the debug build and unit tests on
  every pull request. The worked end-to-end example is in
  CONTRIBUTING.md (the audio-delay setting, traced through screen,
  ViewModel, repository, Koin registration, and the coordinator down
  to `MpvEngineConfig`). Two honest `good first issue`s are open,
  each naming the file to start from: #465 (pin the CI `build` job
  to `ubuntu-24.04`, one line in `.github/workflows/ci.yml`) and
  #466 (inject `AppSettingsRepository` into `MediaStoreRepository`
  instead of building one inline). The `PipUtils` inline-construction
  shape was checked and is NOT labelled: its call sites make it a
  bigger refactor than a first issue.

Until a phase is marked done here, assume the old pattern still applies
to the files it covers.
