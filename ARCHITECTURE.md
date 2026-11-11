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
  (`app/src/main/java/com/mpx/player/`, 116 Kotlin files)
- UI: Jetpack Compose, Material 3 Expressive, one activity (`MainActivity`),
  Compose Navigation
- Playback: mpv only, via `mpv-android-lib` 0.1.12. There is no Media3 /
  ExoPlayer in the app any more.
- Persistence: Room 2.8.5 for library data, SharedPreferences for settings
- Build: AGP 9.2.1, Kotlin 2.5.0-Beta2, KSP 2.3.13, Compose BOM 2026.06.01,
  compileSdk 36, minSdk 24, targetSdk 34. Dependencies are hardcoded in
  `app/build.gradle.kts`; there is no version catalog.

## Layers (current)

```
UI (Compose screens, theme, navigation, components)
        │  calls
Playback (PlaybackManager, PlaybackService, queue, resume, subtitles)
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
- **Playback** — `playback/`: `PlaybackManager` is the centre of the app.
  It owns the queue, resume positions, subtitle handling, and the engine
  listener, and exposes playback state as `StateFlow<PlaybackState>`.
  `PlaybackService` is the foreground service for background playback.
  `PlaybackQueueStore` persists the queue; `PlaybackResumeManager` saves
  and restores positions.
- **Engine** — `playback/engine/`: `PlayerEngine` is the interface the
  player UI is supposed to program against (`load`, `play`, `pause`,
  `seekTo`, `setSpeed`, `selectTrack`, `replaceMediaItem`, `stop`,
  `release`, `setDecoderMode`, plus a `Listener` for state/track/error
  callbacks). `MpvPlayerEngine` is the only implementation.
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
  activity-result launchers; it receives its `PlaybackManager` and
  settings repository from Koin.

## How a video gets from a tap to mpv (current)

1. The Local tab (`ui/screens/local/LocalScreen.kt`) lists folders and
   files through `LocalViewModel`, which reads them from
   `MediaStoreRepository`.
2. Tapping a video calls into `PlaybackManager` (`playVideoFiles`,
   `playQueue`, `playLiveQueueItem`, …), which builds a queue of
   `PlayableMedia` items.
3. `PlaybackManager` hands the queue to the engine through the
   `PlayerEngine` interface (`load(items, startIndex, resumeMs)`), and
   `MpvPlayerEngine` drives mpv.
4. mpv events come back through `PlayerEngine.Listener` into
   `PlaybackManager`, which updates its `StateFlow<PlaybackState>`.
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
`PlaybackManager` directly for transport (play/pause, queue, subtitles)
until Phase 3's final step replaces it with the small coordinator —
the coordinator itself already exists (`playback/
PlaybackCoordinator.kt`): `PlaybackManager` is now a 175-line shell
that delegates everything to it, and the coordinator builds the
database, persistence, settings, resume manager, engine, and the
three extracted pieces through its `production()` factory. No
`getInstance` call site remains in UI code — the only ones left are
`PlaybackService` (final Phase 3 step) and `PlaybackManager` itself,
which calls `PlaybackCoordinator.production()` internally (final
step); the `getInstance` calls in `di/AppModule` and
`di/PlaybackModule` are the legitimate construction points. The other
hand-built construction left is the settings repository, built
directly in `PlaybackCoordinator.production()`, `PlaybackService`,
and `MpvPlayerEngine` (Phases 3–4), plus two data/util internals —
`MediaStoreRepository` (`showHiddenFiles` read) and `PipUtils`
(default argument) — which have no phase assigned yet.

**Two files are very large.** `PlaybackCoordinator` is 1,081 lines
(the moved `PlaybackManager` body plus the coordinator's seams,
`production()` factory, and KDoc) and `MpvPlayerEngine` is 2,674
lines; `PlayerScreen` (2,666), `MusicScreen` (2,414), `LocalScreen`
(1,895), and `SettingsScreen` (1,766) are similarly large. The old
`PlaybackManager` (987 lines) is now a 175-line delegating shell —
its pieces live in `SubtitleManager` (424 lines), `PlaybackQueue`
(585 lines), and `EngineEventTranslator` (363 lines). A small fix in
any of these means reading a lot of unrelated code first. Splitting
them is the migration's Phase 3 and Phase 4; until then, expect it.

## Where do I change X?

| I want to… | Start from | Notes |
|---|---|---|
| Add a setting | `domain/settings/AppSettings.kt` (the model), `data/settings/SharedPrefsAppSettingsRepository.kt` (load/save), `ui/screens/settings/SettingsViewModel.kt` (the write) and `ui/screens/settings/SettingsScreen.kt` (the row) | New user-facing strings need entries in all 9 locales — see CONTRIBUTING.md |
| Add a library sort option | `ui/screens/local/LocalViewModel.kt` (Local) or `ui/screens/music/MusicViewModel.kt` (Music) | Sorting lives in the ViewModel, not in the repository or the screen |
| Change a subtitle behaviour | `playback/SubtitleManager.kt` (discovery, selection, pending pick) and `playback/engine/MpvPlayerEngine.kt` (rendering, codepage) | External subtitles are picked in the player; encoding detection sets `sub-codepage` before the sub-add. `PlaybackManager` is a shell over `PlaybackCoordinator` and keeps thin delegating functions (`loadExternalSubtitle`, `selectSubtitleTrack`, `applySubtitleSettings`) so screens do not change; the track list itself is still published by `PlaybackCoordinator.updateTracksState()` (audio+video+subtitle together). An engine subtitle file is Phase 4 |
| Change queue behaviour (shuffle, repeat, next/previous, restore) | `playback/PlaybackQueue.kt` (queue order, the end-of-item decision, persistence via the `QueueStore` interface) | `PlaybackManager` is a shell over `PlaybackCoordinator` and keeps thin delegating functions (`playQueue`, `skipNext`, `setShuffleEnabled`, …) so screens do not change; the engine listeners that execute the queue's end-of-item decision now live in `playback/EngineEventTranslator.kt` (Phase 3 step 3) |
| Change what an engine event does (play/pause, READY/ENDED, track change, error, decoder fallback) | `playback/EngineEventTranslator.kt` (the `PlayerEngine.Listener` implementation) and `playback/PlaybackCoordinator.kt` (its `EngineEventHost`: published state, ticker, watchdog) | The translator decides what an event means; the coordinator applies the state/lifecycle effects. The preparing watchdog stays in the coordinator (it is also armed from session start), and the codec-error banner text is formatted through the coordinator's `CoordinatorStrings` seam (it needs string resources) |
| Add a network source | `network/` (`StreamUrl.kt`, `SharedLink.kt`) and `ui/screens/network/NetworkViewModel.kt` | Playback of a stream still goes through `PlaybackManager` (a shell over `PlaybackCoordinator`) and the engine |
| Change what resume remembers | `playback/PlaybackResumeManager.kt` and `data/local/entity/PlaybackResumeEntity.kt` | Positions are keyed by media id/URI; see `resumeKey` in the manager |
| Change how the player looks | `ui/screens/player/PlayerScreen.kt`, `ui/components/player/`, `ui/theme/` | Keep the UX law: true black in dark mode, one accent (identity blue), back = exactly one step |
| Change what mpv is told | `playback/engine/MpvPlayerEngine.kt` and `playback/engine/MpvConfigSync.kt` | Settings reach mpv as options; do not call mpv from a screen |
| Add a screen | `ui/navigation/Navigation.kt` (route), a new file under `ui/screens/<feature>/` with its ViewModel beside it, wire it in `MainActivity` | Every new screen gets a ViewModel beside it (Phase 2 pattern) |

## Package map (current)

- `ui/` — Compose screens, theme, navigation, components.
- `ui/screens/<feature>/` — each screen together with its ViewModel,
  e.g. `ui/screens/player/PlayerScreen.kt` beside
  `ui/screens/player/PlayerViewModel.kt`.
- `domain/` — plain models and repository interfaces (as today).
- `data/` — Room, MediaStore, and settings implementations (as today).
- `playback/` — today still the `PlaybackManager` god object, queue,
  resume, and subtitle pieces, plus `PlaybackService`. Phase 3 splits
  the manager into a coordinator, queue, resume, and subtitle pieces;
  the subtitle piece is done (`playback/SubtitleManager.kt`, 18 unit
  tests), the queue piece is done (`playback/PlaybackQueue.kt`,
  585 lines, 29 unit tests), the engine-event translation is
  done (`playback/EngineEventTranslator.kt`, 363 lines, 28 unit
  tests), and the coordinator shell is done
  (`playback/PlaybackCoordinator.kt`, 1,081 lines, 34 unit tests;
  `PlaybackManager` is now a 175-line shell delegating to it). Only
  the final step remains: rewiring the callers to the coordinator
  and deleting `PlaybackManager`.
- `playback/engine/` — the `PlayerEngine` interface and its mpv
  implementation. Phase 4 splits the engine by concern.
- `di/` — the Koin modules (`AppModule`, `PlaybackModule`,
  `ViewModelModule`). The one place in the app where objects are
  constructed.
- `network/`, `update/`, `util/` — largely as today.

A finished screen follows one pattern, every time:

1. The screen obtains its ViewModel with `koinViewModel()`.
2. The ViewModel exposes one immutable state as a `StateFlow` and a small
   set of intent functions (`onPlay(uri)`, `onToggleFavourite()`, …).
3. The screen collects that state and renders it. It holds no repository,
   database, or engine reference. (Until Phase 3, screens still hold
   `PlaybackManager` directly for transport — that is the one remaining
   exception to this rule, and it is deliberate, not a second pattern.)
4. The ViewModel's dependencies arrive through its constructor, so a test
   can pass fakes.

## Glossary

- **Engine** — the thing that actually decodes and renders media: mpv,
  reached only through the `PlayerEngine` interface.
- **Coordinator** — the small public face of playback that
  ViewModels talk to. `PlaybackCoordinator.kt` already owns the
  state, ticker, watchdog, and transport; `PlaybackManager` is a
  thin shell over it until the final Phase 3 step rewires the
  callers and deletes it.
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
  (`PlaybackManager` as a `single`), and `MainActivity` on Koin.
  The mpv engine has no Koin registration —
  `PlaybackCoordinator.production()` builds it (with the database,
  repositories, and resume manager); Koin still provides
  `PlaybackManager`, which delegates to the coordinator. Registering
  the coordinator itself is the final Phase 3 step.
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
    `restorePersistedQueueIfEmpty()`), which `PlaybackManager`
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
    `PlaybackManager`.
  - 2e `MeViewModel` (`ui/screens/me/`, 243 lines, 12 tests): Me /
    History / Playlists state — settings, badge flows, favourites
    actions, history total + paging, playlists.
  - 2e `SettingsViewModel` (`ui/screens/settings/`, 450 lines,
    18 tests): every settings write (including menu, blacklist, and
    diagnostics reads) goes through the ViewModel; `MainActivity`
    passes it the `PlaybackManager` it needs for the few
    playback-coupled reads.
  - Consolidation: no `getInstance` call site remains in UI code —
    `PlayerScreen`, `MeScreen`, `HistoryScreen`, and `PlaylistsScreen`
    take `PlaybackManager` as a required parameter from `MainActivity`.
    What is still NOT migrated (Phases 3–4): screens still hold
    `PlaybackManager` for transport, and `PlaybackManager` /
    `PlaybackService` / `MpvPlayerEngine` still build objects by hand
    (see "The two things that surprise newcomers" above).
- Phase 3 (break up `PlaybackManager`): in progress. Step 1 done:
  `SubtitleManager` (`playback/`, 424 lines, 18 tests) owns external
  subtitle discovery, the manual-pick load path, the pending
  confirmation, and subtitle style settings; `PlaybackManager` keeps
  thin delegating functions so no screen, ViewModel, or service
  changed, and still owns the queue, the published state, and the
  shared track-list refresh. Step 2 done: `PlaybackQueue`
  (`playback/`, 585 lines, 29 tests) owns the live queue, queue
  building, shuffle and repeat, queue persistence (behind the new
  `QueueStore` interface), and the pure end-of-item decision
  (`endedAction`); `PlaybackManager` stays its `QueueHost`, keeps
  thin delegating functions, and still owns the published state and
  the engine listeners. Step 3 done: `EngineEventTranslator`
  (`playback/`, 363 lines, 28 tests) owns the `PlayerEngine.Listener`
  implementation — play/pause effects, the READY/ENDED publishes
  and the ENDED decision execution, transitions, track/subtitle
  delegation, speed/size/decoder/error publishes; `PlaybackManager`
  stays its `EngineEventHost` (published state, ticker, watchdog)
  and still owns the published state. Step 4 done: `PlaybackCoordinator`
  (`playback/`, 1,081 lines, 34 tests) owns the published state, the
  position ticker, the preparing watchdog, the sleep timer, transport
  and session functions, and the three host implementations
  (`SubtitleHost`, `QueueHost`, `EngineEventHost`); `PlaybackManager`
  is a 175-line shell keeping its exact public API and delegating
  everything to the coordinator built by
  `PlaybackCoordinator.production()`, and implements
  `PlaybackStateSource` and `StreamPlayer` the same way the
  coordinator does. Still to do: rewiring the callers to the
  coordinator, then deleting `PlaybackManager` itself.
  `PlaybackResumeManager` already exists and is wired behind the
  coordinator. Unit-test total is now 179 (70 from
  Phase 2 + 18 for `SubtitleManager` + 29 for `PlaybackQueue` + 28
  for `EngineEventTranslator` + 34 for `PlaybackCoordinator`).
- Phase 4 (split `MpvPlayerEngine` by concern): not started.
- Phase 5 (contributor path): in progress. The PR template asks what
  changed, what did not change, and how it was verified; CI runs the
  debug build and unit tests on every pull request. Still to do: the
  worked end-to-end example in CONTRIBUTING.md (needs a real migrated
  feature, so it waits for Phases 1–2) and honest `good first issue`
  labels on genuinely small post-migration tasks.

Until a phase is marked done here, assume the old pattern still applies
to the files it covers.
