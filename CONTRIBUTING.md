# Contributing to MPx Player

Thanks for helping. MPx is a small project with one maintainer, so small, focused contributions land fastest.

## First: how this project works

- **Stack:** native Kotlin, Jetpack Compose, Material 3, Room, mpv (via `mpv-android-lib`) for playback. There is no downloader — it was removed in 2026-11 (see #434); the fourth dock tab is Network (streams + SMB), not Download.
- **Architecture:** see [ARCHITECTURE.md](ARCHITECTURE.md) — layers, where playback state lives, and a "where do I change X?" table. Read it before your first code change.
- **Branch:** `master` is the current branch. Open PRs against `master`.
- **Releases** go out when a goal is reached, verified by **green builds** — that is the gate. The maintainer sometimes launch-tests a release on his phone, but does not test every build, so on-device proof also comes from users in Discussions.
- **Language:** code and issues in English so contributors can follow them. Users can give feedback in Arabic or English in Discussions / Telegram.

## Ways to help (no code needed)

- Try the latest release and report what breaks — device, Android version, MPx version, and steps to reproduce.
- Answer a question in Discussions if you know the answer.
- Translate: strings live in `app/src/main/res/values/strings.xml`, with one `values-<lang>/strings.xml` per language. Keep every key present in every locale — a missing key breaks the build parity check.
- Take real phone screenshots for the README.

## Good first issues

Look for the [`good first issue`](https://github.com/mohammedbakri123/MPx-player/labels/good%20first%20issue) label. Good first PRs are: a small bug fix, a missing translation, a UI polish with a before/after screenshot.

If an issue has no label and looks small, comment "I'd like to take this" and wait for a maintainer reply before starting — it avoids two people doing the same work.

## Before you write code

1. Search open issues — your bug may already be filed.
2. For anything bigger than a typo fix, open an issue first and describe the change. Big PRs with no prior discussion are likely to be closed.
3. Keep the PR to **one thing**. A PR that fixes a bug *and* refactors a screen *and* adds a feature will sit unreviewed.

## Build

Requires JDK 17 and the Android SDK (platform 36, build-tools). Point Gradle at
the SDK with `sdk.dir` in `local.properties` or the `ANDROID_HOME` variable,
and at JDK 17 with `JAVA_HOME`.

```sh
git clone https://github.com/mohammedbakri123/MPx-player.git
cd MPx-player
./gradlew --no-daemon assembleDebug
```

For a release-shaped build (R8 + resource shrinking, debug-signed):

```sh
./gradlew --no-daemon assembleMinified
```

Build quirks that have bitten this project before — please respect them:

- **mpv AAR / mavenLocal:** Gradle running as root resolves `mavenLocal()` to `/root/.m2/repository`, not `~/.m2/repository`. Pass `-Dmaven.repo.local=$HOME/.m2/repository` when the build needs the mpv AAR. A fresh Gradle home often cannot fetch the mpv AAR at all — reuse a Gradle home that already has it cached.
- **Prefer `--no-daemon`.** If plugin downloads fail with connection resets, retry with `--offline` — the needed plugins are normally already cached.
- **Debug ≠ release.** Debug builds don't run R8. If your change touches anything loaded by reflection, serialisation, or runtime registration (mpv, codecs), also build the minified release variant and check R8's output keeps what it needs. v0.3.5 shipped a launch crash because R8 stripped `commons-compress` — don't repeat it.
- **Releases:** the tag is created *before* the minified build, so the APK stamps its own tag (versionName/versionCode come from `git describe --tags`). Never build a release APK from an untagged tree and call it that version.
- **Refactoring PRs carry no behaviour change.** The repo finished its migration to MVVM + Koin (see the migration status in ARCHITECTURE.md). A refactoring PR moves code only; a bug found along the way gets its own issue and its own PR.
- **Strings:** every new user-facing string needs an entry in **all 9 locales** (`values`, `values-ar`, `values-de`, `values-es`, `values-fr`, `values-hi`, `values-pt`, `values-ru`, `values-zh`). Same key, same count.
- **No placeholders shipped as features.** If a screen isn't built, say so in the code/UI — don't fake it.

## Worked example: changing the audio delay

One complete, small feature traced end to end — use it as the template when you add or change a setting. The example is the audio-delay stepper in the player sheet (issue #153); every link below is real code.

1. **Screen** — `ui/screens/player/PlayerPlaybackSheet.kt`: the delay stepper calls `onSettingsChanged(currentSettings.copy(audioDelayMs = ...))`. It never writes settings itself; it hands the updated blob back.
2. **Screen wiring** — `ui/screens/player/PlayerScreen.kt`: the sheet gets `onSettingsChanged = { updated -> viewModel.updateSettings(updated) }`, and `currentSettings` is `viewModel.settings.collectAsState()`. The screen owns no settings logic.
3. **ViewModel** — `ui/screens/player/PlayerViewModel.kt`, `updateSettings`: `settingsRepository.save(settings)` then `_settings.value = settings`. Persist first, publish second, so the UI and the disk never disagree.
4. **Repository** — `domain/settings/AppSettings.kt` declares `AppSettingsRepository` (`load()` / `save()`); `data/settings/SharedPrefsAppSettingsRepository.kt` implements it and writes `audio_delay_ms` to SharedPreferences.
5. **Koin** — `di/AppModule.kt`: `single<AppSettingsRepository> { SharedPrefsAppSettingsRepository(androidContext()) }`. `di/ViewModelModule.kt`: `viewModel { PlayerViewModel(get(), get()) }` — Koin resolves the `PlaybackStateSource` from the `PlaybackCoordinator` single and the repository from `AppModule`. The screen gets the ViewModel with `koinViewModel()`; it never constructs anything.
6. **Apply** — `PlayerScreen.kt` again: the player view's `update` block pushes changed settings to the coordinator (`playbackCoordinator.applySubtitleSettings(currentSettings)` + `applyPlaybackTweaks(currentSettings)`). `playback/PlaybackCoordinator.kt`, `applyPlaybackTweaks`, delegates to the engine, and `playback/engine/MpvEngineConfig.kt`, `applyPlaybackTweaksTo`, sets mpv's `audio-delay` (and `video-zoom`) as runtime properties. That is the one place settings become mpv options — do not call mpv from a screen.
7. **Test** — a settings change gets a unit test in `PlayerViewModelTest` (fake repository, assert save + publish). Engine behaviour is verified by the CI build, not by unit tests: the engine talks to a real mpv instance.

To add a new setting, follow the same six steps: a field on `AppSettings`, a row in the sheet or Settings screen that calls `onSettingsChanged`, nothing else. If the setting also needs an mpv option, add it in `MpvEngineConfig.applyPlaybackTweaksTo`, not in the screen.

## PR checklist

- [ ] One change, one PR, against `master`
- [ ] Debug build passes (`./gradlew --no-daemon assembleDebug`)
- [ ] If it touches reflection / R8 / startup: minified release build also passes, and you checked the kept classes
- [ ] New strings exist in all 9 locales
- [ ] Screenshots or a short screen recording for UI changes
- [ ] Issue number in the PR title or description (e.g. "Fix #359: …")

## Hard lines — please don't

- **Do not copy MX Player's source, binaries, assets, icons, strings, or branding.** Copying *layout ideas* in MPx's own code is fine and is how the project works; lifting MX proprietary material is not, in any build, even private or renamed.
- **Do not embed MX's proprietary engine** in MPx in any form.
- Do not add ads, tracking, or analytics without discussing it first — the project promises none.
- Do not add a new dependency without saying why in the PR. The APK is ~27 MB now; every MB needs a reason.

## Code style

- Follow the existing code around the change. Kotlin, Compose idioms, Material 3.
- Keep the UX law in mind: true black in dark mode, one accent (identity blue) used sparingly, clean uncrowded screens, back = exactly one step, dock tabs switch by tap only.

## Questions?

Ask in [Discussions](https://github.com/mohammedbakri123/MPx-player/discussions) — that's faster than opening an issue for a question, and other contributors can see the answer.
